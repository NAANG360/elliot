# Elliot training runs

This workflow is a **research bootstrap**, not a claim that Elliot is already a capable assistant.

## What it does
- Pulls a bounded sample from HuggingFaceFW/fineweb-edu (sample-10BT) for educational language-model pretraining.
- Trains a randomly initialized small byte-level Transformer; no pretrained model weights are used.
- Saves a checkpoint and attempts to resume it from GitHub Actions cache on later runs.
- Runs a strict tensor/config integrity check and uploads checkpoint + report.
- Sends the exact requested email phrase only after the workflow passes.

## Important limits
- GitHub-hosted runners are CPU-only in this workflow. CPU training is slow and is not a substitute for sustained GPU training.
- GitHub Actions jobs have a 6-hour maximum; private GitHub Free repositories also have a finite monthly minutes allowance. The scheduled run defaults to 55 minutes. Dispatch manually with a larger `minutes` input only while quota remains.
- Actions caches are best-effort, size-limited, and immutable by key. Each run restores the newest matching prefix, then saves its own key. Checkpoints are also uploaded as 30-day artifacts; inspect artifacts if cache restoration misses.
- The current byte tokenizer and small model are a pipeline proof, not a production assistant. It does not yet implement chat instruction tuning, code specialization, robust reasoning, retrieval grounding, or EGF export.
- The integrity gate only checks checkpoint shape and finite values. It does **not** mean the model is "fit for liking". A separate human-reviewed capability benchmark is required before a release can be called ready.

## Email setup (one-time)
Add repository Actions secrets:
- `SMTP_SERVER`
- `SMTP_PORT` (usually 587)
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `NOTIFY_EMAIL`

Until these are configured, training still runs but no email is sent. Do not commit credentials.

## Data governance
Review the Hugging Face dataset card and license, preserve source attribution, and do not treat arbitrary scraped text as trusted facts. Later phases need separate permissively licensed code, technical documentation, mathematics, and instruction/conversation corpora with deduplication, PII filtering, and held-out evaluation.
