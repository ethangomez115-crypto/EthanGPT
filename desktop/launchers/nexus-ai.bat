@echo off
REM Nexus AI desktop launcher for Windows.
REM Requires Java 11 or newer. Put nexus-ai.jar next to this .bat file.
set DIR=%~dp0
java -jar "%DIR%nexus-ai.jar" %*
if errorlevel 1 (
  echo.
  echo Nexus AI could not start. Make sure Java 11+ is installed.
  pause
)
