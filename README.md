# asynchronous-microservices-interaction-Kafka

## Overview

This repository implements asynchronous inter-service communication for a microservice-based system using Apache Kafka.
The solution extends an existing distributed environment that already includes:

- PostgreSQL as the persistence layer
- Spring Cloud Config Server for centralized configuration
- Eureka Discovery Server for service registration and lookup
- API Gateway as the single external entry point
- `user-service` and `company-service` as domain microservices

The key business scenario implemented in this stage is **company deletion with asynchronous cleanup**:

1. A company is not removed immediately from the database.
2. It is first marked as deleted via a soft delete flag.
3. A Kafka event is published from `company-service`.
4. `user-service` consumes the event and sets `company_id = null` for all users associated with that company.
5. `user-service` publishes a completion event.
6. `company-service` consumes the completion event and physically deletes the company from the database.

This design guarantees that user records are detached from the company before the final database deletion is performed.

## Architecture

The complete runtime stack is orchestrated with Docker Compose and includes the following services:

- `postgres`
- `kafka`
- `config-server`
- `discovery-server`
- `api-gateway`
- `user-service`
- `company-service`

Kafka is deployed as a dedicated broker inside the same Docker network. Both business services access it using the internal hostname `kafka:9092`.

## What Was Implemented

### 1. Kafka Integration in the Infrastructure

Kafka was added to the shared `docker-compose.yml` file and connected to both business services through environment variables.

```yaml
kafka:
  image: confluentinc/cp-kafka:7.7.0
  ports:
    - "9092:9092"
  healthcheck:
    test: ["CMD-SHELL", "cub kafka-ready -b localhost:9092 1 30"]

user-service:
  environment:
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092

company-service:
  environment:
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

This ensures that Kafka is part of the same reproducible runtime environment as the rest of the distributed system.

### 2. Centralized Kafka Configuration via Config Server

Kafka-related properties were added to the centralized configuration repository.

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest

app:
  kafka:
    topics:
      company-deletion-requested: company-deletion-requested
      company-deletion-completed: company-deletion-completed
```

Each service also receives its own consumer group configuration:

```yaml
app:
  kafka:
    consumer-group: user-service-company-deletion-group
```

```yaml
app:
  kafka:
    consumer-group: company-service-company-deletion-group
```

This keeps message broker settings centralized and consistent across the system.

### 3. Soft Delete for Companies

The `CompanyEntity` model was extended with a soft delete flag:

```java
@Column(nullable = false)
private boolean deleted = false;
```

A Flyway migration updates the database schema accordingly:

```sql
alter table companies
    add column if not exists deleted boolean not null default false;

create index if not exists idx_companies_deleted on companies(deleted);
```

The repository layer ensures that regular business operations work only with non-deleted companies:

```java
Optional<CompanyEntity> findByIdAndDeletedFalse(Long id);

boolean existsByIdAndDeletedFalse(Long id);

List<CompanyEntity> findAllByDeletedFalse();

@Modifying
@Query("delete from CompanyEntity c where c.id = :id and c.deleted = true")
int deleteSoftDeletedById(@Param("id") Long id);
```

As a result, once a company is marked as deleted, it immediately disappears from search results and existence checks, while still remaining in the database until the asynchronous cleanup is completed.

### 4. Delete Endpoint in `company-service`

The company deletion endpoint is exposed as a standard REST operation:

```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable Long id) {
    companyService.delete(id);
}
```

The business logic performs a soft delete and then publishes a domain event:

```java
@Transactional
public void delete(Long id) {
    CompanyEntity company = companyRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new EntityNotFoundException("Company with id=" + id + " not found"));

    company.setDeleted(true);
    companyRepository.save(company);

    eventPublisher.publishEvent(new CompanyDeletionRequestedEvent(company.getId()));
}
```

If the company does not exist, a custom exception is thrown and transformed into a `404 Not Found` response by the global exception handler.

### 5. Kafka-Based Cleanup in `user-service`

`company-service` publishes the first Kafka event after the transaction commits:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCompanyDeletionRequested(CompanyDeletionRequestedEvent event) {
    kafkaTemplate.send(topic, String.valueOf(event.companyId()), payload);
}
```

`user-service` consumes that event:

```java
@KafkaListener(
        topics = "${app.kafka.topics.company-deletion-requested}",
        groupId = "${app.kafka.consumer-group}")
public void onMessage(String payload) {
    userService.detachUsersFromDeletedCompany(event.companyId());
}
```

The repository performs a bulk update:

```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update UserEntity u set u.companyId = null where u.companyId = :companyId")
int clearCompanyIdByCompanyId(@Param("companyId") Long companyId);
```

The service publishes a completion event after detaching users:

```java
@Transactional
public void detachUsersFromDeletedCompany(Long companyId) {
    int updatedUsers = userRepository.clearCompanyIdByCompanyId(companyId);
    eventPublisher.publishEvent(new CompanyDeletionCompletedEvent(companyId));
}
```

This guarantees that all affected users are updated before the company is physically removed.

### 6. Final Physical Deletion in `company-service`

`company-service` listens for the completion event:

```java
@KafkaListener(
        topics = "${app.kafka.topics.company-deletion-completed}",
        groupId = "${app.kafka.consumer-group}")
public void onMessage(String payload) {
    companyService.physicallyDelete(event.companyId());
}
```

The final database removal is executed only after the completion signal is received:

```java
@Transactional
public void physicallyDelete(Long id) {
    int deletedCount = companyRepository.deleteSoftDeletedById(id);
}
```

This two-stage process preserves consistency between `company-service` and `user-service`.

### 7. Kafka Bean Configuration

Both services contain explicit Kafka configuration classes to provide:

- `KafkaTemplate<String, String>` for message publishing
- `ConcurrentKafkaListenerContainerFactory<String, String>` for listeners
- `NewTopic` beans for the required topics

Example:

```java
@Bean
public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
}

@Bean
public NewTopic companyDeletionRequestedTopic(...) {
    return new NewTopic(topicName, 1, (short) 1);
}
```

This makes message production, consumption, and topic provisioning explicit and reproducible in both services.

## End-to-End Workflow

The implemented deletion scenario can be summarized as follows:

```text
DELETE /company/{id}
    -> company marked as deleted
    -> company-deletion-requested event published
    -> user-service consumes event
    -> all users with company_id = {id} are updated to null
    -> company-deletion-completed event published
    -> company-service consumes completion event
    -> company is physically deleted from the database
```

## Running the Stack

Start the entire environment with:

```powershell
docker compose up --build -d
```

## Demonstration Scripts

The repository includes helper PowerShell scripts in the project root:

- `create.ps1` creates a director, a company, and an employee linked to that company
- `delete.ps1` deletes the previously created company and displays the resulting system state
- `demo.ps1` runs the full scenario in one script

Run the creation step:

```powershell
powershell -ExecutionPolicy Bypass -File .\create.ps1
```

Run the deletion step:

```powershell
powershell -ExecutionPolicy Bypass -File .\delete.ps1
```

Run the complete end-to-end scenario:

```powershell
powershell -ExecutionPolicy Bypass -File .\demo.ps1
```

The file `.lab4-demo-state.json` is generated automatically to store the created entity identifiers between the creation and deletion steps.

## Expected Result of the Demonstration

After the deletion flow completes:

- the employee remains in the system
- the employee's `companyId` becomes `null`
- the company no longer appears in the company list
- `GET /company/exists/{id}` returns `404`
- the company record is physically removed from the database

## Relevant Source Files

- `docker-compose.yml`
- `config-repo/application.yml`
- `config-repo/user-service.yml`
- `config-repo/company-service.yml`
- `company-service/src/main/java/com/unik/company_service/domain/CompanyEntity.java`
- `company-service/src/main/resources/db/migration/V2__add_deleted_flag_to_companies.sql`
- `company-service/src/main/java/com/unik/company_service/repo/CompanyRepository.java`
- `company-service/src/main/java/com/unik/company_service/controller/CompanyController.java`
- `company-service/src/main/java/com/unik/company_service/service/CompanyService.java`
- `company-service/src/main/java/com/unik/company_service/messaging/CompanyDeletionRequestedProducer.java`
- `company-service/src/main/java/com/unik/company_service/messaging/CompanyDeletionCompletedListener.java`
- `company-service/src/main/java/com/unik/company_service/config/KafkaConfiguration.java`
- `user-service/src/main/java/com/unik/user_service/repo/UserRepository.java`
- `user-service/src/main/java/com/unik/user_service/service/UserService.java`
- `user-service/src/main/java/com/unik/user_service/messaging/CompanyDeletionRequestedListener.java`
- `user-service/src/main/java/com/unik/user_service/messaging/CompanyDeletionCompletedProducer.java`
- `user-service/src/main/java/com/unik/user_service/config/KafkaConfiguration.java`
- `create.ps1`
- `delete.ps1`
- `demo.ps1`

## Summary

This repository implements a reliable asynchronous deletion workflow using Kafka in a microservices environment.
The solution combines centralized configuration, event-driven communication, soft delete semantics, and final consistency-preserving physical cleanup.
It demonstrates how distributed services can coordinate destructive operations without relying on synchronous coupling.
