@echo off
title Fountain Connector Plugin Base Build

echo ================================
echo Building Fountain Connector Plugin Base...
echo ================================
echo.

cd /d "%~dp0buildplate\Fountain-connector-plugin-base"

if not exist "pom.xml" (
    echo.
    echo ================================
    echo ERROR: pom.xml NOT FOUND!
    echo ================================
    echo.
    echo Make sure Fountain-connector-plugin-base
    echo is inside the buildplate folder.
    echo.
    pause
    exit /b 1
)

echo Running Maven...
echo.

call mvnw.cmd clean install -DskipTests

if errorlevel 1 (
    echo.
    echo ================================
    echo BUILD FAILED!
    echo ================================
    echo.
    echo Fountain connector plugin base
    echo was NOT installed.
    echo.
    pause
    exit /b 1
)

echo.
echo ================================
echo BUILD SUCCESS!
echo ================================
echo.
echo connector-plugin-base has been
echo installed to the local Maven repository.
echo.
pause
