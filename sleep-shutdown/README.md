# Выключение ПК, когда уснул (Mi Band 10)

## ПК (Windows)
1. Скопируй папку на ПК, например в `C:\sleep-shutdown`.
2. PowerShell **от администратора**: `powershell -ExecutionPolicy Bypass -File C:\sleep-shutdown\install.ps1`
3. Открой `token.txt` — это секрет. Узнай IP ПК (скрипт печатает его при запуске, или `ipconfig`), лучше закрепи его в роутере.
4. Проверка с телефона (в одной Wi-Fi): `http://<IP>:8765/shutdown?token=<ТОКЕН>` → ПК покажет предупреждение и выключится через 120 с. Отмена: `shutdown /a`.

Сеть Wi-Fi должна быть «Частной» (Private), иначе брандмауэр не пустит.

## Телефон (Android)
Запрос — HTTP GET на URL выше (MacroDroid: действие «HTTP-запрос», Tasker: «HTTP Request»).
Триггер — условие «уснул». См. варианты в чате.
