@echo off

:MONITOR
timeout /nobreak /t 1 >nul
tasklist /FI "WINDOWTITLE eq start.bat" | find /i "cmd.exe" >nul

if %errorlevel% equ 0 (
    echo Server is running.
) else (
    echo Server is not running. Restarting...
     java -jar build\libs\flashtanki-server-1.0.0-all.jar
)

goto MONITOR
