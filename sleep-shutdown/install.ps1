# Запусти ОДИН раз от администратора: правило брандмауэра + автозапуск слушателя при входе в систему.
$dir = $PSScriptRoot
New-NetFirewallRule -DisplayName 'SleepShutdown' -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow | Out-Null
$act = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$dir\listener.ps1`""
$trg = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName 'SleepShutdown' -Action $act -Trigger $trg -RunLevel Limited -Force | Out-Null
Start-ScheduledTask -TaskName 'SleepShutdown'
Write-Host 'Готово. Токен появится в token.txt рядом со скриптом.'
