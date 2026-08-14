#!/bin/bash
# Script de despliegue a Kubernetes
# Ejecutar: chmod +x deploy.sh && ./deploy.sh

set -e

echo "============================================"
echo "  SAGA Microservices - Deploy to Kubernetes"
echo "============================================"
echo ""

# 1. Build de imágenes Docker
echo ">>> Construyendo imágenes Docker..."
docker build -t saga/stock-service:latest -f stock-service/Dockerfile .
docker build -t saga/venta-service:latest -f venta-service/Dockerfile .
docker build -t saga/despacho-service:latest -f despacho-service/Dockerfile .
docker build -t saga/auth-service:latest -f auth-service/Dockerfile .
docker build -t saga/frontend:latest -f frontend/Dockerfile .
echo "✅ Imágenes construidas"
echo ""

# 2. Aplicar manifiestos de Kubernetes
echo ">>> Aplicando manifiestos de Kubernetes..."
kubectl apply -f k8s/00-namespace.yaml
echo "  ✓ Namespace creado"

kubectl apply -f k8s/01-mongodb.yaml
echo "  ✓ MongoDB desplegado"

kubectl apply -f k8s/02-postgres.yaml
echo "  ✓ PostgreSQL desplegado"

kubectl apply -f k8s/03-kafka.yaml
echo "  ✓ Zookeeper + Kafka desplegados"

echo ""
echo ">>> Esperando a que la infraestructura esté lista..."
kubectl -n saga wait --for=condition=ready pod -l app=mongodb --timeout=120s
kubectl -n saga wait --for=condition=ready pod -l app=postgres --timeout=120s
kubectl -n saga wait --for=condition=ready pod -l app=kafka --timeout=120s
echo "✅ Infraestructura lista"
echo ""

# 3. Desplegar microservicios
echo ">>> Desplegando microservicios..."
kubectl apply -f k8s/04-microservices.yaml
echo "  ✓ auth-service, stock-service, venta-service, despacho-service"

kubectl apply -f k8s/05-frontend.yaml
echo "  ✓ frontend"
echo ""

# 4. Esperar a que todo esté listo
echo ">>> Esperando a que todos los pods estén Ready..."
kubectl -n saga wait --for=condition=ready pod -l app=auth-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=stock-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=venta-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=despacho-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=frontend --timeout=60s
echo ""

# 5. Mostrar estado
echo "============================================"
echo "  ✅ DESPLIEGUE COMPLETADO"
echo "============================================"
echo ""
echo "Pods:"
kubectl -n saga get pods -o wide
echo ""
echo "Services:"
kubectl -n saga get svc
echo ""
echo "============================================"
echo "  Frontend: http://localhost:30080"
echo "  Login:    admin / admin123"
echo "============================================"
