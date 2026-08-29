#!/usr/bin/env bash
# Raise CPU/memory limits for the 6 Java service deploy blocks (by line number).
# Lines identified from grep: memory/cpus pairs for eureka, api-gateway, auth,
# stock, venta, despacho. Java startup is CPU-bound; 0.5 CPU makes boot ~80s.
set -euo pipefail

f=docker-compose.yml

# Java service memory lines -> 768M
for ln in 158 195 233 272 310 348; do
  sed -i "${ln}s/memory: 512M/memory: 768M/" "$f"
done

# Java service cpus lines -> "2.0"
for ln in 159 196 234 273 311 349; do
  sed -i "${ln}s/cpus: \"0.5\"/cpus: \"2.0\"/" "$f"
done

echo "done"
