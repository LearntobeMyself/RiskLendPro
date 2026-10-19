@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-all.ps1"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to stop services.
    pause
    exit /b 1
)

echo.
echo RiskLendPro services stopped.
pause
endlocal