$productId = "6a7bdf9e86ba215e434a4ca6"

$ventas = @(
    '{"customerId":"cliente-001","productId":"' + $productId + '","quantity":2,"totalAmount":1799.98}',
    '{"customerId":"cliente-002","productId":"' + $productId + '","quantity":1,"totalAmount":899.99}',
    '{"customerId":"cliente-003","productId":"' + $productId + '","quantity":3,"totalAmount":2699.97}'
)

foreach ($v in $ventas) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8082/api/ventas" -Method POST -Body $v -ContentType "application/json" -UseBasicParsing
        Write-Host "Venta creada: $($r.Content)"
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
}
