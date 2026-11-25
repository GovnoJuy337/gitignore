# Слушает HTTP-запрос с телефона и выключает ПК. Без установки Python, без админ-прав на запуск.
# Запуск:  powershell -ExecutionPolicy Bypass -File listener.ps1
param(
  [int]$Port = 8765,
  [int]$GraceSeconds = 120   # время на отмену: shutdown /a
)

$tokenFile = Join-Path $PSScriptRoot 'token.txt'
if (-not (Test-Path $tokenFile)) {
  # Секрет генерируется один раз; вставь его в MacroDroid/Tasker на телефоне
  $bytes = New-Object byte[] 24
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]','') | Set-Content $tokenFile -NoNewline
}
$token = (Get-Content $tokenFile -Raw).Trim()

$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Any, $Port)
$listener.Start()
Write-Host "Слушаю порт $Port. Токен в $tokenFile"
Write-Host ("IP этого ПК: " + ((Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like '192.168.*' -or $_.IPAddress -like '10.*' }).IPAddress -join ', '))

while ($true) {
  $client = $listener.AcceptTcpClient()
  try {
    $client.ReceiveTimeout = 3000
    $reader = New-Object IO.StreamReader($client.GetStream())
    $line = $reader.ReadLine()          # GET /shutdown?token=XXX HTTP/1.1
    $reply = 'NO'
    if ($line -match '^GET /shutdown\?token=([A-Za-z0-9]+)') {
      if ($Matches[1] -eq $token) {
        $reply = 'OK'
        Write-Host "$(Get-Date -f s) выключение через $GraceSeconds с (отмена: shutdown /a)"
        shutdown.exe /s /t $GraceSeconds /c "Ты уснул - выключаюсь. Отмена: shutdown /a"
      } else { Write-Host "$(Get-Date -f s) неверный токен" }
    }
    $body = [Text.Encoding]::ASCII.GetBytes($reply)
    $hdr  = [Text.Encoding]::ASCII.GetBytes("HTTP/1.1 200 OK`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n")
    $s = $client.GetStream(); $s.Write($hdr,0,$hdr.Length); $s.Write($body,0,$body.Length)
  } catch { } finally { $client.Close() }
}
