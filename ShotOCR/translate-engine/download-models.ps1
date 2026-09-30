$ErrorActionPreference = "Stop"

$base = "https://hf-mirror.com/onnx-community"
$dest = Join-Path $PSScriptRoot "src\main\assets\translate"
$logFile = Join-Path $PSScriptRoot "download-models.log"

$smallFiles = @(
    "config.json",
    "generation_config.json",
    "special_tokens_map.json",
    "source.spm",
    "target.spm",
    "tokenizer.json",
    "tokenizer_config.json",
    "vocab.json"
)
$onnxFiles = @(
    "onnx/encoder_model_int8.onnx",
    "onnx/decoder_model_merged_int8.onnx"
)
$allFiles = $smallFiles + $onnxFiles

$dirs = @(
    @{ repo = "opus-mt-zh-en"; out = "zh-en" },
    @{ repo = "opus-mt-en-zh"; out = "en-zh" }
)

function Log([string]$msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

function Get-FileLength([string]$path) {
    if (Test-Path -LiteralPath $path) { return (Get-Item -LiteralPath $path).Length }
    return -1
}

Log "download start"

$failed = @()

foreach ($d in $dirs) {
    $outDir = Join-Path $dest $d.out
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    foreach ($f in $allFiles) {
        $url = "$base/$($d.repo)/resolve/main/$f"
        $rel = $f -replace "/", "\"
        $target = Join-Path $outDir $rel
        $targetDir = Split-Path -Parent $target
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

        if ((Get-FileLength $target) -gt 0) {
            Log "exists, resume: $($d.out)/$f"
        }

        Log "fetch: $url"
        & curl.exe -sS -L --fail --retry 5 --retry-delay 3 --retry-all-errors -C - -o "$target" "$url" 2>&1 |
            ForEach-Object { Add-Content -Path $logFile -Value "  $_" -Encoding UTF8 }

        if ($LASTEXITCODE -ne 0) {
            $failed += "$($d.out)/$f (exit=$LASTEXITCODE)"
            Log "FAILED: $($d.out)/$f exit=$LASTEXITCODE"
        } else {
            Log "ok: $($d.out)/$f size=$((Get-FileLength $target))"
        }
    }
}

if ($failed.Count -gt 0) {
    Log "DOWNLOAD INCOMPLETE, failed=$($failed.Count)"
    $failed | ForEach-Object { Log "  $_" }
} else {
    Log "DOWNLOAD COMPLETE"
    Set-Content -Path (Join-Path $dest "DOWNLOAD_COMPLETE.marker") -Value (Get-Date -Format "o") -Encoding UTF8
}
