@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

rem ---------------------------------------------------------------------------
rem WalletLoginDemo pre-build hook (called by install.bat at the repo root)
rem
rem Arg %1 = build type (debug / release)
rem
rem Writes two keys into android\local.properties:
rem     backend.url=http://<local-ip>:<PORT>
rem     nfc.reader.token=<NFC_READER_TOKEN>
rem PORT and NFC_READER_TOKEN come from backend\.env (same source as
rem start-backend.bat). Defaults: PORT=3000, NFC_READER_TOKEN=dev-reader-token.
rem ---------------------------------------------------------------------------

set "BUILD_TYPE=%~1"
if "%BUILD_TYPE%"=="" set "BUILD_TYPE=debug"

rem This script lives in <android>\hooks\, so ".." is the Gradle root (android dir)
for %%i in ("%~dp0..") do set "ANDROID_DIR=%%~fi"

set "BACKEND_PORT=3000"
set "NFC_READER_TOKEN=dev-reader-token"
set "ENV_FILE=%~dp0..\..\backend\.env"
if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        if /i "%%a"=="PORT" set "BACKEND_PORT=%%b"
        if /i "%%a"=="NFC_READER_TOKEN" set "NFC_READER_TOKEN=%%b"
    )
)

set "LOCAL_PROPS=%ANDROID_DIR%\local.properties"
set "TMP_URL=%TEMP%\wld_prebuild_url.txt"

echo        Resolving backend URL for local.properties...

set "WLD_PORT=%BACKEND_PORT%"
set "WLD_TOKEN=%NFC_READER_TOKEN%"
set "WLD_LOCAL=%LOCAL_PROPS%"
set "WLD_TMP=%TMP_URL%"

powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $c=@(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } | ForEach-Object IPAddress); $ip=$c | Where-Object { $_ -match '^192\.168\.' } | Select-Object -First 1; if(-not $ip){$ip=$c | Select-Object -First 1}; if(-not $ip){$ip='127.0.0.1'}; $u='http://'+$ip+':'+$env:WLD_PORT; $f=$env:WLD_LOCAL; $l=@(); if(Test-Path -LiteralPath $f){$l=@(Get-Content -LiteralPath $f)}; $l=@($l | Where-Object { $_ -notmatch '^\s*(backend\.url|nfc\.reader\.token)\s*=' }); $l+='backend.url='+$u; $l+='nfc.reader.token='+$env:WLD_TOKEN; Set-Content -LiteralPath $f -Value $l -Encoding ASCII; Set-Content -LiteralPath $env:WLD_TMP -Value $u -Encoding ASCII"
if errorlevel 1 (
    echo [ERROR] failed to resolve backend URL for local.properties.
    exit /b 1
)

set "BACKEND_URL="
set /p "BACKEND_URL="<"%TMP_URL%" 2>nul
del "%TMP_URL%" >nul 2>&1
if "!BACKEND_URL!"=="" set "BACKEND_URL=http://127.0.0.1:%BACKEND_PORT%"

echo        backend.url=!BACKEND_URL!
echo        nfc.reader.token=%NFC_READER_TOKEN%
echo        written to %LOCAL_PROPS%

exit /b 0
