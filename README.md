# Microservices User–Company Sync
**Spring Boot • Spring Cloud Config • Flyway • Docker • PostgreSQL**

## Project Overview

This project demonstrates synchronous interaction between independent microservices built with Spring Boot and Spring Cloud.

The system consists of two services:

- **User Service**
- **Company Service**

Each service:

- Owns its own database schema
- Exposes REST APIs
- Validates cross-service business rules via synchronous HTTP calls
- Uses Flyway for schema versioning
- Is externally configured via Spring Cloud Config
- Returns unified API error responses

The main goal of this project is to implement production-style microservice interaction with strict data isolation and cross-service validation.

## Architectural Principles

### 1. Database Isolation

Each service manages its own schema and tables.

Example: `companies` table created via Flyway migration:

```sql
create table if not exists companies (
  id bigserial primary key,
  name varchar(255) not null,
  ogrn varchar(20) not null unique,
  activity_description text,
  director_id bigint not null
);

create index if not exists idx_companies_director_id on companies(director_id);
```
This ensures:

- Service autonomy
- Independent schema evolution
- Clear bounded context separation

### 2. Synchronous Cross-Service Validation

`Company Service` validates director existence via REST call to `User Service`.

Core client implementation:

```java
@Component
public class UserClient {

    private final WebClient webClient;

    // Base URL for User Service is injected from application configuration
    public UserClient(@Value("${external.user-service.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Checks if a user with the given ID exists and is active.
     * Returns true if the user exists (HTTP 200), false if not found (HTTP 404).
     * Other HTTP errors will propagate as exceptions.
     */
    public boolean existsActiveUser(Long userId) {
        try {
            webClient.get()
                    .uri("/users/exists/{id}", userId)  // GET request to existence endpoint
                    .retrieve()
                    .toBodilessEntity()                 // We only care about status, not body
                    .block();                            // Synchronous blocking call
            return true;                                 // Success (HTTP 2xx) means user exists
        } catch (WebClientResponseException.NotFound ex) {
            return false;                                // HTTP 404 means user not found
        }
    }

    /**
     * Retrieves the full name of a user by ID.
     * Returns the name if found, null if user does not exist (HTTP 404).
     */
    public String getUserNameOrNull(Long userId) {
        try {
            return webClient.get()
                    .uri("/users/{id}/name", userId)    // GET request to name endpoint
                    .retrieve()
                    .bodyToMono(String.class)            // Extract response body as String
                    .block();                             // Synchronous blocking call
        } catch (WebClientResponseException.NotFound ex) {
            return null;                                 // HTTP 404 → return null instead of failing
        }
    }
}
```
### Design decisions:

- `WebClient` chosen for modern HTTP interaction
- Explicit `NotFound` handling
- Blocking calls used intentionally for synchronous validation
- No database-level foreign keys across services

## Business Flow Example

### Company Creation Logic

```java
@Transactional
public CompanyResponse create(CompanyCreateRequest req) {

    // Validate that the referenced director exists in User Service
    // This is a synchronous cross-service call
    if (!userClient.existsActiveUser(req.getDirectorId())) {
        throw new EntityNotFoundException(
            "Active director user with id=" + req.getDirectorId() + " not found"
        );
    }

    // Map request data to entity
    CompanyEntity entity = new CompanyEntity();
    entity.setName(req.getName());
    entity.setOgrn(req.getOgrn());
    entity.setActivityDescription(req.getActivityDescription());
    entity.setDirectorId(req.getDirectorId());

    // Persist company with validated director ID
    CompanyEntity saved = companyRepository.save(entity);

    // Enrich response with director name fetched from User Service
    return toResponseWithDirectorName(saved);
}
```

This demonstrates:

- Cross-service business rule enforcement
- Validation before persistence
- Separation of persistence and transport models
- Transactional consistency inside service boundary

## REST API Layer

```java
@RestController
@RequestMapping("/companies")
public class CompanyController {

    @GetMapping
    public List<CompanyResponse> getAll() {
        return companyService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CompanyCreateRequest req) {
        // Director existence is validated inside service via UserClient
        return companyService.create(req);
    }

    /**
     * Endpoint for cross-service validation.
     * Returns HTTP 204 if company exists, HTTP 404 if not found.
     * Used by other services to check company existence.
     */
    @GetMapping("/exists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exists(@PathVariable Long id) {
        companyService.assertExists(id);  // Throws EntityNotFoundException if not found
    }

    /**
     * Endpoint for retrieving company name.
     * Used by User Service to enrich user data with company information.
     */
    @GetMapping("/{id}/name")
    public String getName(@PathVariable Long id) {
        return companyService.getCompanyName(id);
    }
}
```

### Patterns used:

- DTO-based request/response
- Bean validation
- Explicit HTTP status codes
- Dedicated existence endpoint for inter-service checks

## Error Handling Strategy

Unified error response model:

```java
public record ErrorResponse(
    int status,              // HTTP status code (e.g., 404, 400, 500)
    String error,            // HTTP error type (e.g., "Not Found", "Bad Request")
    String message,          // Human-readable error description
    String path,             // Request path that caused the error
    OffsetDateTime timestamp // When the error occurred
) {}
```

Global exception handling:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex,
                                                       HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex,
                                                  HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "An unexpected error occurred",
            request.getRequestURI(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### Benefits:

- Consistent API error contract
- Clear separation of business exceptions
- Production-style error payload

## Configuration Management

The project uses:

- **Spring Cloud Config Server** – centralized configuration management
- **External configuration repository** – configuration stored in version control (Git)
- **RefreshScope** – enables dynamic configuration reload without application restart

Example:

```java
@RefreshScope  // Bean will be recreated when /actuator/refresh is called
@RestController
public class ServiceInfoController {

    @Value("${service.description:undefined}")  // Injected from Config Server
    private String description;

    @GetMapping("/description")
    public String description() {
        return description;
    }
}
```

This demonstrates:

- Centralized configuration
- Runtime config refresh
- Separation of configuration from service code

## Technology Stack

### Core Frameworks

- **Spring Boot 4.x** – application framework
- **Spring Data JPA** – persistence layer
- **Spring Web** – REST controllers
- **Spring WebFlux** – used for `WebClient` (reactive HTTP client)
- **Spring Cloud Config** – externalized configuration
- **Spring Boot Actuator** – monitoring and management endpoints

### Database

- **PostgreSQL** – relational database
- **Flyway** – database migrations

### Infrastructure

- **Docker** – containerization
- **Separate schemas per service** – logical database isolation
- **External configuration repository** – Git-backed config storage

### Validation

- **Jakarta Bean Validation** – DTO validation annotations

## Key Dependencies Explained

- **spring-boot-starter-web**  
  REST controller support, JSON serialization, HTTP handling.

- **spring-boot-starter-data-jpa**  
  ORM layer, repository abstraction, transaction management.

- **spring-boot-starter-webflux**  
  Used specifically for `WebClient` to perform HTTP calls between services.

- **spring-cloud-starter-config**  
  External configuration management and centralized config server integration.

- **flyway-core + flyway-database-postgresql**  
  Version-controlled schema migrations.

- **spring-boot-starter-validation**  
  DTO validation with annotations such as `@NotBlank`, `@NotNull`, `@Size`.

- **spring-boot-starter-actuator**  
  Operational endpoints and production monitoring readiness.

## Demonstrated Scenarios

The system supports:

- Creating a director user
- Creating a company with director validation
- Creating a user linked to a company
- Rejecting invalid director
- Rejecting invalid company
- Activating/deactivating user
- Existence validation endpoint

These flows simulate real distributed system validation patterns.

## Architectural Characteristics

This project demonstrates:

- **Bounded context separation** – each service owns its domain
- **Service-level transaction boundaries** – no distributed transactions
- **No cross-service foreign keys** – referential integrity enforced via application logic
- **Synchronous REST validation** – real-time consistency checks
- **Independent schema evolution** – each service migrates its own database
- **Structured error contracts** – unified error responses across all APIs
- **Centralized configuration management** – externalized config with refresh capability

## Purpose

This laboratory project demonstrates production-oriented microservice architecture concepts including:

- Cross-service business rule enforcement
- REST-based service-to-service communication
- Database isolation
- Configuration centralization
- Infrastructure containerization
- Clean layered architecture (Controller → Service → Repository)

It goes beyond CRUD and reflects enterprise backend design patterns.




