param(
    [string]$LlamaDir = ""
)

$ErrorActionPreference = "Stop"

function Info([string]$msg) { Write-Host "[patch] $msg" }

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ([string]::IsNullOrWhiteSpace($LlamaDir)) {
    $LlamaDir = Join-Path $scriptDir "..\third_party\llama.cpp"
}

$vocab = Join-Path $LlamaDir "src\llama-vocab.cpp"
if (-not (Test-Path $vocab)) {
    Info "llama-vocab.cpp not found: $vocab"
    exit 1
}

$text = [System.IO.File]::ReadAllText($vocab)

if ($text.Contains('tokenizer_model == "plamo3"')) {
    Info "already patched"
    exit 0
}

$anchor = '} else if (tokenizer_model == "gemma4") {'
$index = $text.IndexOf($anchor)
if ($index -lt 0) {
    Info "anchor not found; llama.cpp layout changed, patch manually"
    exit 1
}

$patchLines = @(
    '} else if (tokenizer_model == "plamo3") {',
    '            // PLaMo-3 marks its tokenizer as "plamo3" in GGUF, but the vocabulary layout',
    '            // matches PLaMo-2 (Aho-Corasick + scores, same special token ids).',
    '            type = LLAMA_VOCAB_TYPE_PLAMO2;',
    '',
    '            special_bos_id = 1;  // <|plamo:bos|>',
    '            special_eos_id = 2;  // <|plamo:eos|>',
    '            special_unk_id = 0;  // <|plamo:unk|>',
    '            special_sep_id = LLAMA_TOKEN_NULL;',
    '            special_pad_id = 3;  // <|plamo:pad|>',
    '            special_mask_id = LLAMA_TOKEN_NULL;'
)
$patch = ($patchLines -join "`r`n")

$patched = $text.Substring(0, $index) + $patch + "`r`n        " + $text.Substring($index)

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($vocab, $patched, $utf8)

Info "patched: $vocab"
