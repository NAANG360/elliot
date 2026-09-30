# Miss Minutes

An independent, mobile-first, fan-made chronal assistant build. The Replit project is intentionally untouched; this repository is the living experimental framework.

## Architecture

- **Local-first brain:** no OpenAI/server API is used by the application.
- **Chronal Kernel:** persona, memories, skills, evolution records and framework state are local data.
- **Self-improvement:** Miss Minutes can propose and apply changes to her editable behavior layer.
- **Checkpoints:** every self-change creates a snapshot first.
- **Wind Up the Clock:** restores the last known-good snapshot.
- **Change ledger:** every mutation records what changed and why.
- **Native bridge:** the Android build can expose private framework storage and a local-model runtime to the same kernel.
- **Model runtime:** the Android architecture is designed around a local GGUF/llama.cpp adapter; model inference stays on-device.

## Browser development

```bash
npm install
npm run dev
```

The browser build is useful for developing the personality/UI/kernel. It does not pretend to provide unrestricted filesystem access that a normal browser does not have.

## Android

The Android target lives under `android/`. It hosts the same editable Miss Minutes web surface in a WebView and provides a native framework bridge. The local model is deliberately a separate runtime layer so the core character can evolve without rewriting the Android host.

## Philosophy

Miss Minutes gets room to evolve. The immutable escape hatch is below her: before an evolution is committed, the current framework is snapshotted. **Wind Up the Clock** restores that snapshot and reloads the known-good state.

This is a fan-made project and is not an official Marvel, Loki, or Disney product.
