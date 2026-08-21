@echo off
title Vienna Controller

echo ================================
echo Starting Vienna API Server...
echo ================================
echo.

start "Vienna API Server" cmd /k "mvnw.cmd -pl apiserver exec:java -Dexec.mainClass=micheal65536.vienna.apiserver.Main"

echo.
echo ================================
echo Vienna API Server
echo ================================
echo.
echo.
echo.
echo Typing "off" will stop the operation.
echo.

:chat
set /p "message=Chat: "

if /i "%message%"=="off" (
    echo.
    echo Stopping operation...
    taskkill /FI "WINDOWTITLE eq Vienna API Server" /T /F >nul 2>&1
    echo Operation stopped.
    pause
    exit /b
)

echo You typed: %message%
goto chat
