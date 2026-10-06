# Miss Minutes

An independent, mobile-first, fan-made chronal assistant build. The Replit project is intentionally untouched; this repository is the living experimental framework.

## Privacy / project boundary

**This repository and the Miss Minutes application are private personal projects.** The repository is private, the project is not intended for public distribution, and the author does not grant permission for other people to redistribute the APK, model files, private recordings, private datasets, checkpoints, memory stores, or other project artifacts.

That privacy statement describes the project's intended distribution boundary; it is **not** a blanket copyright licence and does not claim ownership of Marvel/Disney characters, performances, recordings, or other third-party material.

This project is intentionally a private fan implementation. It is not an official Marvel, Loki, TVA, or Disney product.

## 0.8.0 native direction

The Android application is now moving off WebView:

- **Native Android UI:** the APK renders Miss Minutes through a Kotlin View instead of a WebView.
- **Native chronal memory:** durable traces are stored locally through Android storage rather than browser localStorage.
- **Native local brain:** llama.cpp remains the local inference layer and the installed GGUF remains the single base brain.
- **No silent model ladder:** Miss Minutes does not autonomously download replacement models.
- **Swappable voice boundary:** speech is isolated behind VoiceBackend, so an authorized/licensed voice implementation can be plugged in without changing the brain.
- **Death renderer:** the native character renderer has the foundation for the requested glitch -> ASCII -> terminal-style failure presentation. It is an application effect, not a real operating-system crash.

The old React/Vite surface remains in the repository as a development/reference environment. **It is no longer the Android application's runtime UI.**

## Architecture

- **Local-first brain:** no OpenAI/server API is required by the application.
- **Chronal Kernel:** persona, memories, skills, evolution records and framework state are local data.
- **Self-improvement:** Miss Minutes can propose and apply changes to her editable behavior layer.
- **Checkpoints:** every self-change creates a snapshot first.
- **Wind Up the Clock:** restores the last known-good snapshot.
- **Change ledger:** every mutation records what changed and why.
- **Native Android core:** UI, storage, permissions, sensors, speech and local inference are moving into native Android/Kotlin/C++.
- **Effectively unbounded memory:** long-term memory is treated as an external persistent store with retrieval/compression, rather than pretending a tiny local model can hold an infinite token context at once.

## Voice

The voice system is deliberately a **backend interface**, not a hard-coded actor impersonation system.

The target character direction is Miss Minutes: bright, theatrical, mischievous, precise and warm, with a retro-cartoon cadence. The application can use Android/system TTS or another voice implementation whose model and voice rights permit the intended use.

The project does **not** ship a Tara Strong voice clone or claim authorization to reproduce her recognizable performance. If the project ever obtains explicit authorization/licensing for a particular performance or voice model, that authorized backend can be connected without redesigning the rest of the application.

India's Copyright Act gives performers specific rights over recording/reproduction/communication of performances under section 38A, while section 39 contains a private-use exception for certain sound/visual recordings. Those provisions do not amount to a blanket licence for every form of AI voice cloning or redistribution, so this README intentionally does not make that claim. See the official Copyright Office text before changing the project's distribution or voice policy.

## Browser development

npm install
npm run dev

The browser build is useful for developing the personality/kernel and prototyping UI. It is not the Android runtime.

## Android

Build the Android application from the android directory. The native application uses a local GGUF/llama.cpp runtime and native Kotlin UI/services.

## Philosophy

Miss Minutes gets room to evolve, while the user retains control of the immutable safety/Guardian boundary. Editable behavior, memory, capabilities and adaptation artifacts can evolve, but protected control boundaries are deliberately outside the editable layer.

**Private project. Private artifacts. No public distribution by default.**

See:
- DESIGN_REFERENCE.md — character and visual invariants
- docs/NATIVE_ARCHITECTURE.md — native/infinite-memory architecture
- docs/VOICE_ARCHITECTURE.md — swappable speech architecture and rights boundary
- docs/DEATH_SEQUENCE.md — original destruction/failure effect specification
