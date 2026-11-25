@echo off
rem Compiles and runs ManualTestRunner against the MySQL database in db.properties.
cd /d "%~dp0"
where mvn >nul 2>&1 || set "PATH=%PATH%;%USERPROFILE%\tools\apache-maven-3.9.9\bin"
call mvn -q test-compile 2>nul || exit /b 1
java -cp "target\test-classes;target\classes;%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar" com.marketplace.ManualTestRunner
