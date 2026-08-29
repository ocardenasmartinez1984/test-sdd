# DEPRECATED

Esta carpeta es la versión antigua del frontend POS.

**No usar. La versión actual es `../../pos-frontend/`.**

## Por qué quedó aquí

Esta UI se reemplazó por `pos-frontend/` (Angular 18, puerto 4300) que es
la que está integrada en `docker-compose.yml`, `Jenkinsfile` y `k8s/`.

## Qué hacer con esto

- Para trabajo nuevo: ve a `pos-frontend/`.
- Para comparar con la versión antigua: el código sigue aquí por ahora.
- Cuando confirmes que no se necesita, bórrala.

## Lo que NO encontrarás aquí

- Referencias en `docker-compose.yml` (usa `pos-frontend`).
- Referencias en `Jenkinsfile` (solo construye `pos-frontend`).
- Referencias en `k8s/05-frontend.yaml` (despliega `pos-frontend`).

Esta carpeta está aislada del resto del sistema.
