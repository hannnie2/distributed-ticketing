# Distributed Ticketing System

A microservices-based event ticketing system built with Spring Boot.

## Overview

<!-- TODO: 1-2 paragraphs on what the app does, target users, and the headline use cases (browse events, hold seats, pay, confirm). -->

## Architecture

Four services communicate over RabbitMQ (async, outbox-relayed) and HTTP (sync, where needed). PostgreSQL is the system of record per service; Redis is used for short-lived holds and Pub/Sub fan-out for SSE.

| Service     | Port | Responsibility                                                |
|-------------|------|---------------------------------------------------------------|
| `event`     | 8080 | Event catalog: events, artists, venues, categories            |
| `inventory` | 8081 | Seat holds and deductions backed by Redis + Postgres          |
| `order`     | 8082 | Order lifecycle, payment orchestration, SSE status streaming  |
| `payment`   | 8083 | Stripe integration, PaymentIntent lifecycle                   |

Shared infrastructure: PostgreSQL, Redis, RabbitMQ.

### Architecture diagram
![Architecture diagram](docs/architecture.png)

### Key flow

## Tech stack

- **Java, Spring Boot** - microservice development
- **PostgreSQL** - data persistence
- **Redis** - hot state and cache
- **RabbitMQ** - asynchronous messaging
- **Docker** - containerization and deployment
- **Kubernetes** - container orchestration
- **AWS (API Gateway, Cognito)**
- **Stripe** - payment processing
- **Swagger/OpenAPI** - API documentation
- **Resend** - emailing

## Running locally

### Prerequisites

- Docker + Docker Compose
- JDK 17 (only if you want to run a service outside Docker)
- A Stripe test account 
- A Resend API key (optional; required for email)

### Environment

Copy the example file and fill in secrets:

```bash
cp .env.example .env
```

Required variables:

| Variable                 | Description                            |
|--------------------------|----------------------------------------|
| `POSTGRES_PASSWORD`      | Postgres superuser password            |
| `RABBITMQ_DEFAULT_USER`  | RabbitMQ user                          |
| `RABBITMQ_DEFAULT_PASS`  | RabbitMQ password                      |
| `STRIPE_SECRET_KEY`      | Stripe secret key (test mode)          |
| `RESEND_API_KEY`         | Resend API key (optional)              |

<!-- TODO: add any service-specific vars (DB names, JWT secrets, etc.) -->

### Start everything

```bash
docker compose up --build
```

Services come up at:

- Event:     http://localhost:8080
- Inventory: http://localhost:8081
- Order:     http://localhost:8082
- Payment:   http://localhost:8083
- User:      http://localhost:8084
- RabbitMQ management UI: http://localhost:15672

## API

TODO: replace with link to OpenAPI/Swagger.

## Deployment

<!-- TODO: describe how images are built/published, which environments exist, and how k8s/ is applied. -->

Kubernetes manifests live under `k8s/`. Apply with:

```bash
kubectl apply -f k8s/
```
