# POS Microservices System

A cloud-native Point of Sale (POS) system built with microservices architecture using Spring Boot, Spring Cloud, Apache Kafka, and Angular. Implements the SAGA pattern for distributed transaction management across sales, inventory, and dispatch services.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              FRONTENDS                                       │
│  ┌──────────────┐   ┌───────────────────┐   ┌───────────────────┐          │
│  │ POS Frontend │   │ Ventas Mantenedor  │   │ Users Mantenedor  │          │
│  │   :4300      │   │      :4200         │   │      :4400        │          │
│  └──────┬───────┘   └────────┬───────────┘   └────────┬──────────┘          │
└─────────┼────────────────────┼─────────────────────────┼────────────────────┘
          │                    │                         │
          ▼                    ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY (:8080)                                    │
│                   Spring Cloud Gateway + Eureka                               │
└──────────┬──────────────────┬──────────────────────┬────────────────────────┘
           │                  │                      │
           ▼                  ▼                      ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────────┐
│  AUTH SERVICE  │  │ STOCK SERVICE  │  │   VENTA SERVICE    │
│    (:8084)     │  │   (:8081)      │  │     (:8082)        │
│  JWT + PGSQL   │  │ MongoDB+Kafka  │  │ MongoDB+Kafka+SAGA │
└────────────────┘  └───────┬────────┘  └─────────┬──────────┘
                            │                     │
                            │    ┌────────────────┘
                            │    │
                            ▼    ▼
                    ┌────────────────────┐
                    │  DESPACHO SERVICE  │
                    │     (:8083)        │
                    │  MongoDB + Kafka   │
                    └────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          INFRASTRUCTURE                                       │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌───────┐  ┌──────────────────┐  │
│  │  Kafka  │  │ MongoDB │  │PostgreSQL│  │ Redis │  │  Eureka (:8761)  │  │
│  │  :9092  │  │ :27017  │  │  :5432   │  │ :6379 │  │ Service Discovery│  │
│  └─────────┘  └─────────┘  └──────────┘  └───────┘  └──────────────────┘  │
│  ┌──────────┐  ┌──────────────┐  ┌──────────┐                              │
│  │ Zipkin   │  │  SonarQube   │  │ Jenkins  │                              │
│  │ :9411    │  │    :9000     │  │  :8888   │                              │
│  └──────────┘  └──────────────┘  └──────────┘                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### SAGA Flow (Sales Transaction)

```
Venta Service ──► Stock Reserve (Kafka) ──► Stock Service
     │                                           │
     │◄──── Stock Reserve Response ◄─────────────┘
     │
     ├──► Despacho Request (Kafka) ──► Despacho Service
     │                                       │
     │◄──── Despacho Response ◄──────────────┘
     │
     └──► Complete / Compensate
```

## Tech Stack

| Layer          | Technology                                    |
|----------------|-----------------------------------------------|
| Backend        | Java 21, Spring Boot 3.3, Spring Cloud 2023   |
| API Gateway    | Spring Cloud Gateway + Eureka Discovery       |
| Messaging      | Apache Kafka (KRaft mode in K8s)              |
| Databases      | MongoDB 7.0, PostgreSQL 16                    |
| Cache          | Redis                                         |
| Frontend       | Angular 18, TypeScript                        |
| Containerization | Docker, Kubernetes                          |
| CI/CD          | Jenkins, SonarQube, JaCoCo                    |
| Testing        | JUnit 5, Testcontainers, Gatling              |
| Monitoring     | Micrometer, Dynatrace, Custom Node.js Monitor |
| Tracing        | Zipkin                                        |

## Prerequisites

- **Java 21** (JDK)
- **Docker** & Docker Compose
- **Node.js 18+** (for frontends and monitor)
- **Gradle 8.9+** (wrapper included)

## Quick Start

```bash
# Clone the repository
git clone <repository-url>
cd pos-test
```

### Recommended: phased startup (Windows / PowerShell)

Starting all 19 containers at once (6 JVMs + Kafka + SonarQube + Jenkins) can
overwhelm the machine. Use the phased startup script, which brings the stack up
by layers and waits for each phase to become healthy:

```powershell
# Essential stack only (uses prebuilt images, starts in seconds)
./start-stack.ps1

# Rebuild images first (fast thanks to shared BuildKit Gradle/npm cache)
./start-stack.ps1 -Build

# Also start CI/CD + monitoring tools (Jenkins, SonarQube, Prometheus, Grafana)
./start-stack.ps1 -Tooling

# Tear everything down
./start-stack.ps1 -Down
```

### Manual startup with Docker Compose

The heavy tooling services (Jenkins, SonarQube, Prometheus, Grafana, Zipkin) are
behind Compose **profiles** and do **not** start by default:

```bash
# Enable BuildKit for the shared build caches
export DOCKER_BUILDKIT=1        # PowerShell: $env:DOCKER_BUILDKIT=1

# Essential stack (no tooling)
docker compose up -d

# Include tooling when you need it
docker compose --profile tooling up -d

# Only specific profiles
docker compose --profile monitoring up -d   # Prometheus + Grafana
docker compose --profile sonar up -d        # SonarQube + its Postgres
docker compose --profile ci up -d           # Jenkins
docker compose --profile tracing up -d      # Zipkin
```

> **Note:** The Java service Dockerfiles share a Gradle cache and the Angular
> Dockerfiles share an npm cache via BuildKit cache mounts, so rebuilds only
> download dependencies once. Always run with `DOCKER_BUILDKIT=1`.

Wait for all health checks to pass, then access:
- **POS Frontend**: http://localhost:4300
- **Ventas Mantenedor**: http://localhost:4200
- **Users Mantenedor**: http://localhost:4400
- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8761

## Service Ports

| Service             | Port  | Description                          |
|---------------------|-------|--------------------------------------|
| api-gateway         | 8080  | API Gateway (single entry point)     |
| stock-service       | 8081  | Inventory management                 |
| venta-service       | 8082  | Sales orchestrator (SAGA)            |
| despacho-service    | 8083  | Dispatch / shipping                  |
| auth-service        | 8084  | Authentication (JWT)                 |
| eureka-server       | 8761  | Service discovery                    |
| pos-frontend        | 4300  | Point of Sale UI                     |
| ventas-mantenedor   | 4200  | Sales management UI                  |
| users-mantenedor    | 4400  | User management UI                   |
| Kafka               | 9092  | Message broker                       |
| MongoDB             | 27017 | Document database                    |
| PostgreSQL          | 5432  | Relational database (auth)           |
| Redis               | 6379  | Cache (gateway rate limiting)        |
| SonarQube           | 9000  | Code quality                         |
| Jenkins             | 8888  | CI/CD                                |
| Zipkin              | 9411  | Distributed tracing                  |

## Environment Variables

Create a `.env` file at the project root:

```env
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auth_db
SPRING_DATASOURCE_USERNAME=auth_user
SPRING_DATASOURCE_PASSWORD=auth_pass
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Eureka
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# JWT
JWT_SECRET=DefaultSecretKeyThatShouldBeAtLeast256BitsLongForHS256Algorithm
JWT_EXPIRATION=86400000

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Dynatrace (optional)
DYNATRACE_ENABLED=false
DYNATRACE_URI=
DYNATRACE_API_TOKEN=

# Distributed tracing (optional). Default 0.0 = tracing off so services don't
# waste time exporting spans when Zipkin isn't running. Set to a value between
# 0.0 and 1.0 (e.g. 1.0 to sample everything) and start Zipkin with the
# `tracing` (or `tooling`) profile to collect traces.
TRACING_SAMPLING=0.0
```

## Performance & Startup Optimizations

The stack is tuned for fast, reliable local startup. Key optimizations:

### Compose profiles (lighter default stack)
Heavy tooling is behind Compose profiles and does **not** start by default, so a
normal run brings up only the 13 essential containers (infra + 6 Java services +
3 frontends) instead of 19:

| Profile      | Services                                             |
|--------------|------------------------------------------------------|
| _(default)_  | postgres, mongodb, kafka, redis, eureka, api-gateway, auth, stock, venta, despacho, and the 3 frontends |
| `tooling`    | everything below (jenkins, sonarqube, prometheus, grafana, zipkin) |
| `monitoring` | prometheus, grafana                                  |
| `sonar`      | sonarqube + its postgres                             |
| `ci`         | jenkins                                              |
| `tracing`    | zipkin                                               |

### CPU limits sized for JVM startup
Spring Boot startup is CPU-bound (JIT, class loading, classpath scanning). The
6 Java services are given `2.0` CPUs / `768M` each in `docker-compose.yml`.
Dropping this back to `0.5` CPU roughly **doubles** startup time (~36s → ~80s
per service), so keep at least 1–2 CPUs per JVM if you tune these values.

### Shared BuildKit caches
- Java Dockerfiles share a Gradle cache mount (`/root/.gradle`), so dependencies
  are downloaded once across all 6 service builds.
- Angular Dockerfiles use `npm ci` with a shared npm cache mount (`/root/.npm`).

Always build with BuildKit enabled (`start-stack.ps1` sets this automatically;
manual builds need `DOCKER_BUILDKIT=1`).

### JVM entrypoint tuning
Each Java service entrypoint applies `JAVA_OPTS` (heap limits) and
`-Djava.security.egd=file:/dev/./urandom` to avoid blocking on low entropy.

### UTF-8 BOM guard
`javac` rejects source files that begin with a UTF-8 BOM (`\ufeff`), which some
Windows editors add invisibly. Use `strip-bom.sh` to detect/remove BOMs from
`.java`, `.yml`, `.yaml`, and `.properties` files:

```bash
bash strip-bom.sh          # report files that have a BOM
bash strip-bom.sh --fix    # strip the BOM in place
```

## Development Setup

### Backend Services

```bash
# Build all services
./gradlew build

# Run individual service
./gradlew :stock-service:bootRun
./gradlew :venta-service:bootRun
./gradlew :despacho-service:bootRun
./gradlew :auth-service:bootRun
./gradlew :eureka-server:bootRun
./gradlew :api-gateway:bootRun
```

### Frontend Applications

```bash
# POS Frontend
cd pos-frontend
npm install
npm start  # http://localhost:4300

# Ventas Mantenedor
cd ventas-mantenedor
npm install
npm start  # http://localhost:4200

# Users Mantenedor
cd users-mantenedor
npm install
npm start  # http://localhost:4400
```

### API Documentation (Swagger)

Each service exposes OpenAPI documentation:
- Swagger UI: `http://localhost:<port>/swagger-ui.html`
- API Docs: `http://localhost:<port>/api-docs`

Aggregated via Gateway:
- http://localhost:8080/swagger-ui.html

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Tests (Testcontainers)

Integration tests use Testcontainers to spin up real MongoDB, PostgreSQL, and Kafka instances:

```bash
./gradlew integrationTest
```

### Stress Tests (Gatling)

```bash
cd stress-test
../gradlew gatlingRun

# Or use the PowerShell script
./stress-test.ps1
```

### Code Coverage

JaCoCo reports are generated automatically after tests:

```bash
./gradlew jacocoTestReport
# Reports at: <service>/build/reports/jacoco/test/html/index.html
```

### SonarQube Analysis

```bash
./gradlew sonar -Dsonar.host.url=http://localhost:9000
```

## CI/CD Pipeline

The project uses Jenkins with the following pipeline stages:

```
┌─────────┐    ┌───────┐    ┌──────┐    ┌───────────┐    ┌────────┐    ┌────────┐
│Checkout │───►│ Build │───►│ Test │───►│ SonarQube │───►│ Docker │───►│ Deploy │
└─────────┘    └───────┘    └──────┘    └───────────┘    └────────┘    └────────┘
```

1. **Checkout** - Pull source code
2. **Build** - Compile all services with Gradle
3. **Test** - Run unit and integration tests
4. **SonarQube** - Static code analysis + coverage
5. **Docker** - Build and push images
6. **Deploy** - Deploy to Kubernetes cluster

Jenkins is accessible at http://localhost:8888. See [SETUP-CICD.md](SETUP-CICD.md) for detailed setup instructions.

## Kubernetes Deployment

The `k8s/` directory contains deployment manifests:

```bash
# Deploy to Kubernetes
cd k8s

# Linux/Mac
./deploy.sh

# Windows
deploy.bat
```

Manifests are applied in order:
1. `00-namespace.yaml` - Create namespace
2. `01-mongodb.yaml` - MongoDB StatefulSet
3. `02-postgres.yaml` - PostgreSQL StatefulSet
4. `03-kafka.yaml` - Kafka (KRaft mode) deployment
5. `04-microservices.yaml` - All backend services
6. `05-frontend.yaml` - Frontend deployments

## Monitoring

### Custom Monitor Dashboard

A real-time monitoring dashboard built with Node.js:

```bash
cd monitor
npm install
npm start
```

Or with Docker:
```bash
cd monitor
docker-compose up
```

See [monitor/README.md](monitor/README.md) for details.

### Health Checks

All services expose health endpoints via Spring Actuator:
```
GET http://localhost:<port>/actuator/health
GET http://localhost:<port>/actuator/info
GET http://localhost:<port>/actuator/metrics
```

### Metrics

Services export metrics via Micrometer with optional Dynatrace integration. Set `DYNATRACE_ENABLED=true` and configure `DYNATRACE_URI` and `DYNATRACE_API_TOKEN` to enable.

## Project Structure

```
pos-test/
├── api-gateway/          # Spring Cloud Gateway
├── auth-service/         # Authentication (JWT + PostgreSQL)
├── stock-service/        # Inventory management (MongoDB + Kafka)
├── venta-service/        # Sales orchestrator - SAGA (MongoDB + Kafka)
├── despacho-service/     # Dispatch service (MongoDB + Kafka)
├── eureka-server/        # Service discovery
├── pos-frontend/         # Angular POS UI
├── ventas-mantenedor/    # Angular Sales Management UI
├── users-mantenedor/     # Angular User Management UI
├── frontend/             # Shared frontend (legacy)
├── monitor/              # Node.js monitoring dashboard
├── stress-test/          # Gatling stress tests
├── k8s/                  # Kubernetes manifests
├── jenkins/              # Jenkins Dockerfile
├── docker-compose.yml    # Full stack orchestration
├── Jenkinsfile           # CI/CD pipeline
├── build.gradle          # Root Gradle build
└── settings.gradle       # Multi-project settings
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'feat: add new feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

### Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation
- `refactor:` Code refactoring
- `test:` Tests
- `chore:` Maintenance

### Code Quality

- All code must pass SonarQube quality gate
- Minimum 80% test coverage
- No critical or blocker issues

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
