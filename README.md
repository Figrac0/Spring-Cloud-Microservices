# Cloud-Native Microservices Platform with Centralized Configuration, Service Discovery and API Gateway

A production-style microservices platform built with Spring Boot, Spring Cloud Config Server, Eureka Service Discovery, Spring Cloud Gateway, PostgreSQL, Flyway and Docker Compose.

This project demonstrates how to assemble multiple independently deployed services into a single cloud-ready infrastructure with centralized configuration management, runtime service registration, intelligent request routing and containerized deployment.

## Overview

The platform includes the following core components:

- Config Server - centralized external configuration management
- Discovery Server - Eureka-based service registry
- API Gateway - single entry point for client traffic
- User Service - user management microservice
- Company Service - company management microservice
- PostgreSQL - persistent storage with separate schemas for services

The system is designed to show a realistic microservices workflow where services:

- obtain configuration from a central configuration server
- register themselves dynamically in a discovery registry
- communicate through a gateway
- persist data in PostgreSQL using isolated schemas
- apply schema changes through Flyway migrations
- run together in Docker containers as one infrastructure stack

## Architecture

Client requests are sent to the API Gateway on port `8080`.

The gateway routes requests by path:

- `/user/**` -> `user-service`
- `/company/**` -> `company-service`

Configuration is served centrally by the Config Server on port `8888`.

All runtime services participate in service discovery through Eureka on port `8761`.

The platform uses PostgreSQL as the database backend, while each microservice works with its own schema to preserve service-level data isolation.

## Technology Stack

- Java 17
- Spring Boot 4
- Spring Cloud 2025.x
- Spring Cloud Config Server
- Spring Cloud Netflix Eureka
- Spring Cloud Gateway
- Spring Data JPA
- Spring Web / WebFlux
- PostgreSQL 16
- Flyway
- Docker
- Docker Compose
- Maven

## Implemented Infrastructure

### 1. Centralized Configuration

The project includes a dedicated Config Server that loads external configuration files from a native repository.

Configuration files are maintained separately for each service, including:

- `user-service.yml`
- `company-service.yml`
- `api-gateway.yml`
- `discovery-server.yml`
- shared `application.yml`

A test profile is also supported for `user-service` with overridden configuration values.

This allows each service to remain lightweight while keeping runtime settings externalized and centrally managed.

### 2. Service Discovery

A dedicated Eureka server is used as the discovery layer.

The following services are integrated with service discovery:

- `user-service`
- `company-service`
- `api-gateway`

This makes the infrastructure more flexible by removing the need for hardcoded service locations at the routing layer.

### 3. API Gateway

The API Gateway acts as the unified external access point for the platform.

It performs request routing to downstream services using logical service names rather than fixed host addresses.

Configured routes include:

- `/user` -> `/users`
- `/company` -> `/companies`
- `/user/description` -> `/description`
- `/company/description` -> `/description`
- `/user/**` -> rewritten and forwarded to `user-service`
- `/company/**` -> rewritten and forwarded to `company-service`

This enables transparent client access while hiding internal service topology.

### 4. Synchronous Microservice Communication

The platform supports synchronous inter-service communication.

For example:

- the user service can enrich responses with company information
- the company service can validate or resolve user-related information

Requests between services are routed through the API Gateway, which reflects a more realistic microservice communication topology.

### 5. Database Isolation and Migrations

The services use PostgreSQL with independent schemas.

Examples:

- `user_schema`
- `company_schema`

Flyway is used to version and apply database migrations automatically on startup.

This approach keeps schema evolution explicit, traceable and repeatable.

### 6. Containerized Deployment

Each service has its own Dockerfile and the whole platform is started as a single containerized stack using Docker Compose.

The stack includes:

- PostgreSQL
- Config Server
- Discovery Server
- API Gateway
- User Service
- Company Service

This makes the project reproducible and easy to run in a local development or demonstration environment.

## Service Ports

Default exposed ports:

- `8888` - Config Server
- `8761` - Discovery Server
- `8080` - API Gateway
- `8081` - User Service
- `8082` - Company Service
- `5432` - PostgreSQL

## Key Capabilities

### Centralized service descriptions

Each service exposes its description from external configuration, demonstrating real integration with the Config Server.

Examples:

- `/user/description`
- `/company/description`

### Gateway-based access

Instead of calling business services directly, requests are sent to the gateway:

- `GET /user`
- `GET /company`

This confirms that the gateway is functioning as the platform entry point.

### Externalized environment-specific configuration

The project supports profile-specific overrides, including test-specific configuration for `user-service`.

### Refreshable runtime configuration

The services are prepared for external configuration refresh through Actuator and Spring Cloud mechanisms, allowing updated values to be applied without rebuilding the application.

## Example Platform Flow

1. Config Server starts and exposes centralized configuration.
2. Discovery Server starts and becomes the service registry.
3. User Service, Company Service and API Gateway start.
4. Each service reads its configuration from Config Server.
5. Runtime services register themselves in Eureka.
6. Client traffic is sent to API Gateway.
7. Gateway forwards requests to target services based on path rules.
8. Services access the database and, where needed, communicate synchronously.

## Why This Project Is Interesting

This project is not just a set of isolated Spring Boot applications. It is a complete cloud-oriented microservices platform that demonstrates several important enterprise patterns at once:

- centralized configuration management
- dynamic service discovery
- gateway-based routing
- database schema isolation
- migration-based persistence
- multi-container deployment
- synchronous service collaboration

It reflects the type of infrastructure commonly seen in distributed backend systems and is a strong practical example of modern Spring Cloud architecture.

## Project Structure

```text
Spring_Cloud_Config_Server_1/
├── config-repo/
├── config-server/
├── discovery-server/
├── api-gateway/
├── user-service/
├── company-service/
└── docker-compose.yml
```

## Educational and Engineering Value

This platform is useful both as an academic project and as a portfolio-ready backend engineering example because it covers:

- Spring Cloud ecosystem fundamentals
- service-to-service topology design
- runtime configuration externalization
- gateway-driven traffic management
- Docker-based deployment strategy
- practical microservice infrastructure assembly

## Future Extensions

Potential next steps for the platform include:

- circuit breakers and fault tolerance
- distributed tracing
- centralized logging
- authentication and authorization
- OpenAPI documentation
- message broker integration
- Kubernetes deployment

## Status

Current implementation demonstrates a working end-to-end microservices environment with:

- centralized configuration
- service discovery
- API gateway routing
- synchronous interaction
- database-backed services
- Flyway migrations
- Dockerized infrastructure
