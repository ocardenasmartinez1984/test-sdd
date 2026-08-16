$pair = "admin:admin"
$bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
$base64 = [System.Convert]::ToBase64String($bytes)
$headers = @{ Authorization = "Basic $base64" }

try {
    $response = Invoke-WebRequest -Uri "http://localhost:9000/api/user_tokens/generate?name=jenkins-ci-token" -Method Post -Headers $headers -UseBasicParsing
    Write-Output "Status: $($response.StatusCode)"
    Write-Output "Body: $($response.Content)"
} catch {
    Write-Output "Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Output "Response: $body"
    }
}
