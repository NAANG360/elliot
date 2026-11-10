# Kaggle GPU training for Elliot

## Why this is a separate training path
The CPU foundation workflow trains a tiny UTF-8 byte-level Transformer from random initialization. Its weights are **not architecture-compatible** with the Android starter brain. The app's documented starter brain is **Qwen2.5-0.5B-Instruct Q4_K_M**. This Kaggle path therefore fine-tunes the same Qwen family using 4-bit QLoRA and saves a portable PEFT adapter. It does not overwrite or delete the older foundation checkpoints.

## Start
1. Open https://www.kaggle.com/code and create a notebook.
2. In notebook Settings, set Accelerator to GPU. Internet must be enabled to fetch packages and the base model.
3. Add this repository's `kaggle/elliot_gpu_train.py` to the notebook by cloning the public repo or copy the script into a cell/file.
4. Run the notebook cells in `kaggle/elliot_gpu_training.ipynb`.
5. The notebook checks CUDA and deliberately stops if no GPU is attached.

## Dataset gate
The current `ai/dataset/seed.jsonl` contains only a tiny seed set. The trainer refuses to run with fewer than 10 unique conversations. Passing that gate is only a technical smoke test, not evidence of a capable assistant. Add a larger, reviewed, deduplicated curriculum to `ai/dataset/*.jsonl` before serious training. Keep train/eval examples separate and never evaluate on training examples.

## Output
The run writes:
- `/kaggle/working/elliot-output/adapter/` — LoRA adapter and tokenizer
- `training-report.json` — dataset counts, GPU identity and eval loss
- `trainer-state/` — resumable Trainer checkpoints

Download the output from Kaggle's notebook output panel. Store it as a GitHub Release asset or external model storage; **do not commit model weights into Git history**. The old byte-model checkpoint remains preserved separately.

## Compatibility warning
This is a Qwen2.5-0.5B-Instruct LoRA adapter. It is not itself a GGUF and is not automatically wired into the Android app. A later export step must merge/convert/quantize it and run the app's JSON response evaluation before release.

## No secrets
This initial workflow needs no GitHub token and does not ask for a card. Never paste API tokens into notebook cells or chat.

## Existing byte-level Elliot checkpoint continuation

If the goal is to continue the custom `ElliotMini` checkpoint such as `elliot-136400.pt`, do **not** run `elliot_gpu_train.py`: that is the separate Qwen2.5-0.5B QLoRA experiment. Use [FOUNDATION_CONTINUATION.md](FOUNDATION_CONTINUATION.md) and `elliot_foundation_gpu_train.py` instead. That trainer reads the byte model's architecture from its checkpoint, preserves its AdamW state, and produces separate latest/best checkpoints for an actual continuation run.
