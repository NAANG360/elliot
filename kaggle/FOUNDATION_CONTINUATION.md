# Elliot byte-foundation continuation on Kaggle

This is the continuation path for the existing **ElliotMini byte-level checkpoint** (for example, `elliot-136400.pt`). It is intentionally separate from `elliot_gpu_train.py`, which fine-tunes Qwen2.5-0.5B-Instruct with QLoRA. Those model families and weights are not interchangeable.

## What the trainer does

- Reads the architecture directly from the checkpoint config and loads its weights strictly.
- Requires and restores the original AdamW optimizer state; it refuses to silently restart the optimizer.
- Keeps the original 95/5 byte-level corpus split.
- Runs actual GPU training immediately after measuring an in-run validation baseline.
- Uses CUDA mixed precision (BF16 when supported, otherwise FP16), TF32, gradient clipping and GPU-resident random sequence sampling.
- Starts a lower-LR continuation phase (default peak LR `1e-4`, cosine-decaying to `2e-5`); AdamW moments are preserved.
- Evaluates on fixed validation windows so scores are comparable between evaluations.
- Writes `latest.pt` for recovery and `best.pt` / `model.pt` for the best validation checkpoint.
- Saves periodically and on Ctrl-C / notebook interruption where Python can catch it.
- Stops after eight consecutive non-improving evaluations by default.

The default model architecture is **not hardcoded**. The checkpoint config is authoritative, which prevents accidentally instantiating the newer 384/6/6/512 defaults against the older 192/4/4/256 checkpoint.

## Kaggle setup

1. Attach the existing checkpoint dataset `naang360/elliot-foundation-checkpoint`.
2. Enable a GPU accelerator and Internet access.
3. Make the existing FineWeb-Edu corpus file available in the notebook. The previously used working path was `/kaggle/working/elliot/ai/dataset/fineweb_edu.txt`; verify that it exists in the current session rather than downloading a second copy.
4. This repository is private. Download `elliot_foundation_gpu_train.py` from the branch while signed in to GitHub, then upload it to the Kaggle notebook or attach it through a private Kaggle dataset. Do not use an unauthenticated public clone. The file is at [kaggle/elliot_foundation_gpu_train.py](https://github.com/NAANG360/elliot/blob/feature/elliot-foundation-gpu-resume/kaggle/elliot_foundation_gpu_train.py).
5. Put the script at `/kaggle/working/elliot_foundation_gpu_train.py` (or change the command path below). The script imports the existing model class from the repository, so also make `ai/train/train.py` available at `/kaggle/working/elliot/ai/train/train.py`; alternatively upload that file and preserve the same folder layout.
6. Start the overnight training run (the paths below match the previously used Kaggle dataset layout):

```bash
python /kaggle/working/elliot_foundation_gpu_train.py \
  --checkpoint /kaggle/input/datasets/naang360/elliot-foundation-checkpoint/model/model.pt \
  --data /kaggle/working/elliot/ai/dataset/fineweb_edu.txt \
  --out /kaggle/working/elliot-foundation-run \
  --minutes 600 \
  --max-new-steps 50000 \
  --batch-size 32
```

The run is training, not a standalone evaluation job. The initial fixed-window validation pass is part of the training session and establishes the baseline used to select the best checkpoint.

If the corpus is mounted somewhere else, change only `--data` to its actual file path. Do not regenerate or append to the corpus during a resumed run: the corpus SHA-256 is recorded and the previous best checkpoint is reused only when its validation signature matches.

## Resuming a later Kaggle session

Download and attach both `latest.pt` and `best.pt` from the previous run as a Kaggle dataset. Resume from the latest state while retaining the best model:

```bash
python /kaggle/working/elliot/kaggle/elliot_foundation_gpu_train.py \
  --checkpoint /kaggle/input/previous-elliot-run/latest.pt \
  --best-checkpoint /kaggle/input/previous-elliot-run/best.pt \
  --data /kaggle/working/elliot/ai/dataset/fineweb_edu.txt \
  --out /kaggle/working/elliot-foundation-run \
  --minutes 600 \
  --max-new-steps 50000 \
  --batch-size 32
```

Keep the same corpus and validation arguments when comparing best checkpoints. Keep the downloaded outputs outside Git history; model weights should be stored as Kaggle datasets or release artifacts, not committed to the repository.

## Important scope

This is continued **foundation language-model pretraining** on FineWeb-Edu. It does not, by itself, make the byte model an instruction-tuned personal assistant. The later Elliot phase still needs a curated conversation / coding / tool-use curriculum and separate supervised fine-tuning. Elliot's persistent memory, idle research, capability development, permission broker and rollback system remain external framework components; they are not encoded by simply extending the foundation training run.
