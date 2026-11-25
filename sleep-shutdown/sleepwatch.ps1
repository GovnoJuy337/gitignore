# Ночью, если долго нет ввода с клавиатуры/мыши, спрашивает "Ты не спишь?".
# Нет реакции за $ConfirmSeconds -> выключает ПК. Шевельнул мышью -> отмена.
# Тест:  powershell -ExecutionPolicy Bypass -File sleepwatch.ps1 -IdleMinutes 1 -NightStart 0 -NightEnd 24 -ConfirmSeconds 20 -DryRun
param(
  [int]$IdleMinutes = 40,     # сколько минут без ввода считаем подозрительным
  [int]$ConfirmSeconds = 90,  # сколько ждём реакции в окне
  [int]$NightStart = 0,       # ночное окно: с этого часа...
  [int]$NightEnd = 7,         # ...до этого (не включая). Можно 22..7
  [switch]$DryRun             # не выключать, только писать в консоль
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Idle {
  [StructLayout(LayoutKind.Sequential)]
  struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
  [DllImport("user32.dll")]
  static extern bool GetLastInputInfo(ref LASTINPUTINFO p);
  public static double Seconds() {
    LASTINPUTINFO l = new LASTINPUTINFO();
    l.cbSize = (uint)Marshal.SizeOf(l);
    GetLastInputInfo(ref l);
    return unchecked((uint)Environment.TickCount - l.dwTime) / 1000.0;
  }
}
"@

function Test-Night([int]$h) {
  if ($NightStart -le $NightEnd) { return ($h -ge $NightStart -and $h -lt $NightEnd) }
  return ($h -ge $NightStart -or $h -lt $NightEnd)
}

function Confirm-Awake {
  $form = New-Object Windows.Forms.Form
  $form.FormBorderStyle = 'None'; $form.StartPosition = 'CenterScreen'; $form.TopMost = $true
  $form.ShowInTaskbar = $false; $form.Size = New-Object Drawing.Size(560,150)
  $form.BackColor = [Drawing.Color]::FromArgb(30,30,30)
  $lbl = New-Object Windows.Forms.Label
  $lbl.Dock = 'Fill'; $lbl.ForeColor = [Drawing.Color]::White; $lbl.TextAlign = 'MiddleCenter'
  $lbl.Font = New-Object Drawing.Font('Segoe UI', 16)
  $form.Controls.Add($lbl)
  $form.Show()
  [Media.SystemSounds]::Asterisk.Play()
  $deadline = (Get-Date).AddSeconds($ConfirmSeconds)
  $awake = $false
  while ((Get-Date) -lt $deadline) {
    if ([Idle]::Seconds() -lt 3) { $awake = $true; break }
    $left = [int]($deadline - (Get-Date)).TotalSeconds
    $lbl.Text = "Ты не спишь?`nПошевели мышью, иначе ПК выключится через $left с"
    [Windows.Forms.Application]::DoEvents()
    Start-Sleep -Milliseconds 400
  }
  $form.Close(); $form.Dispose()
  return $awake
}

Write-Host "SleepWatch: ночь $NightStart-$NightEnd ч, простой $IdleMinutes мин, ожидание $ConfirmSeconds с. DryRun=$DryRun"
while ($true) {
  Start-Sleep -Seconds 20
  if (-not (Test-Night (Get-Date).Hour)) { continue }
  if ([Idle]::Seconds() -lt $IdleMinutes * 60) { continue }
  if (Confirm-Awake) { Write-Host "$(Get-Date -f s) не спишь, продолжаю"; continue }
  Write-Host "$(Get-Date -f s) нет реакции -> выключение"
  if (-not $DryRun) { shutdown.exe /s /t 30 /c "Ты уснул - выключаюсь. Отмена: shutdown /a" }
  Start-Sleep -Seconds 120
}
