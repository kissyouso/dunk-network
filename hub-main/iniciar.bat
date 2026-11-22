@echo off
title DunkMC - Hub (Teste)
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jre-8.0.482.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"
echo Iniciando Hub...
"%JAVA_HOME%\bin\java" -Xms128M -Xmx1024M -jar server.jar
pause
