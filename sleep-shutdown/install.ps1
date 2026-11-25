# Запусти один раз (админ не нужен): автозапуск SleepWatch при входе в Windows.
$dir = $PSScriptRoot
$act = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$dir\sleepwatch.ps1`""
$trg = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName 'SleepWatch' -Action $act -Trigger $trg -Force | Out-Null
Start-ScheduledTask -TaskName 'SleepWatch'
Write-Host 'SleepWatch установлен и запущен. Удалить: Unregister-ScheduledTask SleepWatch -Confirm:$false'
