@echo off
setlocal enabledelayedexpansion

rem Download AI model files for a provider project.
rem Usage: download-models <project-folder>
rem Model URLs are stored in *.url files next to the expected model files.
rem Set HF_MIRROR (e.g. https://hf-mirror.com) to redirect huggingface.co URLs.

if "%~1"=="" goto :usage
if "%~1"=="/?" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="--help" goto :usage

set "PROJECT=%~1"
set "PROJECT_DIR=%~dp0%PROJECT%"
if not exist "%PROJECT_DIR%" (
    echo [ERROR] Project folder not found: %PROJECT%
    exit /b 1
)

if /i "%PROJECT%"=="OnDeviceTranslate" (
    echo [INFO] OnDeviceTranslate uses its own download script.
    echo        Running: engine\download-models.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\engine\download-models.ps1"
    if errorlevel 1 (
        echo [ERROR] Download failed. Check OnDeviceTranslate\engine\download-models.log
        exit /b 1
    )
    goto :done
)

set "MIRROR=%HF_MIRROR%"
set /a TOTAL=0
set /a SKIPPED=0
set /a FAILED=0

echo [INFO] Scanning for .url files under %PROJECT% ...
for /f "delims=" %%u in ('dir /s /b /a:-d "%PROJECT_DIR%\*.url" 2^>nul') do (
    set /a TOTAL+=1
    call :download "%%u"
)

if "%TOTAL%"=="0" (
    echo [WARN] No .url files found under %PROJECT%. Nothing to download.
    exit /b 1
)

echo.
if "!FAILED!"=="0" (
    echo ===== Done: !TOTAL! item^(s^), !SKIPPED! skipped, 0 failed =====
) else (
    echo ===== DONE WITH ERRORS: !FAILED! of !TOTAL! failed =====
)
if "!FAILED!" GTR 0 exit /b 1

:done
exit /b 0

:download
set "URL_FILE=%~1"
set "TARGET=%URL_FILE:~0,-4%"
set "URL="
set /p URL=<"%URL_FILE%"
if not defined URL (
    echo [WARN] Empty url file: %URL_FILE%
    set /a SKIPPED+=1
    exit /b 0
)
if defined MIRROR set "URL=!URL:https://huggingface.co=%MIRROR%!"
if exist "%TARGET%" (
    for %%f in ("%TARGET%") do set "FSIZE=%%~zf"
    if !FSIZE! GTR 0 (
        echo [SKIP] !TARGET! already exists
        set /a SKIPPED+=1
        exit /b 0
    )
)
echo [GET ] !TARGET!
echo        from !URL!
curl.exe -sS -L --fail --retry 3 --retry-delay 3 --retry-all-errors -C - -o "!TARGET!" "!URL!"
if errorlevel 1 (
    echo [ERROR] Download failed: !URL!
    set /a FAILED+=1
    exit /b 0
)
echo [OK  ] !TARGET!
exit /b 0

:usage
echo Usage: download-models ^<project-folder^>
echo.
echo Downloads AI model files for a provider project. Each model file has a
echo same-name .url file next to it, e.g. models\det\inference.onnx.url
echo holds the URL for models\det\inference.onnx
echo.
echo OnDeviceTranslate is forwarded to its own script: engine\download-models.ps1
echo.
echo Environment:
echo   HF_MIRROR   Optional mirror host for huggingface.co URLs,
echo               e.g. set HF_MIRROR=https://hf-mirror.com
echo.
echo Existing files are skipped. Interrupted downloads resume with curl -C -.
echo.
echo Examples:
echo   download-models OnDeviceOcr
echo   download-models OnDeviceLlm
echo   download-models OnDeviceTranslate
exit /b 0
