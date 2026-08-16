# Saga Microservices - Guía de Configuración CI/CD

## Arquitectura

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│   GitHub    │────▶│   Jenkins    │────▶│   SonarQube     │
│  (código)   │     │  (CI/CD)     │     │  (análisis)     │
└─────────────┘     └──────────────┘     └─────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │Docker Images │
                    └──────────────┘
```

## Servicios del Proyecto

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| Jenkins | 8888 | Servidor CI/CD |
| SonarQube | 9000 | Análisis de código |
| PostgreSQL (auth) | 5432 | BD para auth-service |
| PostgreSQL (sonar) | - | BD interna para SonarQube |
| MongoDB | 27017 | BD para stock, venta, despacho |
| Kafka | 9092 | Mensajería entre microservicios |
| Zookeeper | 2181 | Coordinación para Kafka |
| Eureka Server | 8761 | Service Discovery |
| API Gateway | 8080 | Gateway de entrada |
| Auth Service | 8084 | Autenticación y autorización |
| Stock Service | 8081 | Gestión de inventario |
| Venta Service | 8082 | Gestión de ventas |
| Despacho Service | 8083 | Gestión de despachos |
| Frontend Admin | 4200 | Panel de administración (Angular) |
| Frontend POS | 4300 | Punto de venta (Angular) |

---

## 1. Levantar Infraestructura CI/CD

### Requisitos previos
- Docker y Docker Compose instalados
- Al menos 4 GB de RAM disponible para los contenedores

### Iniciar Jenkins y SonarQube

```bash
docker compose up -d jenkins sonarqube postgres-sonar
```

Verificar que los contenedores estén corriendo:

```bash
docker ps --filter "name=saga-jenkins" --filter "name=saga-sonarqube" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

---

## 2. Configurar Jenkins

### 2.1 Desbloquear Jenkins

1. Abrir http://localhost:8888
2. Obtener la contraseña inicial:

```bash
docker exec saga-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

3. Pegar la contraseña y continuar

### 2.2 Instalar Plugins

En la pantalla de plugins, seleccionar **"Install suggested plugins"** y adicionalmente instalar:

- **SonarQube Scanner** (para análisis de código)
- **NodeJS** (para builds de frontend)
- **Pipeline** (ya incluido en sugeridos)
- **Git** (ya incluido en sugeridos)

Ir a: Manage Jenkins → Plugins → Available plugins → buscar e instalar.

### 2.3 Configurar Herramientas (Tools)

Ir a: **Manage Jenkins → Tools**

#### JDK
- Name: `jdk-17`
- Instalar automáticamente o apuntar a una instalación existente

#### Gradle
- Name: `gradle-9.6`
- Install from Gradle.org: versión `9.6`

#### NodeJS
- Name: `node-20`
- Install from nodejs.org: versión `20.x`

### 2.4 Crear el Job del Pipeline

1. New Item → nombre: `test-add` → tipo: **Pipeline**
2. En la sección Pipeline:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/ocardenasmartinez1984/test-sdd.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
3. Guardar

---

## 3. Configurar SonarQube

### 3.1 Acceso inicial

1. Abrir http://localhost:9000
2. Login con credenciales por defecto:
   - Usuario: `admin`
   - Contraseña: `admin`
3. SonarQube pedirá cambiar la contraseña en el primer acceso

### 3.2 Generar Token de Autenticación

1. Ir a: **My Account** (ícono de usuario arriba a la derecha) → **Security**
2. En "Generate Tokens":
   - Name: `jenkins`
   - Type: `Global Analysis Token`
   - Expires in: `No expiration` (o el período que prefieras)
3. Click en **Generate**
4. **Copiar el token** (solo se muestra una vez)

### 3.3 Crear Proyecto en SonarQube (opcional)

SonarQube creará el proyecto automáticamente en el primer análisis, pero si quieres crearlo manualmente:

1. Projects → Create Project → Manually
2. Project Key: `saga-microservices`
3. Display Name: `Saga Microservices`

---

## 4. Conectar Jenkins con SonarQube

### 4.1 Guardar el Token como Credential en Jenkins

1. Ir a: **Manage Jenkins → Credentials → System → Global credentials**
2. Add Credentials:
   - Kind: **Secret text**
   - Secret: (pegar el token generado en SonarQube)
   - ID: `sonarqube-token`
   - Description: `SonarQube Token`

### 4.2 Configurar Servidor SonarQube en Jenkins

1. Ir a: **Manage Jenkins → System**
2. Buscar la sección **SonarQube servers**
3. Check: **Environment variables**
4. Click en **Add SonarQube**:
   - Name: `SonarQube`
   - Server URL: `http://sonarqube:9000`
   - Server authentication token: seleccionar `sonarqube-token`
5. Guardar

> **Nota:** La URL es `http://sonarqube:9000` (nombre del contenedor) porque Jenkins y SonarQube están en la misma red Docker (`saga-network`).

---

## 5. Pipeline - Stages

El pipeline (`Jenkinsfile`) ejecuta los siguientes stages:

| # | Stage | Descripción |
|---|-------|-------------|
| 1 | Checkout | Clona el repositorio desde GitHub |
| 2 | Prepare | Obtiene commit SHA y genera tag de imagen |
| 3 | Build Backend | Compila los 6 microservicios con Gradle |
| 4 | Unit Tests | Ejecuta pruebas unitarias de todos los servicios |
| 5 | Build Frontends | Compila Frontend Admin y Frontend POS en paralelo |
| 6 | SonarQube Analysis | Análisis de calidad de código |
| 7 | Docker Build | Construye imágenes Docker (deshabilitado) |
| 8 | Docker Push | Sube imágenes al registry (deshabilitado) |
| 9 | Deploy to Dev | Despliega con docker-compose (deshabilitado) |
| 10 | Deploy to Production | Despliega a Kubernetes (deshabilitado) |

Los stages 7-10 están deshabilitados con `when { expression { return false } }`. Habilitar según se vayan configurando los entornos.

---

## 6. Ejecutar el Pipeline

1. En Jenkins, ir al job `test-add`
2. Click en **Build Now**
3. Ver progreso en **Console Output** o en el gráfico de stages

---

## 7. Levantar la Aplicación Completa

Para levantar todos los microservicios junto con la infraestructura:

```bash
docker compose up -d
```

Para levantar solo la infraestructura de soporte (sin los microservicios):

```bash
docker compose up -d postgres mongodb zookeeper kafka
```

Para ver los logs de un servicio específico:

```bash
docker logs -f saga-jenkins
docker logs -f saga-sonarqube
```

---

## 8. Troubleshooting

### Jenkins no inicia
```bash
docker logs saga-jenkins
```

### SonarQube no inicia
SonarQube necesita un ajuste de kernel en Linux:
```bash
sudo sysctl -w vm.max_map_count=524288
```

En Windows con Docker Desktop esto no suele ser necesario.

### El análisis de SonarQube falla con "Not authorized"
- Verificar que el token esté correctamente configurado en Jenkins
- Verificar que el nombre del servidor en `withSonarQubeEnv('SonarQube')` coincida con el configurado en Jenkins

### Gradle Wrapper no encontrado
El archivo `gradle/wrapper/gradle-wrapper.jar` debe estar en el repositorio. Si falta:
```bash
gradle wrapper --gradle-version 8.9
git add gradle/wrapper/gradle-wrapper.jar
git commit -m "fix: add gradle-wrapper.jar"
```

---

## 9. Estructura del Proyecto

```
pos-test/
├── api-gateway/           # Spring Cloud Gateway
├── auth-service/          # Autenticación JWT + PostgreSQL
├── stock-service/         # Inventario con MongoDB + Kafka
├── venta-service/         # Ventas con MongoDB + Kafka
├── despacho-service/      # Despachos con MongoDB + Kafka
├── eureka-server/         # Service Discovery
├── frontend/              # Angular - Panel Admin
├── pos-frontend/          # Angular - Punto de Venta
├── k8s/                   # Manifiestos Kubernetes
├── gradle/wrapper/        # Gradle Wrapper
├── build.gradle           # Build raíz (plugins + subprojects)
├── settings.gradle        # Configuración multi-proyecto
├── docker-compose.yml     # Todos los servicios
├── Jenkinsfile            # Pipeline CI/CD
└── gradlew / gradlew.bat  # Scripts del wrapper
```
