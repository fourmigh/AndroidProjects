@echo off
setlocal enabledelayedexpansion

set "ADB=E:\Android\Sdk\platform-tools\adb.exe"
set "SDK_DIR=E:\Android\Sdk"

if "%~1"=="" goto :usage
if "%~1"=="/?" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="--help" goto :usage

set "PROJECT=%~1"
set "BUILD_TYPE=debug"
set "DO_CLEAN=0"

shift
:parse_args
if "%~1"=="" goto :done_args
if /i "%~1"=="release" set "BUILD_TYPE=release"
if /i "%~1"=="clean" set "DO_CLEAN=1"
shift
goto :parse_args
:done_args

set "PROJECT_DIR=%~dp0%PROJECT%"
if not exist "%PROJECT_DIR%" (
    echo [ERROR] Project folder not found: %PROJECT%
    exit /b 1
)
if not exist "%PROJECT_DIR%\gradlew.bat" (
    echo [ERROR] gradlew.bat not found in %PROJECT%
    exit /b 1
)

echo [1/5] Checking device connection...
set "DEVICE_ID="
for /f "delims=" %%d in ('"%ADB%" get-serialno 2^>nul') do set "DEVICE_ID=%%d"
if "%DEVICE_ID%"=="" (
    echo [ERROR] No device connected. Please connect a device or start an emulator.
    echo         Run: adb devices to check.
    exit /b 1
)
echo        Device: %DEVICE_ID%

echo [2/5] Building %BUILD_TYPE% APK...
if "%DO_CLEAN%"=="1" (
    echo        Cleaning previous build...
    pushd "%PROJECT_DIR%"
    call gradlew.bat clean >nul 2>&1
    popd
)

pushd "%PROJECT_DIR%"
if "%BUILD_TYPE%"=="release" (
    call gradlew.bat assembleRelease 2>&1
) else (
    call gradlew.bat assembleDebug 2>&1
)
if errorlevel 1 (
    popd
    echo [ERROR] Build failed.
    exit /b 1
)
popd

echo [3/5] Finding APK...
if "%BUILD_TYPE%"=="release" (
    set "APK_DIR=%PROJECT_DIR%\app\build\outputs\apk\release"
) else (
    set "APK_DIR=%PROJECT_DIR%\app\build\outputs\apk\debug"
)

set "APK_PATH="
for %%f in ("%APK_DIR%\*.apk") do set "APK_PATH=%%f"

if "%APK_PATH%"=="" (
    echo [ERROR] APK not found in %APK_DIR%
    exit /b 1
)

for %%f in ("%APK_PATH%") do set "APK_SIZE=%%~zf"
set /a "APK_SIZE_MB=%APK_SIZE% / 1048576"
echo        APK: %APK_PATH%
echo        Size: %APK_SIZE_MB% MB

echo [4/5] Installing APK...
"%ADB%" install -r "%APK_PATH%" 2>&1
if errorlevel 1 (
    echo [ERROR] Install failed.
    exit /b 1
)

echo [5/5] Launching app...
set "PKG_NAME="
for /f "tokens=2 delims==" %%a in ('findstr /r "applicationId" "%PROJECT_DIR%\app\build.gradle.kts"') do (
    set "RAW=%%a"
    set "RAW=!RAW: =!"
    set "RAW=!RAW:"=!"
    set "PKG_NAME=!RAW!"
)
if "%PKG_NAME%"=="" (
    echo        [WARN] Could not read applicationId from build.gradle.kts, skipping launch.
    goto :done
)

"%ADB%" shell monkey -p "%PKG_NAME%" -c android.intent.category.LAUNCHER 1 >nul 2>&1
if errorlevel 1 (
    echo        [WARN] Failed to launch app automatically.
) else (
    echo        App launched: %PKG_NAME%
)

:done
echo.
echo ===== Done =====
echo Project:  %PROJECT%
echo Build:    %BUILD_TYPE%
echo APK:      %APK_PATH%
echo Device:   %DEVICE_ID%
echo.
exit /b 0

:usage
echo Usage: install ^<project-folder^> [release] [clean]
echo.
echo Parameters:
echo   project-folder   Project folder name (required)
echo   release          Build release APK (default: debug)
echo   clean            Clean before building
echo.
echo Examples:
echo   install ShotOCR
echo   install ShotOCR release
echo   install ShotOCR clean
echo   install ShotOCR release clean
echo.
exit /b 0
