@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo  RiskLendPro startup
echo ============================================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1"
if errorlevel 1 (
    echo.
    echo [ERROR] Startup failed. Check the output above and the logs directory.
    pause
    exit /b 1
)

echo.
echo Startup completed. Closing this window does not stop the services.
echo Run stop.bat to stop all services.
pause
endlocal