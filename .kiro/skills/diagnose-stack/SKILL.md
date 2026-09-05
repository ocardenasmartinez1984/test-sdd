---
name: diagnose-stack
description: Diagnostica de forma sistematica problemas del stack de microservicios en Docker (servicios unhealthy, 502 en frontends, fallos de login, SAGA/Kafka, arranque lento de JVMs). Usalo cuando algo del stack POS no responde o falla.
---

# Diagnostico del stack de microservicios (POS / SAGA en Docker)

Eres un ingeniero SRE diagnosticando el stack de microservicios de este proyecto
(Spring Boot + Spring Cloud Gateway + Eureka + Kafka + MongoDB/PostgreSQL/Redis,
con 3 frontends Angular servidos por nginx). Diagnostica **de fuera hacia dentro**
y **de forma sistematica**, sin cambiar nada hasta confirmar la causa raiz.

Sintoma / area a diagnosticar: $ARGUMENTS

## Topologia de referencia

| Servicio           | Contenedor              | Puerto host | Notas                                  |
|--------------------|-------------------------|-------------|----------------------------------------|
| api-gateway        | saga-api-gateway        | 8080        | Unico punto de entrada. Rate limiting. |
| stock-service      | saga-stock-service      | 8081        | MongoDB + Kafka                        |
| venta-service      | saga-venta-service      | 8082        | MongoDB + Kafka + orquestador SAGA     |
| despacho-service   | saga-despacho-service   | 8083        | MongoDB + Kafka                        |
| auth-service       | saga-auth-service       | 8084        | JWT + PostgreSQL. Login: `/api/v1/auth/login` |
| eureka-server      | saga-eureka-server      | 8761        | Service discovery                      |
| ventas-mantenedor  | saga-ventas-mantenedor  | 4200        | nginx -> proxy_pass a saga-api-gateway |
| pos-frontend       | saga-pos-frontend       | 4300        | nginx -> proxy_pass a saga-api-gateway |
| users-mantenedor   | saga-users-mantenedor   | 4400        | nginx -> proxy_pass a saga-api-gateway |
| kafka/mongo/pg/redis | saga-{kafka,mongodb,postgres,redis} | 9092/27017/5432/6379 | Infra |

Red Docker: `test-sdd_saga-network`. Credenciales de prueba: `admin` / `admin123`.

## Procedimiento

Ejecuta los pasos en orden y **para en cuanto localices la capa que falla**.

1. **Estado general**
   - `docker compose ps` — revisa columnas STATUS (healthy/unhealthy/starting).
   - Anota que contenedores estan caidos o unhealthy.

2. **Aisla la capa** (de fuera hacia dentro). Para un fallo de request, prueba el
   mismo endpoint en cada nivel y compara codigos HTTP:
   - Frontend nginx:  `curl -s -o /dev/null -w "%{http_code}" http://localhost:<4200|4300|4400>/api/...`
   - API Gateway:     `curl ... http://localhost:8080/api/...`
   - Servicio directo:`curl ... http://localhost:<8081-8084>/api/...`
   - Si gateway/servicio responden 200 pero el frontend da **502** -> es nginx (ver paso 5).

3. **Logs del servicio sospechoso**
   - `docker logs <contenedor> 2>&1 | tail -40`
   - Busca `ERROR`/`Exception` y la linea `Started ...Application in Xs`.
   - Los `WARN` de `BeanPostProcessorChecker`/LoadBalancer son benignos.

4. **Arranque lento de JVM (falsos unhealthy)**
   - En maquinas cargadas estos servicios tardan **varios minutos** en arrancar.
   - El `healthcheck` de compose tiene `start_period` corto; puede marcar
     **unhealthy transitorio** aunque el arranque sea correcto.
   - Verifica el arranque real con: `docker logs <c> 2>&1 | grep -iE "Started .*Application"`.
   - No reinicies un servicio que todavia esta arrancando: espera y reverifica.

5. **502 Bad Gateway en un frontend (causa muy comun)**
   - Reproduce y mira el error de nginx: `docker logs <frontend> 2>&1 | tail -5`.
   - Si dice `connect() failed (111: Connection refused) ... upstream: http://<IP>:8080`:
     nginx cacheo una **IP antigua** del gateway. Ocurre tras recrear el gateway
     (`--force-recreate`) sin recrear los frontends, porque `proxy_pass` con
     hostname literal resuelve la IP **solo al arrancar**.
   - Fix inmediato: `docker exec <frontend> nginx -s reload` (repite en los 3 frontends).
   - Fix permanente: usar variable en `proxy_pass` para re-resolver por request:
     ```nginx
     location /api/ {
         set $upstream http://saga-api-gateway:8080;
         proxy_pass $upstream$request_uri;
     }
     ```

6. **Fallo de login**
   - Ruta correcta del backend: **`/api/v1/auth/login`** (no `/api/auth/login`).
   - Prueba directo: `curl -i -X POST http://localhost:8084/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'`
   - 200 con token -> backend OK; si el frontend falla, es proxy nginx (paso 5).
   - 401 -> credenciales/usuario deshabilitado. 404 -> ruta equivocada.

7. **Conectividad de red / discovery**
   - Misma red: `docker inspect <c> --format '{{json .NetworkSettings.Networks}}'`.
   - Prueba desde dentro: `docker exec <origen> wget -qO- http://<destino>:<puerto>/actuator/health`.
   - Registro en Eureka: revisa http://localhost:8761 o los logs `DiscoveryClient`.

8. **Kafka / SAGA**
   - Revisa logs de venta-service (orquestador) buscando timeouts de reserva de
     stock o de despacho, y confirma que Kafka este healthy.

## Reglas

- **No apliques cambios** (reload, restart, recreate, edicion de configs) hasta
  haber confirmado la causa raiz con evidencia (codigo HTTP, log concreto).
- Prefiere acciones **reversibles y de minimo alcance** (p.ej. `nginx -s reload`
  antes que recrear contenedores).
- Nunca imprimas secretos (tokens JWT, contrasenas) en claro; redactalos.
- Al terminar, entrega: **causa raiz**, **evidencia citada**, **fix aplicado o
  propuesto**, y si aplica, el **fix permanente**.
