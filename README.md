# Distributed Microservices Platform with Centralized Configuration, Service Discovery, Event-Driven Deletion, and JWT Security

## Overview

This repository contains a distributed Spring-based platform composed of independently deployable microservices coordinated through centralized configuration, service discovery, an API gateway, synchronous HTTP integration, asynchronous Kafka messaging, and JWT-based security.

The system is centered around two business domains:

- `user-service` - manages users, credentials, roles, activation state, and company references
- `company-service` - manages companies, validates directors, supports soft deletion, and completes physical deletion through an asynchronous workflow

The supporting infrastructure includes:

- `config-server` - a Spring Cloud Config Server backed by a native configuration repository
- `discovery-server` - a Eureka registry for service registration and lookup
- `api-gateway` - a Spring Cloud Gateway instance that exposes the platform through a single entry point
- `postgres` - a shared PostgreSQL instance with isolated schemas for bounded contexts
- `kafka` - a single-node Apache Kafka broker used for event-driven company deletion

The implementation combines five architectural concerns into one coherent platform:

- centralized externalized configuration
- synchronous service-to-service interaction
- service discovery and gateway-based routing
- event-driven consistency through Kafka
- authentication and authorization through Spring Security and JWT

## System Architecture

```text
                                +----------------------+
                                |     Config Server    |
                                |  externalized YAML   |
                                +----------+-----------+
                                           |
                                           v
+-----------+      +----------------+   +------------------+   +------------------+
|  Client   +----->+   API Gateway  +--->+   user-service   +--->+    PostgreSQL    |
+-----------+      | JWT validation |   | auth, users,     |   |   user_schema     |
                   | route mapping  |   | sync + Kafka      |   +------------------+
                   +-------+--------+   +---------+---------+
                           |                        ^
                           |                        |
                           v                        |
                   +----------------+               |
                   | company-service +---------------+
                   | companies,      |   synchronous validation via Feign
                   | soft delete,    |
                   | Kafka producer  |
                   +--------+--------+
                            |
                            v
                       +---------+
                       | Kafka   |
                       | events  |
                       +---------+

All runtime services register in Eureka and communicate inside one Docker network.
```

## Architectural Objectives

The repository demonstrates the following technical properties:

- configuration is externalized and versionable through a dedicated config repository
- services do not hardcode their operational parameters
- service instances are discovered dynamically through Eureka
- external traffic is routed through a single gateway
- domain services use synchronous validation when immediate consistency is required
- destructive cross-service workflows are coordinated asynchronously through Kafka
- authentication is centralized in `user-service`
- authorization is enforced both at the gateway layer and inside downstream services
- inter-service security context is preserved through Feign interceptors

## Service Catalog

### `config-server`

Responsibilities:

- exposes configuration from `config-repo`
- provides shared and service-specific properties
- supports profile-based overrides such as `user-service-test.yml`

Key implementation:

```java
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

The server is configured in native mode and reads files from the mounted configuration repository.

```yaml
spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          # The config server reads configuration files directly from the mounted repository.
          search-locations: ${CONFIG_REPO_PATH:file:../config-repo}
```

### `discovery-server`

Responsibilities:

- acts as the Eureka registry
- maintains service location metadata
- enables logical service names such as `lb://user-service`

Key implementation:

```java
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

### `api-gateway`

Responsibilities:

- exposes a single external entry point
- maps public paths to internal services
- validates JWT tokens for non-whitelisted routes
- enriches downstream requests with trusted identity headers

Representative route configuration:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-service
              uri: lb://user-service
              predicates:
                # Authentication endpoints stay publicly reachable.
                - Path=/auth/**
              filters:
                # Preserve the /auth contract while forwarding to user-service.
                - RewritePath=/auth/?(?<segment>.*), /auth/$\{segment}
            - id: user-service
              uri: lb://user-service
              predicates:
                - Path=/user/**
              filters:
                # The gateway exposes /user/** externally and rewrites it to /users/** internally.
                - RewritePath=/user/?(?<segment>.*), /users/$\{segment}
            - id: company-service
              uri: lb://company-service
              predicates:
                - Path=/company/**
              filters:
                - RewritePath=/company/?(?<segment>.*), /companies/$\{segment}
```

### `user-service`

Responsibilities:

- stores user records and credentials
- exposes registration and login endpoints
- issues JWT tokens
- validates company existence synchronously before creating or updating users
- clears `company_id` when a company deletion event is received

### `company-service`

Responsibilities:

- stores company records
- validates director existence synchronously before company creation
- supports soft deletion and later physical deletion
- starts the asynchronous deletion workflow by emitting Kafka events

## Centralized Configuration Model

The configuration repository contains:

- `config-repo/application.yml` - shared cross-service configuration
- `config-repo/user-service.yml` - user-service properties
- `config-repo/company-service.yml` - company-service properties
- `config-repo/api-gateway.yml` - gateway routing configuration
- `config-repo/discovery-server.yml` - discovery configuration
- `config-repo/user-service-test.yml` - test-profile override for `user-service`

### Shared configuration

Shared configuration contains:

- Kafka bootstrap and serializer settings
- Kafka topic names
- JWT secret and token expiration
- security whitelist patterns

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

app:
  kafka:
    topics:
      company-deletion-requested: company-deletion-requested
      company-deletion-completed: company-deletion-completed

jwt:
  # Shared signing secret used by token producer and token validators.
  secret: UnikMicroservicesJwtSecretKeyForLab5NeedsAtLeastThirtyTwoBytes
  # Token lifetime in milliseconds.
  expiration: 3600000

security:
  whitelist:
    # Public endpoints bypass JWT enforcement.
    - /auth/**
    - /eureka/**
    - /actuator/**
```

### Service-specific configuration

`user-service` and `company-service` each define:

- service port
- service description
- datasource properties
- Flyway settings
- Eureka connection settings
- external base URL of the counterpart service through the gateway
- Kafka consumer group

Example:

```yaml
server:
  port: 8081

service:
  description: "user-service"

external:
  company-service:
    # Synchronous calls are intentionally routed through the gateway.
    base-url: ${COMPANY_SERVICE_BASE_URL:http://localhost:8080/company}

app:
  kafka:
    consumer-group: user-service-company-deletion-group
```

### Dynamic configuration refresh

Configuration-backed service description endpoints are marked with `@RefreshScope`, which allows runtime refresh after changing external configuration and calling `/actuator/refresh`.

```java
@RefreshScope
@RestController
public class ServiceInfoController {

    @Value("${service.description:undefined}")
    private String description;

    @GetMapping("/description")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String description() {
        // The returned value comes from Config Server, not from a hardcoded constant.
        return description;
    }
}
```

The repository also includes a dedicated test-profile override:

- `config-repo/user-service-test.yml`

This file overrides `service.description` when `user-service` is started with the `test` profile.

## Persistence Model

The platform uses one PostgreSQL instance with two schemas:

- `user_schema`
- `company_schema`

Schema initialization:

```sql
create schema if not exists user_schema;
create schema if not exists company_schema;
```

### User aggregate

The user model stores:

- business identity
- login and hashed password
- email
- activation state
- foreign reference to company
- comma-separated role set

```sql
create table if not exists users (
  id bigserial primary key,
  name varchar(255) not null,
  login varchar(100) not null unique,
  password varchar(255) not null,
  email varchar(255) not null unique,
  active boolean not null default true,
  company_id bigint
);
```

The security extension adds the roles column:

```sql
alter table users
    add column if not exists roles varchar(255) not null default 'USER';
```

### Company aggregate

The company model stores:

- company identity
- name
- OGRN
- business description
- director reference
- soft deletion flag

```sql
create table if not exists companies (
  id bigserial primary key,
  name varchar(255) not null,
  ogrn varchar(20) not null unique,
  activity_description text,
  director_id bigint not null
);
```

The asynchronous deletion workflow requires a soft delete marker:

```sql
alter table companies
    add column if not exists deleted boolean not null default false;

create index if not exists idx_companies_deleted on companies(deleted);
```

### Domain entities

Representative JPA mappings:

```java
@Entity
@Table(name = "users")
public class UserEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(nullable = false)
    private String password;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false)
    private String roles = UserRole.USER.name();
}
```

```java
@Entity
@Table(name = "companies")
public class CompanyEntity {
    @Column(name = "director_id", nullable = false)
    private Long directorId;

    @Column(nullable = false)
    private boolean deleted = false;
}
```

## Synchronous Service-to-Service Integration

The system uses OpenFeign for synchronous validation and data enrichment.

Two integration directions exist:

- `user-service -> company-service`
  - verify company existence before assigning `companyId`
  - resolve company name when returning users
- `company-service -> user-service`
  - verify active director existence before creating a company
  - resolve director name when returning companies

### Feign clients

`user-service` calls `company-service` through the gateway:

```java
@FeignClient(
        name = "company-service-client",
        url = "${external.company-service.base-url}",
        configuration = FeignSecurityConfiguration.class)
public interface CompanyClient {

    @GetMapping("/exists/{id}")
    ResponseEntity<Void> assertExists(@PathVariable("id") Long id);

    @GetMapping("/{id}/name")
    String getCompanyName(@PathVariable("id") Long id);
}
```

`company-service` calls `user-service` through the gateway:

```java
@FeignClient(
        name = "user-service-client",
        url = "${external.user-service.base-url}",
        configuration = FeignSecurityConfiguration.class)
public interface UserClient {

    @GetMapping("/exists/{id}")
    ResponseEntity<Void> assertExistsActive(@PathVariable("id") Long id);

    @GetMapping("/{id}/name")
    String getUserName(@PathVariable("id") Long id);
}
```

### Business validation

User creation validates the referenced company synchronously:

```java
@Transactional
public UserResponse create(UserCreateRequest req) {
    // Reject duplicate login and email before touching external dependencies.
    validateLoginAvailable(req.getLogin(), null);
    validateEmailAvailable(req.getEmail(), null);

    // Cross-service referential integrity is enforced through a synchronous Feign call.
    if (req.getCompanyId() != null && !companyClient.existsCompany(req.getCompanyId())) {
        throw new EntityNotFoundException("Company with id=" + req.getCompanyId() + " not found");
    }

    UserEntity e = new UserEntity();
    e.setPassword(passwordEncoder.encode(req.getPassword()));
    e.setRoleList(List.of(UserRole.USER.name()));
    ...
}
```

Company creation validates the director synchronously:

```java
@Transactional
public CompanyResponse create(CompanyCreateRequest req) {
    // A company cannot be created if the director is absent or inactive.
    if (!userClient.existsActiveUser(req.getDirectorId())) {
        throw new EntityNotFoundException("Active director user with id=" + req.getDirectorId() + " not found");
    }

    CompanyEntity e = new CompanyEntity();
    ...
}
```

## Event-Driven Consistency with Kafka

The repository implements a two-stage deletion pipeline for companies.

### Motivation

A company cannot be physically deleted immediately because users may still reference it through `company_id`.

The implemented strategy is:

1. mark the company as deleted
2. hide it from read APIs immediately
3. publish a deletion-request event
4. detach all affected users in `user-service`
5. publish a deletion-completed event
6. physically delete the company in `company-service`

### Repository support for soft delete and detach

```java
public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
    Optional<CompanyEntity> findByIdAndDeletedFalse(Long id);
    boolean existsByIdAndDeletedFalse(Long id);
    List<CompanyEntity> findAllByDeletedFalse();

    // Physical deletion is allowed only after the entity has already been soft-deleted.
    @Modifying
    @Query("delete from CompanyEntity c where c.id = :id and c.deleted = true")
    int deleteSoftDeletedById(@Param("id") Long id);
}
```

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    // Bulk cleanup of foreign references after a company deletion request.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.companyId = null where u.companyId = :companyId")
    int clearCompanyIdByCompanyId(@Param("companyId") Long companyId);
}
```

### Event emission from `company-service`

```java
@Transactional
public void delete(Long id) {
    CompanyEntity company = companyRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new EntityNotFoundException("Company with id=" + id + " not found"));

    // The company disappears from read APIs immediately after this flag is set.
    company.setDeleted(true);
    companyRepository.save(company);

    // The actual physical removal is deferred to the asynchronous workflow.
    eventPublisher.publishEvent(new CompanyDeletionRequestedEvent(company.getId()));
}
```

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCompanyDeletionRequested(CompanyDeletionRequestedEvent event) {
    // The message is published only after the database transaction commits.
    String payload = objectMapper.writeValueAsString(event);
    kafkaTemplate.send(topic, String.valueOf(event.companyId()), payload);
}
```

### Detach operation in `user-service`

```java
@KafkaListener(
        topics = "${app.kafka.topics.company-deletion-requested}",
        groupId = "${app.kafka.consumer-group}")
public void onMessage(String payload) {
    // user-service reacts to the deletion request and removes company references from users.
    CompanyDeletionRequestedEvent event = objectMapper.readValue(payload, CompanyDeletionRequestedEvent.class);
    userService.detachUsersFromDeletedCompany(event.companyId());
}
```

```java
@Transactional
public void detachUsersFromDeletedCompany(Long companyId) {
    int updatedUsers = userRepository.clearCompanyIdByCompanyId(companyId);
    log.info("Detached {} users from companyId={}", updatedUsers, companyId);

    // A completion event is emitted only after user references have been cleared.
    eventPublisher.publishEvent(new CompanyDeletionCompletedEvent(companyId));
}
```

### Final removal in `company-service`

```java
@KafkaListener(
        topics = "${app.kafka.topics.company-deletion-completed}",
        groupId = "${app.kafka.consumer-group}")
public void onMessage(String payload) {
    // The final delete happens only after user-service confirms cleanup.
    CompanyDeletionCompletedEvent event = objectMapper.readValue(payload, CompanyDeletionCompletedEvent.class);
    companyService.physicallyDelete(event.companyId());
}
```

This design separates:

- request intent
- foreign-key-like cleanup responsibility
- final destructive action

That separation reduces coupling and prevents premature deletion of shared references.

## Security Architecture

The platform implements a layered security model.

### 1. Identity provider

`user-service` is the only service that stores credentials and issues tokens.

Public authentication endpoints:

- `POST /auth/register`
- `POST /auth/login`

Authentication controller:

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody AuthRegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return authService.login(request);
    }
}
```

Authentication service:

```java
public AuthResponse login(AuthLoginRequest request) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(request.getLogin());

    // The login flow validates both the password and the activation flag.
    if (!userDetails.isEnabled() || !passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
        throw new BadCredentialsException("Invalid login or password");
    }

    List<String> roles = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replaceFirst("^ROLE_", ""))
            .toList();

    // The issued JWT contains username, roles, issue time, and expiration.
    String token = jwtTokenService.generateToken(userDetails.getUsername(), roles);
    return new AuthResponse(token, "Bearer", userDetails.getUsername(), roles, jwtTokenService.getExpiration());
}
```

### 2. Password protection

Passwords are never persisted in plaintext.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // BCrypt is used for password hashing and password verification.
    return new BCryptPasswordEncoder();
}
```

Whenever a user is created or registered:

```java
e.setPassword(passwordEncoder.encode(req.getPassword()));
```

### 3. JWT generation

The token generator uses the shared secret from Config Server.

```java
public String generateToken(String username, List<String> roles) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusMillis(jwtProperties.getExpiration());

    return Jwts.builder()
            // The token carries the authenticated principal name.
            .claim("username", username)
            // Roles are embedded so the gateway can reconstruct authorization context.
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey())
            .compact();
}
```

### 4. Gateway-level JWT validation

The gateway is responsible for rejecting unauthenticated traffic before it reaches internal services.

```java
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (isWhitelisted(path)) {
        // Authentication endpoints and actuator endpoints bypass token checks.
        return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
        return unauthorized(exchange);
    }

    String token = authHeader.substring(7).trim();

    try {
        JwtPayload payload = jwtTokenValidator.validate(token);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Trusted identity headers are reconstructed by the gateway, not by the client.
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Roles");
                    headers.set("X-User-Name", payload.username());
                    headers.set("X-User-Roles", String.join(",", payload.roles()));
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    } catch (JwtException | IllegalArgumentException ex) {
        return unauthorized(exchange);
    }
}
```

This establishes three critical guarantees:

- missing token -> `401 Unauthorized`
- invalid or expired token -> `401 Unauthorized`
- valid token -> propagated identity context

### 5. Downstream service trust model

`user-service` and `company-service` do not validate JWT signatures themselves. Instead, they trust headers injected by the gateway and rebuild a Spring Security authentication object.

```java
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

    String username = request.getHeader("X-User-Name");
    String rolesHeader = request.getHeader("X-User-Roles");

    if (StringUtils.hasText(username)) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(
                StringUtils.hasText(rolesHeader) ? rolesHeader.split(",") : new String[0])
                .map(String::trim)
                .filter(role -> !role.isBlank())
                // USER becomes ROLE_USER, ADMIN becomes ROLE_ADMIN.
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(username, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
}
```

### 6. Service-level authorization

Every downstream service still enforces authorization locally.

Security chain:

```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers(whitelistProperties.getWhitelist().toArray(String[]::new)).permitAll()
            .anyRequest().authenticated())
    .addFilterBefore(gatewayHeaderAuthenticationFilter, AnonymousAuthenticationFilter.class);
```

Method security:

```java
@GetMapping
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public List<UserResponse> getAll(HttpServletRequest request) {
    touchSecurityContext(request);
    return userService.findAll();
}

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasRole('ADMIN')")
public UserResponse create(@Valid @RequestBody UserCreateRequest req, HttpServletRequest request) {
    touchSecurityContext(request);
    return userService.create(req);
}
```

The controller intentionally touches the servlet security APIs required by the platform design:

```java
private void touchSecurityContext(HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    request.isUserInRole("USER");
    request.isUserInRole("ADMIN");
    if (principal == null) {
        throw new IllegalStateException("Authenticated principal is required");
    }
}
```

### 7. Feign security context propagation

Inter-service calls preserve caller identity by forwarding security headers.

```java
@Bean
public RequestInterceptor securityHeaderForwardingInterceptor() {
    return template -> {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();

        // The interceptor forwards the caller's security context to downstream services.
        forwardHeader(request, template, "Authorization");
        forwardHeader(request, template, "X-User-Name");
        forwardHeader(request, template, "X-User-Roles");
    };
}
```

This is critical for multi-hop scenarios, for example:

- an authenticated administrator calls `company-service`
- `company-service` invokes `user-service` through Feign
- the downstream service still sees the original identity and roles

### 8. Seeded administrator

To enable immediate testing of secured flows, the application seeds a default administrator if it does not already exist.

```java
if (userRepository.existsByLogin("admin")) {
    return;
}

UserEntity admin = new UserEntity();
admin.setLogin("admin");
admin.setPassword(passwordEncoder.encode("admin123"));
admin.setRoleList(List.of(UserRole.ADMIN.name(), UserRole.USER.name()));

userRepository.save(admin);
```

## Public API Surface Through the Gateway

### Authentication

- `POST /auth/register`
- `POST /auth/login`

### User endpoints

- `GET /user`
- `POST /user`
- `PUT /user/{id}`
- `PATCH /user/{id}/active?value=true|false`
- `GET /user/exists/{id}`
- `GET /user/{id}/name`
- `GET /user/description`

### Company endpoints

- `GET /company`
- `POST /company`
- `DELETE /company/{id}`
- `GET /company/exists/{id}`
- `GET /company/{id}/name`
- `GET /company/description`

## Containerization and Runtime Topology

The entire platform is orchestrated with Docker Compose.

Services included in `docker-compose.yml`:

- `postgres`
- `kafka`
- `config-server`
- `discovery-server`
- `api-gateway`
- `user-service`
- `company-service`

Representative infrastructure excerpt:

```yaml
services:
  postgres:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./db/init:/docker-entrypoint-initdb.d

  kafka:
    image: confluentinc/cp-kafka:7.7.0
    environment:
      # The broker runs in KRaft mode and advertises the Docker hostname.
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

  user-service:
    environment:
      CONFIG_SERVER_URL: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-server:8761/eureka/
      USER_DB_URL: jdbc:postgresql://postgres:5432/unik?currentSchema=user_schema
      COMPANY_SERVICE_BASE_URL: http://api-gateway:8080/company
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

The Docker network guarantees that services resolve each other by container name:

- `config-server`
- `discovery-server`
- `api-gateway`
- `postgres`
- `kafka`

## Running the Platform

### Start the full stack

```powershell
docker compose up --build -d
```

### Inspect running containers

```powershell
docker compose ps
```

### Stop the stack

```powershell
docker compose down
```

## Demonstration and Validation Scripts

The repository contains PowerShell scenarios in the project root.

### Legacy domain scenario scripts

- `create.ps1` - creates a director, a company, and an employee linked to that company
- `delete.ps1` - deletes the company and prints the resulting state
- `demo.ps1` - runs the end-to-end deletion scenario

### Security and full-platform validation

- `demo5.ps1` - starts or reuses the stack, waits for readiness, executes authentication and authorization checks, verifies service protection, validates Kafka-based deletion, and writes a machine-readable report to `demo-report.json`

Run the complete security and integration scenario:

```powershell
powershell -ExecutionPolicy Bypass -File .\demo5.ps1
```

Reuse an already running stack:

```powershell
powershell -ExecutionPolicy Bypass -File .\demo5.ps1 -SkipDockerUp
```

The generated `demo-report.json` captures evidence for:

- infrastructure readiness
- config-server exposure of shared security settings
- successful admin login and user registration
- JWT payload correctness
- gateway rejection of missing and invalid tokens
- direct downstream service protection
- role-based authorization outcomes
- Kafka-based cleanup after company deletion

## Representative Runtime Guarantees Verified by `demo5.ps1`

The scenario verifies all critical control points:

- config-server returns `jwt.secret`, `jwt.expiration`, and whitelist patterns
- gateway exposes `/auth/**` without authentication
- `POST /auth/login` and `POST /auth/register` are publicly available
- JWT contains `username`, `roles`, and `exp`
- unauthenticated access to protected gateway routes returns `401`
- invalid JWT access returns `401`
- direct access to `user-service` and `company-service` without authentication returns `401`
- `USER` can perform read operations
- `USER` cannot create users or delete companies
- `ADMIN` can create companies, create users, and delete companies
- deleting a company triggers Kafka cleanup and nullifies `companyId` in related users

## Repository Layout

```text
.
+-- api-gateway/
+-- company-service/
+-- config-repo/
+-- config-server/
+-- db/
|   +-- init/
+-- discovery-server/
+-- user-service/
+-- docker-compose.yml
+-- create.ps1
+-- delete.ps1
+-- demo.ps1
+-- demo5.ps1
+-- demo-report.json
```

## Technology Stack

- Java 17
- Spring Boot
- Spring Cloud Config
- Spring Cloud Netflix Eureka
- Spring Cloud Gateway
- Spring Cloud OpenFeign
- Spring Security
- JJWT
- Spring for Apache Kafka
- Spring Data JPA
- Flyway
- PostgreSQL 16
- Apache Kafka
- Docker Compose
- PowerShell automation for repeatable demonstration flows

## Summary

This repository is not a set of isolated examples. It is a single integrated system in which configuration management, service discovery, routing, synchronous validation, asynchronous consistency, and zero-session token-based security are implemented as cooperating architectural layers.

The result is a reproducible microservice platform with:

- externalized operational control
- schema-isolated persistence
- synchronous domain validation
- event-driven cleanup for destructive workflows
- centralized identity issuance
- layered authorization enforcement
- containerized deployment and scripted verification
