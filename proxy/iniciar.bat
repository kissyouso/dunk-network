@echo off
title DunkMC - Proxy (Teste)
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jre-8.0.482.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"
echo Iniciando Proxy (BungeeCord)...
"%JAVA_HOME%\bin\java" -Xms128M -Xmx512M -jar bungeecord.jar
pause
