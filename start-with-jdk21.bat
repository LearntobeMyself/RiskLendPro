@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JAVA_HOME=D:\Java"
set "RISK_SERVICE_PORT=8082"
set "RISK_SERVICE_URL=http://localhost:8082"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found at:
    echo %JAVA_HOME%
    echo Edit this file and set JAVA_HOME to your JDK 21 directory.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ============================================
echo  RiskLendPro one-click startup
echo ============================================
echo Using JDK:
"%JAVA_HOME%\bin\java.exe" -version
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1"
if errorlevel 1 (
    echo.
    echo [ERROR] Startup failed. Check the output above and logs\*.log
    pause
    exit /b 1
)

start "" "%~dp0demo\recommendation-demo-v2.html"
echo.
echo All services started successfully.
echo Gateway: http://localhost:8088/api/v1
echo Demo page opened in your browser.
echo Run stop.bat to stop all services.
pause
endlocal

