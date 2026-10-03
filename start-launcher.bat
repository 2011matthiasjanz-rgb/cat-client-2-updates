@echo off
cd /d "%~dp0"
call gradlew.bat :launcher:run --console=plain
if errorlevel 1 pause
