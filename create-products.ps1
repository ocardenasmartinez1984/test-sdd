$ErrorActionPreference = "Continue"
$url = "http://localhost:8080/api/v1/stock"

$products = @(
    '{"sku":"LAPTOP-001","name":"Laptop HP ProBook 450","quantity":50,"price":899.99}',
    '{"sku":"LAPTOP-002","name":"MacBook Air M3","quantity":30,"price":1299.99}',
    '{"sku":"LAPTOP-003","name":"Dell XPS 15","quantity":25,"price":1499.99}',
    '{"sku":"LAPTOP-004","name":"Lenovo ThinkPad X1","quantity":40,"price":1199.99}',
    '{"sku":"MONITOR-001","name":"Samsung 27 4K","quantity":80,"price":349.99}',
    '{"sku":"MONITOR-002","name":"LG UltraWide 34","quantity":45,"price":499.99}',
    '{"sku":"MONITOR-003","name":"Dell UltraSharp 32","quantity":35,"price":599.99}',
    '{"sku":"MOUSE-001","name":"Logitech MX Master 3S","quantity":200,"price":99.99}',
    '{"sku":"MOUSE-002","name":"Razer DeathAdder V3","quantity":150,"price":69.99}',
    '{"sku":"MOUSE-003","name":"Apple Magic Mouse","quantity":100,"price":79.99}',
    '{"sku":"TECLADO-001","name":"Keychron K8 Pro","quantity":120,"price":109.99}',
    '{"sku":"TECLADO-002","name":"Logitech MX Keys","quantity":90,"price":119.99}',
    '{"sku":"TECLADO-003","name":"Corsair K100 RGB","quantity":60,"price":229.99}',
    '{"sku":"AURICULAR-001","name":"Sony WH-1000XM5","quantity":75,"price":349.99}',
    '{"sku":"AURICULAR-002","name":"AirPods Pro 2","quantity":100,"price":249.99}',
    '{"sku":"AURICULAR-003","name":"Bose QuietComfort Ultra","quantity":55,"price":429.99}',
    '{"sku":"WEBCAM-001","name":"Logitech C920 HD","quantity":180,"price":79.99}',
    '{"sku":"WEBCAM-002","name":"Elgato Facecam Pro","quantity":40,"price":299.99}',
    '{"sku":"SSD-001","name":"Samsung 990 Pro 2TB","quantity":200,"price":189.99}',
    '{"sku":"SSD-002","name":"WD Black SN850X 1TB","quantity":150,"price":99.99}',
    '{"sku":"RAM-001","name":"Corsair Vengeance 32GB DDR5","quantity":120,"price":129.99}',
    '{"sku":"RAM-002","name":"Kingston Fury 16GB DDR5","quantity":180,"price":69.99}',
    '{"sku":"GPU-001","name":"NVIDIA RTX 4090","quantity":15,"price":1599.99}',
    '{"sku":"GPU-002","name":"NVIDIA RTX 4070","quantity":45,"price":599.99}',
    '{"sku":"GPU-003","name":"AMD RX 7900 XTX","quantity":30,"price":949.99}',
    '{"sku":"TABLET-001","name":"iPad Pro 12.9","quantity":60,"price":1099.99}',
    '{"sku":"TABLET-002","name":"Samsung Galaxy Tab S9","quantity":50,"price":849.99}',
    '{"sku":"IMPRESORA-001","name":"HP LaserJet Pro M404","quantity":40,"price":299.99}',
    '{"sku":"IMPRESORA-002","name":"Epson EcoTank L3250","quantity":65,"price":199.99}',
    '{"sku":"CABLE-001","name":"Cable HDMI 2.1 3m","quantity":500,"price":24.99}',
    '{"sku":"CABLE-002","name":"Cable USB-C 2m","quantity":600,"price":14.99}',
    '{"sku":"CABLE-003","name":"Cable Ethernet Cat6 5m","quantity":400,"price":12.99}',
    '{"sku":"DOCK-001","name":"CalDigit TS4 Thunderbolt","quantity":25,"price":399.99}',
    '{"sku":"DOCK-002","name":"Anker 675 USB-C Dock","quantity":50,"price":249.99}',
    '{"sku":"SILLA-001","name":"Herman Miller Aeron","quantity":20,"price":1395.00}',
    '{"sku":"SILLA-002","name":"Secretlab Titan Evo","quantity":35,"price":519.99}',
    '{"sku":"ESCRITORIO-001","name":"Flexispot E7 Standing Desk","quantity":30,"price":499.99}',
    '{"sku":"ESCRITORIO-002","name":"IKEA Bekant 160x80","quantity":45,"price":349.99}',
    '{"sku":"UPS-001","name":"APC Back-UPS 1500VA","quantity":55,"price":189.99}',
    '{"sku":"UPS-002","name":"CyberPower 1000VA","quantity":70,"price":129.99}',
    '{"sku":"SWITCH-001","name":"TP-Link 8 Port Gigabit","quantity":90,"price":29.99}',
    '{"sku":"ROUTER-001","name":"ASUS RT-AX86U Pro","quantity":40,"price":249.99}',
    '{"sku":"NAS-001","name":"Synology DS224+","quantity":20,"price":299.99}',
    '{"sku":"MICRO-001","name":"Blue Yeti X","quantity":60,"price":169.99}',
    '{"sku":"MICRO-002","name":"Shure SM7B","quantity":25,"price":399.99}',
    '{"sku":"CAMARA-001","name":"Sony A7 IV","quantity":15,"price":2499.99}',
    '{"sku":"CAMARA-002","name":"Canon EOS R6 II","quantity":20,"price":2299.99}',
    '{"sku":"PENDRIVE-001","name":"Samsung BAR Plus 256GB","quantity":300,"price":29.99}',
    '{"sku":"PENDRIVE-002","name":"SanDisk Ultra 128GB","quantity":400,"price":14.99}',
    '{"sku":"CARGADOR-001","name":"Anker 100W GaN Charger","quantity":150,"price":59.99}'
)

$count = 0
foreach ($p in $products) {
    try {
        $null = Invoke-WebRequest -Uri $url -Method POST -Body $p -ContentType "application/json" -UseBasicParsing
        $count++
    } catch {
        Write-Host "Error en producto $count"
    }
}
Write-Host "Total productos creados: $count"
