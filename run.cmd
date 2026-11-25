@echo off
rem Compiles and starts the console app.
cd /d "%~dp0"
rem Works even in a terminal opened before Maven was added to PATH.
where mvn >nul 2>&1 || set "PATH=%PATH%;%USERPROFILE%\tools\apache-maven-3.9.9\bin"
call mvn -q compile 2>nul || exit /b 1
java -cp "target\classes;%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar" com.marketplace.ui.MainApp
