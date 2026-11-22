@echo off
title DunkMC - BW-1 (Teste)
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jre-8.0.482.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"
echo Iniciando BW-1...
"%JAVA_HOME%\bin\java" -Xms128M -Xmx1024M -javaagent:modifier.jar -jar server.jar
pause
