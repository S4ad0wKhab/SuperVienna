@echo off
title Vienna Controller

echo ================================
echo Starting Vienna Services...
echo ================================
echo.

echo Starting Event Bus Server...
echo.

start "Vienna Event Bus" cmd /k "mvnw.cmd -pl eventbus/server exec:java -Dexec.mainClass=micheal65536.vienna.eventbus.server.Main"

echo Event Bus Server started.
echo.
echo Waiting for Event Bus...
timeout /t 3 /nobreak >nul

echo.
echo Starting Vienna API Server...
echo.

start "Vienna API Server" cmd /k "mvnw.cmd -pl apiserver exec:java -Dexec.mainClass=micheal65536.vienna.apiserver.Main"

echo.
echo ================================
echo Vienna Services Started
echo ================================
echo.
echo Typing "off" will stop the operation.
echo.

:chat
set /p "message=Chat: "

if /i "%message%"=="off" (
    echo.
    echo Stopping Vienna Services...
    echo.

    taskkill /FI "WINDOWTITLE eq Vienna API Server" /T /F >nul 2>&1
    taskkill /FI "WINDOWTITLE eq Vienna Event Bus" /T /F >nul 2>&1

    echo.
    echo Vienna Services stopped.
    echo.
    pause
    exit /b
)

echo You typed: %message%
goto chat
