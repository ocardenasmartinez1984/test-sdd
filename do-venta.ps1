$body = '{"customerId":"cliente-kafka-demo","productId":"6a7bdf9e86ba215e434a4ca6","quantity":3,"totalAmount":2699.97}'
Write-Host "=== Creando venta ==="
$r = Invoke-WebRequest -Uri "http://localhost:8082/api/ventas" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
Write-Host $r.Content
Write-Host ""
Write-Host "Esperando 5 segundos para que la SAGA se complete..."
Start-Sleep -Seconds 5
