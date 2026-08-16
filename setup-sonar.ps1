$ErrorActionPreference = "Stop"
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin123"))
$h = @{ Authorization = "Basic $cred" }

Write-Host "Otorgando permiso de Execute Analysis al usuario admin..."
Invoke-WebRequest -Uri "http://localhost:9000/api/permissions/add_user?login=admin&permission=scan" -Method Post -Headers $h -UseBasicParsing

Write-Host "Generando token..."
$r = Invoke-WebRequest -Uri "http://localhost:9000/api/user_tokens/generate?name=jenkins2&type=GLOBAL_ANALYSIS_TOKEN" -Method Post -Headers $h -UseBasicParsing
$json = $r.Content | ConvertFrom-Json
Write-Host "TOKEN: $($json.token)"
