@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo  RiskLendPro  one-click start
echo  MySQL and Redis are remote, not local
echo ============================================
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java。请先安装 JDK 21 并加入 PATH：
    echo https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

echo 当前 Java 版本：
java -version
echo.
if "%SERVER_PORT%"=="" set SERVER_PORT=8080

echo 正在启动后端（首次会自动下载 Maven，需要联网）...
echo 启动成功后访问: http://localhost:%SERVER_PORT%/api/v1
echo 管理端账号: admin / Admin123456
echo 用户端账号: 13800138001 / Test123456
echo.

call "%~dp0mvnw.cmd" -pl services/legacy-service spring-boot:run "-Dspring-boot.run.arguments=--server.port=%SERVER_PORT%"
if errorlevel 1 (
    echo.
    echo [错误] 启动失败。常见原因：未装 JDK 21、8080 被占用、或连不上云端 MySQL/Redis。
    pause
)
