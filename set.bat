@echo off
title Vienna Build

echo ================================
echo Building Vienna project...
echo ================================
echo.

call mvnw.cmd -DskipTests clean install

if errorlevel 1 (
    echo.
    echo ================================
    echo BUILD FAILED!
    echo ================================
    echo.
    echo set.bat will NOT be deleted.
    pause
    exit /b 1
)

echo.
echo ================================
echo BUILD SUCCESS!
echo ================================
echo.
echo All Maven modules have been installed.
echo.

echo Deleting set.bat...
del "%~f0"
