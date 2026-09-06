# logger-view

Small helper scripts to view the logs of each container in the POS
microservices stack. There is one script per container plus a shared helper
(`_common.sh`) that all of them use.

## Requirements

- The stack must be running (`docker compose ps` should list the containers).
- Docker CLI available on your PATH.

## Usage

Run the script for the container whose logs you want. **Logs stream live (real
time) by default** — press `Ctrl+C` to stop.

```bash
cd logger-view

./logs-api-gateway.sh          # stream the API gateway logs live
./logs-stock-service.sh        # stream the stock service logs live
./logs-venta-service.sh -n 50  # start from the last 50 lines, then keep streaming
./logs-kafka.sh -s 10m         # start from the last 10 minutes, then keep streaming
./logs-redis.sh -N             # print current logs and exit (no live streaming)
```

If you get `permission denied`, make them executable once:

```bash
chmod +x logger-view/*.sh
```

## Options

Every script accepts the same flags:

| Flag | Description | Default |
|------|-------------|---------|
| _(none)_           | Stream logs live (real time) | **on** |
| `-N`, `--no-follow`| Print current logs and exit (no streaming) | — |
| `-f`, `--follow`   | Force live streaming (already the default) | — |
| `-n`, `--tail N`   | Show the last N lines before streaming | 200 |
| `-s`, `--since T`  | Show logs since T (e.g. `10m`, `1h`, `2026-09-05T12:00`) | all (within tail) |
| `-h`, `--help`     | Show help for that script | — |

## Available scripts

Backend services (Java / Spring Boot):

| Script | Container | Port |
|--------|-----------|------|
| `logs-api-gateway.sh`      | saga-api-gateway      | 8080 |
| `logs-auth-service.sh`     | saga-auth-service     | 8084 |
| `logs-stock-service.sh`    | saga-stock-service    | 8081 |
| `logs-venta-service.sh`    | saga-venta-service    | 8082 |
| `logs-despacho-service.sh` | saga-despacho-service | 8083 |
| `logs-eureka-server.sh`    | saga-eureka-server    | 8761 |

Frontends (Angular + nginx):

| Script | Container | Port |
|--------|-----------|------|
| `logs-pos-frontend.sh`      | saga-pos-frontend      | 4300 |
| `logs-ventas-mantenedor.sh` | saga-ventas-mantenedor | 4200 |
| `logs-users-mantenedor.sh`  | saga-users-mantenedor  | 4400 |

Infrastructure:

| Script | Container | Port |
|--------|-----------|------|
| `logs-kafka.sh`    | saga-kafka    | 9092 |
| `logs-mongodb.sh`  | saga-mongodb  | 27017 |
| `logs-postgres.sh` | saga-postgres | 5432 |
| `logs-redis.sh`    | saga-redis    | 6379 |

## Notes

- `_common.sh` is a shared helper sourced by every script; you don't run it
  directly.
- If a container is not found, the script prints a friendly hint to check that
  the stack is up.
- To tail **all** services at once, prefer `docker compose logs -f` from the
  project root.
