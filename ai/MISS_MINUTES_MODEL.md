# Miss Minutes AI — our own local model

The goal is not to ship a generic chatbot with a Miss Minutes prompt. The project will own a **Miss Minutes model layer**: a locally runnable language model fine-tuned for the character, wrapped by the Chronal Kernel for memory, tools, framework evolution, and rollback.

## What "our own model" means

We are not pretending that a phone can cheaply pretrain a frontier foundation model from zero. The practical path is:

1. Start from a permissively licensed small instruct base that runs well on Android.
2. Build our own Miss Minutes training corpus and behavior specification.
3. Fine-tune/LoRA the base into a Miss-Minutes-specific checkpoint.
4. Convert/quantize the resulting model to GGUF.
5. Ship the weights separately from the APK so the APK stays small.
6. Run inference locally through llama.cpp.
7. Put the Chronal Kernel around the model for memory, tools, self-improvement, checkpoints, and Wind Up the Clock.

The resulting assistant model is ours in the sense that its weights are specifically trained for this project; the base model's license and attribution remain respected.

## Dataset contract

Training examples should be JSONL records:

```json
{"messages":[{"role":"system","content":"...Miss Minutes behavior contract..."},{"role":"user","content":"..."},{"role":"assistant","content":"..."}]}
```

The corpus should emphasize:
- helpfulness before theatrics
- cheerful retro-TV-host delivery
- playful mischief without pretending to be supernatural
- concise explanations
- tool-use discipline
- consistent memory behavior
- graceful uncertainty
- no copied show dialogue
- no claims of being official Marvel/TVA software

## Visual/interaction contract

The model should emit optional interaction hints alongside text, for example:

```json
{"text":"Well hello there, sugar!","emotion":"warm","gesture":"wave","pace":"bright"}
```

The renderer can use those hints for eyes, pupils, mouth, arms, camera movement, and holographic effects.

## Training pipeline

The repository's next AI milestone is a reproducible LoRA/QLoRA training pipeline outside the Android build. Training artifacts should never be embedded into source control or the APK. The Android app consumes the final GGUF through the existing model picker and private storage.

## Safety boundary

Self-improvement may modify the editable behavior/framework layer, but every framework mutation is checkpointed first and reversible through **Wind Up the Clock**. Model weights themselves are not silently overwritten by the app.
