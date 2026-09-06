---
name: create_interview
description: Genera una guia de estudio en formato "simulacion de entrevista tecnica" sobre una tecnologia o base de conocimiento (Spring Security, Kafka, Redis, SAGA, etc.), anclada al codigo real de este proyecto POS. Produce los tres artefactos en interview-simulator/ - README_<TEMA>.md, README_<TEMA>.html (tema oscuro + CSS de impresion) y README_<TEMA>.pdf. Usalo cuando pidan crear/actualizar una entrevista con /create_interview <tecnologia>.
---

# Crear simulacion de entrevista sobre una tecnologia (anclada al repo)

Eres un ingeniero senior que redacta una **guia de estudio en formato entrevista
tecnica** sobre la tecnologia o base de conocimiento `$ARGUMENTS`
(p. ej. `spring-security`, `kafka`, `redis`, `saga`, `kubernetes`), **anclada al
codigo real** de este proyecto POS de microservicios (Spring Boot + Spring Cloud
Gateway + Eureka + Kafka + MongoDB/PostgreSQL/Redis + 3 frontends Angular).

El objetivo principal es **producir el contenido de entrevista** (preguntas del
entrevistador + respuestas modelo + follow-ups) que ensene como se usa esa
tecnologia **en este repositorio concreto**, citando clases, propiedades,
topicos, endpoints y puertos reales. La conversion a HTML/PDF es solo el paso
final para empaquetar ese contenido.

El resultado son **tres archivos hermanos** en `interview-simulator/`, con el
mismo estilo que los existentes (`README_SAGA_OUTBOX.*`, `README_KAFKA.*`,
`README_REDIS.*`, ...):

- `README_<TEMA>.md`   — fuente Markdown (el contenido)
- `README_<TEMA>.html` — HTML con tema oscuro y `@media print`
- `README_<TEMA>.pdf`  — PDF (Chrome headless desde el HTML)

Donde `<TEMA>` es el argumento en MAYUSCULAS con `-`/espacios sustituidos por
`_` (ej. `spring-security` -> `SPRING_SECURITY`).

## Procedimiento

### 1. Definir tema y nombre de archivo
- `SLUG = mayusculas($ARGUMENTS)`, sustituyendo ` ` y `-` por `_`.
- Base de archivos: `interview-simulator/README_<SLUG>`.
- Si ya existen, avisa que los vas a **sobrescribir** antes de continuar.

### 2. Investigar la tecnologia EN EL CODIGO REAL (paso clave, obligatorio)
No inventes ni escribas una entrevista generica: la guia debe reflejar como se
usa `$ARGUMENTS` **en este repo**. Antes de redactar, investiga a fondo y toma
notas de nombres reales que luego citaras en las respuestas:

- Busca configuracion y dependencias: `build.gradle` (starters/librerias),
  `application.yml`/`.properties`, `docker-compose.yml`, manifiestos `k8s/`.
- Localiza las clases/beans relevantes al tema (p. ej. para `spring-security`:
  `SecurityFilterChain`, `JwtAuthenticationFilter`, `UserDetailsService`,
  endpoints `/api/v1/auth/login`; para `kafka`: `@KafkaListener`, topicos como
  `stock-reserve-topic`, serializers, grupos de consumidor; para `redis`:
  `RedisRateLimiter`, `KeyResolver`, etc.).
- Anota puertos, nombres de contenedor, topicos, colecciones/tablas, estados de
  la maquina de estados y flujos concretos del proyecto.
- Usa las herramientas de busqueda de codigo (search de simbolos, grep) y lee
  los archivos relevantes. Cada afirmacion tecnica del `.md` debe poder
  rastrearse a algo que viste en el codigo.

> Regla de oro: si no lo verificaste en el repo, no lo afirmes. Cita nombres
> reales (clases, propiedades, topicos, endpoints, puertos), como hacen las
> entrevistas existentes.

### 3. Redactar el Markdown (`README_<SLUG>.md`)
Sigue **exactamente** la convencion de los MD existentes:
- `# Simulación de Entrevista Técnica — <Tema>`
- Uno o dos `> blockquote` de intro: de que trata, formato sugerido, duracion y
  bloques de la entrevista.
- Secciones numeradas `## 0. Warm-up — <tema> en el proyecto`, `## 1. ...`, ...
  y un `## Apéndice — Chuleta rápida` final con una tabla resumen.
- Empieza el Warm-up siempre con "como se usa <tema> en este sistema POS",
  respondido con detalles reales del repo.
- Cada pregunta/respuesta usa este patron literal (el conversor lo detecta):
  ```
  **P.** ¿Pregunta del entrevistador?

  **R.** Respuesta modelo, anclada al repo, con `código`/`Clases` reales y **negritas**.

  *Follow-up:* Pregunta de seguimiento — respuesta breve.
  ```
- Escribe en **espanol**, tono de entrevista, respuestas correctas y concretas.
  Apunta a 6-8 secciones y 15-25 preguntas. Mezcla fundamentos de la tecnologia
  con su aplicacion especifica en este proyecto.

### 4. Generar HTML + PDF con el conversor incluido
La skill trae `render.py`, que convierte el `.md` al `.html` (estilo del
proyecto) y luego el `.html` al `.pdf`. Ejecutalo desde la raiz del repo:

```bash
python3 .kiro/skills/create_interview/render.py interview-simulator/README_<SLUG>.md
```

El script convierte los marcadores `**P.**` / `**R.**` / `*Follow-up:*` en los
bloques `.q` / `.a` / `.followup`, aplica el `<style>` de tema oscuro +
`@media print` identico al de los HTML existentes, genera la TOC desde los
encabezados `## `, y renderiza el PDF con Chrome headless. Requiere `python3`
con el modulo `markdown` (`pip install markdown` si falta) y un binario
Chrome/Chromium. Usa `--no-pdf` para omitir el PDF.

### 5. Verificar
- `ls -la interview-simulator/README_<SLUG>.{md,html,pdf}` — los tres existen y
  el PDF pesa > 0 bytes.
- Revisa el HTML: TOC, bloques Q/A y bloques de codigo se ven bien.
- Repasa que las respuestas citen elementos reales del repo (no genericos).

## Reglas
- **Ancla todo al codigo real del repo**; verifica antes de afirmar. Cero
  contenido inventado.
- Respeta el naming `README_<SLUG>.{md,html,pdf}` en `interview-simulator/`.
- Manten el mismo estilo visual y estructura que las entrevistas existentes.
- No imprimas secretos (tokens, contrasenas) en claro; redactalos.
- Al terminar, reporta: tecnologia, ruta de los 3 archivos, numero de preguntas
  y 2-3 ejemplos de elementos reales del repo que citaste.
