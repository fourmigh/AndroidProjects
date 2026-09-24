@echo off
setlocal enabledelayedexpansion

if "%~1"=="" goto :usage
if "%~1"=="/?" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="--help" goto :usage

set "PROJECT=%~1"
set "PROJECT_DIR=%~dp0%PROJECT%"
set "BACKEND_PORT="

shift
:parse_args
if "%~1"=="" goto :done_args
set "ARG=%~1"
if /i "!ARG:~0,5!"=="port:" set "BACKEND_PORT=!ARG:~5!"
shift
goto :parse_args
:done_args

if not exist "%PROJECT_DIR%" (
    echo [ERROR] Project folder not found: %PROJECT%
    exit /b 1
)

if not exist "%PROJECT_DIR%\package.json" (
    if exist "%PROJECT_DIR%\backend\package.json" (
        set "PROJECT_DIR=!PROJECT_DIR!\backend"
        echo        Detected backend subproject: %PROJECT%\backend
    ) else (
        echo [ERROR] package.json not found in %PROJECT%
        exit /b 1
    )
)

pushd "%PROJECT_DIR%"

where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Please install Node.js first.
    popd
    exit /b 1
)

set "NODE_VERSION=unknown"
for /f "delims=" %%v in ('node --version') do set "NODE_VERSION=%%v"

echo ============================================
echo  Project: %PROJECT%
echo  Dir:     %PROJECT_DIR%
echo  Node:    !NODE_VERSION!
echo ============================================
echo.

if not exist "node_modules" (
    echo [1/4] Installing dependencies...
    call npm install
    if errorlevel 1 (
        echo [ERROR] npm install failed.
        popd
        exit /b 1
    )
) else (
    echo [1/4] Dependencies already installed, skipping.
)

if not exist ".env" (
    echo        [WARN] .env not found. Copy .env.example to .env and set ISSUER_ID.
)
if not exist "service-account.json" (
    echo        [WARN] service-account.json not found. Required for Google Wallet API.
)

if "!BACKEND_PORT!"=="" (
    set "BACKEND_PORT=3000"
    if exist ".env" (
        for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
            if /i "%%a"=="PORT" set "BACKEND_PORT=%%b"
        )
    )
)

echo.
set "FW_RULE=%PROJECT% backend !BACKEND_PORT!"
echo [2/4] Checking firewall rule "%FW_RULE%"...
powershell -NoProfile -Command "if (Get-NetFirewallRule -DisplayName '%FW_RULE%' -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo        Rule exists, skipping.
    goto :firewall_ready
)

net session >nul 2>&1
if errorlevel 1 (
    echo        Rule missing. Requesting administrator privileges ^(UAC^)...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%*' -Verb RunAs"
    popd
    exit /b 0
)

echo        Adding rule for TCP !BACKEND_PORT!...
powershell -NoProfile -Command "Remove-NetFirewallRule -DisplayName 'node.exe' -ErrorAction SilentlyContinue; New-NetFirewallRule -DisplayName '%FW_RULE%' -Direction Inbound -Action Allow -Protocol TCP -LocalPort !BACKEND_PORT! | Out-Null"
if errorlevel 1 (
    echo        [WARN] Failed to add firewall rule. Phone may not reach the backend.
) else (
    echo        Rule added.
)
:firewall_ready

echo.
echo [3/4] Local IPv4 addresses ^(install.bat writes one into local.properties^):
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set "IP=%%a"
    echo        !IP: =!
)
echo.

echo [4/4] Starting backend... ^(press Ctrl+C to stop^)
echo.
set "PORT=!BACKEND_PORT!"
call npm start

popd
exit /b 0

:usage
echo Usage: start-backend ^<project-folder^> [port:^<n^>]
echo.
echo Parameters:
echo   project-folder   Project folder name (required).
echo                    If it has a "backend" subfolder, it is used automatically.
echo   port:^<n^>         Backend port (default: PORT in .env, else 3000).
echo.
echo Note: the script auto-allows this port in Windows Firewall
echo       ^(asks for administrator / UAC once if the rule is missing^).
echo.
echo Examples:
echo   start-backend WalletLoginDemo
echo   start-backend WalletLoginDemo port:8080
echo   start-backend WalletLoginDemo\backend
echo.
exit /b 0
