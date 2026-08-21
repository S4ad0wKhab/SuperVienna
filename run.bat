@echo off
title Vienna Controller

echo ================================
echo Starting Vienna services...
echo ================================
echo.

if not exist "mvnw.cmd" (
    echo ERROR: mvnw.cmd was not found.
    echo Please run this file from the Vienna project root.
    echo.
    pause
    exit /b 1
)

echo Starting Event Bus...
echo.

REM ==================================================
REM IMPORTANT:
REM Replace the Main class below with the actual
REM Event Bus server Main class from eventbus-server.
REM ==================================================

start "Vienna Event Bus" cmd /k "mvnw.cmd -pl eventbus-server exec:java -Dexec.mainClass=YOUR.EVENTBUS.MAIN.CLASS"

echo Event Bus window started.
echo.
echo Waiting for Event Bus to initialize...
timeout /t 3 /nobreak >nul

echo.
echo Starting Vienna API Server...
echo.

start "Vienna API Server" cmd /k "mvnw.cmd -pl apiserver exec:java -Dexec.mainClass=micheal65536.vienna.apiserver.Main"

echo.
echo ================================
echo Vienna services started.
echo ================================
echo.
echo Typing "off" will stop the operation.
echo.

:chat
set /p "message=Chat: "

if /i "%message%"=="off" (
    echo.
    echo Stopping Vienna services...
    echo.

    taskkill /FI "WINDOWTITLE eq Vienna API Server" /T /F >nul 2>&1
    taskkill /FI "WINDOWTITLE eq Vienna Event Bus" /T /F >nul 2>&1

    echo.
    echo Vienna services stopped.
    echo.
    pause
    exit /b
)

echo You typed: %message%
goto chat
