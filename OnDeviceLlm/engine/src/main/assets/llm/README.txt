Place the local LLM model here.

Required file (name must match exactly):
  qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm

Source (int4, non-thinking, ~347MB):
  https://huggingface.co/litert-community/Qwen3-0.6B-int4
  direct: https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm

Easiest way (from repo root):
  download-models.bat OnDeviceLlm
  (reads the same-name .url file in this folder;
   run "set HF_MIRROR=https://hf-mirror.com" first if huggingface.co is unreachable)

Manual steps:
  1. Download the .litertlm file (no-think variant for fast, direct answers).
  2. Copy it to: OnDeviceLlm/engine/src/main/assets/llm/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm
  3. Rebuild the app. On first launch the file is copied to filesDir for LiteRT to load.

Notes:
  - Do NOT commit this file to git (root .gitignore: OnDeviceLlm/engine/src/main/assets/llm/*.litertlm).
