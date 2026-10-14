Place the local LLM model here.

Required file (name must match exactly):
  qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm

Recommended model (int4, non-thinking, ~347MB):
  https://huggingface.co/litert-community/Qwen3-0.6B-int4
  direct: https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm

Steps:
  1. Download the .litertlm file (use the no-think variant for fast, direct answers).
  2. Copy it to: llm-parser/src/main/assets/llm/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm
  3. Rebuild the app. On first launch the file is copied to filesDir for LiteRT to load.

Notes:
  - Do NOT commit this file to git (see .gitignore: llm-parser/src/main/assets/llm/*.litertlm).
  - assets/*.litertlm is excluded from APK compression (see llm-parser/build.gradle.kts).
  - The build prints a warning if this file is missing.
