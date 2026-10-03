@echo off
cd /d "%~dp0"
java -jar cat-client-2-launcher.jar
if errorlevel 1 pause
