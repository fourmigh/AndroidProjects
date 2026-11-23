param(
    [string]$SourceDir = "F:\Github\PythonProjects\llama\llama.cpp",
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Stop"

function Info([string]$msg) { Write-Host "[llama] $msg" }

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ([string]::IsNullOrWhiteSpace($TargetDir)) {
    $TargetDir = Join-Path $scriptDir "..\third_party\llama.cpp"
}

$target = [System.IO.Path]::GetFullPath($TargetDir)
$cmakeFile = Join-Path $target "CMakeLists.txt"
if (Test-Path $cmakeFile) {
    Info "already present: $target"
    & (Join-Path $scriptDir "patch-llama.ps1") -LlamaDir $target
    exit 0
}

New-Item -ItemType Directory -Force -Path $target | Out-Null

# Only copy what is needed to build llama + ggml.
# Examples / tools / tests / .git are skipped to keep the tree small.
$parts = @("CMakeLists.txt", "LICENSE", "cmake", "ggml", "src", "include", "licenses", "vendor")

$sourceCmake = Join-Path $SourceDir "CMakeLists.txt"
if (Test-Path $sourceCmake) {
    Info "copying from local source: $SourceDir"
    foreach ($part in $parts) {
        $src = Join-Path $SourceDir $part
        if (Test-Path $src) {
            $dst = Join-Path $target $part
            Info ("  " + $part)
            Copy-Item -LiteralPath $src -Destination $dst -Recurse -Force
        }
    }
    Info "done: $target"
    & (Join-Path $scriptDir "patch-llama.ps1") -LlamaDir $target
    exit 0
}

Info "local source not found, trying git clone (requires network)"
git clone --depth 1 https://github.com/ggml-org/llama.cpp $target
if ($LASTEXITCODE -ne 0) {
    Write-Host "[llama] ERROR: cannot fetch llama.cpp. Pass -SourceDir or clone manually to $target"
    exit 1
}
Info "done: $target"
