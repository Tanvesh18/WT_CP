@echo off
setlocal
cd /d "%~dp0"
if not exist "node_modules\vite" (
  call npm.cmd ci
  if errorlevel 1 exit /b 1
)
set "TRIPSHIELD_API_TARGET=http://127.0.0.1:18084"
call npm.cmd run dev -- --host 127.0.0.1 --port 15174 --strictPort
exit /b %errorlevel%
