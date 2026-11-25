@echo off
rem Deletes marketplace_db and recreates it with the sample data (schema.sql + seed.sql).
rem Use before a manual test so ids and stock match docs\MANUAL_TEST.md.
cd /d "%~dp0"
set MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set /p PW=MySQL root password: 
echo DROP DATABASE IF EXISTS marketplace_db; | %MYSQL% -u root -p%PW% 2>nul || (echo Could not connect to MySQL & exit /b 1)
%MYSQL% -u root -p%PW% < sql\schema.sql 2>nul
%MYSQL% -u root -p%PW% < sql\seed.sql 2>nul
echo Database reset: 2 vendors, 3 customers, 4 products.
