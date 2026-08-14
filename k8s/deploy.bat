@echo off
REM Script de despliegue a Kubernetes (Windows)
REM Requiere: docker, kubectl

echo ============================================
echo   SAGA Microservices - Deploy to Kubernetes
echo ============================================
echo.

echo ^>^>^> Construyendo imagenes Docker...
docker build -t saga/stock-service:latest -f stock-service/Dockerfile .
docker build -t saga/venta-service:latest -f venta-service/Dockerfile .
docker build -t saga/despacho-service:latest -f despacho-service/Dockerfile .
docker build -t saga/auth-service:latest -f auth-service/Dockerfile .
docker build -t saga/frontend:latest -f frontend/Dockerfile .
echo Imagenes construidas
echo.

echo ^>^>^> Aplicando manifiestos Kubernetes...
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-mongodb.yaml
kubectl apply -f k8s/02-postgres.yaml
kubectl apply -f k8s/03-kafka.yaml

echo.
echo ^>^>^> Esperando infraestructura...
kubectl -n saga wait --for=condition=ready pod -l app=mongodb --timeout=120s
kubectl -n saga wait --for=condition=ready pod -l app=postgres --timeout=120s
kubectl -n saga wait --for=condition=ready pod -l app=kafka --timeout=120s
echo Infraestructura lista
echo.

echo ^>^>^> Desplegando microservicios...
kubectl apply -f k8s/04-microservices.yaml
kubectl apply -f k8s/05-frontend.yaml

echo.
echo ^>^>^> Esperando pods...
kubectl -n saga wait --for=condition=ready pod -l app=auth-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=stock-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=venta-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=despacho-service --timeout=180s
kubectl -n saga wait --for=condition=ready pod -l app=frontend --timeout=60s

echo.
echo ============================================
echo   DESPLIEGUE COMPLETADO
echo ============================================
echo.
kubectl -n saga get pods
echo.
kubectl -n saga get svc
echo.
echo   Frontend: http://localhost:30080
echo   Login:    admin / admin123
echo ============================================
