@echo off
title Vienna Controller

echo ================================
echo Starting Vienna Services...
echo ================================
echo.

REM ========================================
REM Start Object Store Server
REM ========================================

echo Starting Object Store Server...
echo.

start "Vienna Object Store" cmd /k "mvnw.cmd -pl objectstore/server exec:java -Dexec.mainClass=micheal65536.vienna.objectstore.server.Main -- --dataDir data"

echo Object Store process started.
echo.
echo Waiting for Object Store on port 5396...
echo.

:wait_objectstore
powershell -NoProfile -Command "$tcp = New-Object System.Net.Sockets.TcpClient; try { $tcp.Connect('127.0.0.1',5396); $tcp.Close(); exit 0 } catch { exit 1 }" >nul 2>&1

if errorlevel 1 (
    echo Object Store is not ready yet...
    timeout /t 1 /nobreak >nul
    goto wait_objectstore
)

echo.
echo ================================
echo Object Store is READY!
echo ================================
echo.

REM ========================================
REM Start Event Bus Server
REM ========================================

echo Starting Event Bus Server...
echo.

start "Vienna Event Bus" cmd /k "mvnw.cmd -pl eventbus/server exec:java -Dexec.mainClass=micheal65536.vienna.eventbus.server.Main"

echo Event Bus process started.
echo.
echo Waiting for Event Bus on port 5532...
echo.

:wait_eventbus
powershell -NoProfile -Command "$tcp = New-Object System.Net.Sockets.TcpClient; try { $tcp.Connect('127.0.0.1',5532); $tcp.Close(); exit 0 } catch { exit 1 }" >nul 2>&1

if errorlevel 1 (
    echo Event Bus is not ready yet...
    timeout /t 1 /nobreak >nul
    goto wait_eventbus
)

echo.
echo ================================
echo Event Bus is READY!
echo ================================
echo.

REM ========================================
REM Start API Server
REM ========================================

echo Starting Vienna API Server...
echo.

start "Vienna API Server" cmd /k "mvnw.cmd -pl apiserver exec:java -Dexec.mainClass=micheal65536.vienna.apiserver.Main"

echo.
echo ================================
echo Vienna Services Started
echo ================================
echo.
echo Object Store: 127.0.0.1:5396
echo Event Bus   : 127.0.0.1:5532
echo API Server  : Starting...
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
    taskkill /FI "WINDOWTITLE eq Vienna Object Store" /T /F >nul 2>&1

    echo.
    echo Vienna Services stopped.
    echo.
    pause
    exit /b
)

echo You typed: %message%
goto chat
