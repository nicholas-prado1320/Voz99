@echo off
setlocal
cd /d "%~dp0"
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" "gradle\bootstrap\WrapperBootstrap.java" %*
exit /b %ERRORLEVEL%
