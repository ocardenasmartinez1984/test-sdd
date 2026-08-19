# 📊 Kiro Monitor

Una aplicación de monitoreo de infraestructura y aplicaciones similar a Dynatrace, construida con Node.js.

![Status](https://img.shields.io/badge/status-active-green)
![Node](https://img.shields.io/badge/node-18%2B-blue)

## ✨ Características

- **Dashboard en tiempo real** - Gráficos interactivos con Chart.js y WebSocket
- **Recolección de métricas** - CPU, memoria, disco, red, procesos, temperatura, batería
- **Sistema de alertas** - Reglas configurables con umbrales, duración y cooldown
- **Multi-host** - Soporte para monitorear múltiples máquinas
- **Dark theme** - Interfaz moderna estilo Dynatrace
- **API REST** - Endpoints para consultar métricas y configurar alertas

## 🏗 Arquitectura

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   Agent          │  HTTP   │   Server         │   WS    │   Dashboard      │
│   (collector.js) │ ──────► │   (server.js)    │ ──────► │   (index.html)   │
│                  │  POST   │                  │  push   │                  │
│  - CPU           │         │  - API REST      │         │  - Charts        │
│  - Memory        │         │  - WebSocket     │         │  - Processes     │
│  - Disk          │         │  - Alert Engine  │         │  - Alerts        │
│  - Network       │         │  - Metrics Store │         │  - Summary       │
│  - Processes     │         │                  │         │                  │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

## 🚀 Inicio Rápido

### Requisitos
- Node.js 18+
- npm

### Instalación

```bash
# Entrar a la carpeta del monitor
cd monitor

# Instalar dependencias
npm install

# Ejecutar todo (servidor + agente)
npm run dev

# O ejecutar por separado:
npm start      # Solo servidor
npm run agent  # Solo agente
```

### Acceder al Dashboard

Abre http://localhost:3000 en tu navegador.

## 📡 API REST

### Métricas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/metrics` | Enviar métricas desde un agente |
| GET | `/api/hosts` | Listar todos los hosts monitoreados |
| GET | `/api/metrics/:hostId` | Obtener métricas de un host |
| GET | `/api/metrics/:hostId?limit=N` | Últimas N entradas |

### Alertas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/alerts/rules` | Obtener reglas de alertas |
| POST | `/api/alerts/rules` | Crear/actualizar regla |
| DELETE | `/api/alerts/rules/:name` | Eliminar regla |
| GET | `/api/alerts/history` | Historial de alertas |

### Ejemplo: Crear una regla de alerta

```bash
curl -X POST http://localhost:3000/api/alerts/rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "CPU Critical",
    "metric": "cpu.usage",
    "operator": ">",
    "threshold": 95,
    "severity": "critical",
    "duration": 2,
    "message": "CPU above 95% for 2 checks"
  }'
```

## ⚙️ Configuración

### Variables de entorno del Agente

| Variable | Default | Descripción |
|----------|---------|-------------|
| `MONITOR_SERVER` | `http://localhost:3000` | URL del servidor |
| `COLLECT_INTERVAL` | `5000` | Intervalo de recolección (ms) |
| `HOST_ID` | hostname del sistema | Identificador del host |

### Variables de entorno del Servidor

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `3000` | Puerto del servidor |

## 📁 Estructura del Proyecto

```
monitor/
├── package.json
├── README.md
└── src/
    ├── server.js              # Servidor principal (API + WebSocket)
    ├── test.js                # Tests
    ├── agent/
    │   └── collector.js       # Agente recolector de métricas
    ├── alerts/
    │   └── alertEngine.js     # Motor de alertas
    └── public/
        └── index.html         # Dashboard web
```

## 🔔 Reglas de Alertas por Defecto

| Regla | Métrica | Umbral | Severidad |
|-------|---------|--------|-----------|
| High CPU Usage | cpu.usage | > 90% | critical |
| High Memory Usage | memory.usedPercent | > 85% | warning |
| Critical Memory | memory.usedPercent | > 95% | critical |
| High Disk Usage | filesystem.0.usedPercent | > 90% | warning |

## 🔌 Métricas Recolectadas

- **CPU**: Uso total, por core, sistema vs usuario
- **Memoria**: Total, usado, libre, porcentaje, swap
- **Disco**: I/O lectura/escritura, bytes/sec
- **Filesystem**: Espacio usado por partición
- **Red**: RX/TX bytes, bytes/sec por interfaz
- **Procesos**: Total, running, sleeping, top 5 por CPU/memoria
- **Sistema**: Uptime, plataforma, arquitectura, load average
- **Temperatura**: CPU principal y por core
- **Batería**: Porcentaje, cargando, tiempo restante

## 🛠 Desarrollo

```bash
# Ejecutar en modo desarrollo
npm run dev

# Ejecutar tests básicos
npm test
```

## 📝 Licencia

MIT
