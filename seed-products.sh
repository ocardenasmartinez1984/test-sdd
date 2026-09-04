#!/usr/bin/env bash
# Genera un catálogo grande de productos en el stock-service via API Gateway.
# Los SKU usan prefijos de categoría que el POS frontend reconoce para los iconos.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1/stock}"

# categoria|Nombre base|precio_min|precio_max  (5 modelos por categoría)
CATEGORIES=(
  "laptop|Laptop|650|2200"
  "monitor|Monitor|180|900"
  "mouse|Mouse|15|120"
  "teclado|Teclado|25|220"
  "auricular|Auriculares|30|400"
  "webcam|Webcam|40|250"
  "ssd|SSD|45|380"
  "ram|Memoria RAM|35|300"
  "gpu|Tarjeta Grafica|320|2400"
  "tablet|Tablet|180|1300"
  "impresora|Impresora|90|650"
  "cable|Cable|5|60"
  "dock|Docking Station|60|350"
  "silla|Silla Gamer|120|780"
  "escritorio|Escritorio|150|900"
  "ups|UPS|70|500"
  "switch|Switch de Red|40|420"
  "router|Router|35|380"
  "nas|NAS|250|1600"
  "micro|Microfono|40|450"
  "camara|Camara|180|1900"
  "pendrive|Pendrive|8|90"
  "cargador|Cargador|18|130"
)

BRANDS=("Pro" "Max" "Ultra" "Lite" "Elite" "Plus" "Air" "Prime")

total=0
for entry in "${CATEGORIES[@]}"; do
  IFS='|' read -r cat name pmin pmax <<< "$entry"
  for i in $(seq 1 5); do
    idx=$(printf "%03d" "$i")
    brand="${BRANDS[$((RANDOM % ${#BRANDS[@]}))]}"
    sku="${cat}-${idx}"
    pname="${name} ${brand} ${i}"
    price=$(LC_ALL=C awk -v a="$pmin" -v b="$pmax" 'BEGIN{srand(); printf "%.2f", a + rand()*(b-a)}')
    qty=$((RANDOM % 40 + 5))
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL" \
      -H 'Content-Type: application/json' \
      -d "{\"sku\":\"${sku}\",\"name\":\"${pname}\",\"quantity\":${qty},\"reservedQuantity\":0,\"price\":${price}}")
    if [ "$code" = "200" ]; then
      total=$((total + 1))
    else
      echo "FALLO ($code): $sku"
    fi
  done
  echo "categoria '${cat}' lista"
done

echo "== Total creados: ${total} =="
