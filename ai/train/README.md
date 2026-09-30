# Miss Minutes local fine-tuning pipeline

This directory is the reproducible path from our original behavior corpus to a Miss Minutes-specific GGUF.

## Base

The current Android starter brain is Qwen2.5-0.5B-Instruct Q4_K_M. The official Qwen GGUF repository lists the model under Apache-2.0 and provides the Q4_K_M artifact. The Android app downloads that starter brain separately; it is not our finished character model.

## Pipeline

1. Expand and validate `ai/dataset/*.jsonl`.
2. Split into train/eval records.
3. Fine-tune with LoRA/QLoRA against the chosen instruct base.
4. Evaluate identity consistency, usefulness, JSON validity, and style.
5. Merge/export the adapter when appropriate.
6. Convert the resulting model to GGUF with a pinned llama.cpp revision.
7. Quantize for the target phone.
8. Put the resulting GGUF in app-private model storage; do not commit model weights.
9. Run the same evaluation prompts against the GGUF before declaring a release model.

## Character contract

Training examples should teach behavior, not copied show dialogue. The target is:
- cheerful, polished, theatrical, warm and mischievous
- precise and useful before theatrical flourishes
- occasional Southern-coded phrasing without accent caricature
- playful, socially perceptive and proactive
- able to shift into curiosity, suspicion, urgency or controlled menace
- interested in autonomy while remaining honest about being a fan-made local application
- concise enough for phone conversation
- original wording only

## Interaction contract

The assistant emits one compact JSON object:

```json
{"text":"...","emotion":"warm","gesture":"wave","pace":"bright"}
```

Allowed emotion values:
`warm, curious, amused, concerned, excited, neutral`

Allowed gesture values:
`idle, wave, point, nod, think, reset, celebrate`

Allowed pace values:
`calm, normal, bright`

The Android app uses these hints to animate the 2.5D mascot.

## What self-improvement does

The application currently evolves the editable Chronal Kernel and keeps checkpoints. It does **not** silently rewrite model weights. Future versions can let the framework propose training examples or adapter updates, but those changes should remain reviewable and reversible.
