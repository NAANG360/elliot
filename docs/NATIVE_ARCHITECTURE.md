# Native Architecture

## Goal

The Android app should behave like a native character application rather than a web page inside an Android shell.

## Memory model

The local neural model is intentionally not treated as an infinite-context database.

1. **Working memory** — current conversation and immediate sensor/task context.
2. **Episodic memory** — compressed events and sessions.
3. **Semantic memory** — durable facts, entities, relationships, preferences and project state.
4. **Archive** — older low-priority events retained outside the active prompt.
5. **Retrieval** — rank only the relevant material for each generation.
6. **Consolidation** — merge repeated observations, detect changes, increase confidence and compress redundant history.

This means the usable memory horizon can grow far beyond the model's context window without pretending the model has an infinite KV cache.

## Brain evolution

The installed GGUF is the base brain. Autonomous improvement may produce datasets, adapters, evaluations and reversible artifacts, but the app must not silently download a larger replacement model.

The promotion gate remains explicit and reversible.

## Perception

The native layer is the long-term home for camera, microphone and Android sensor input. Raw streams should not be dumped into the language-model context. Extracted signals should be summarized into short-lived perception state and durable memory only when useful.

## Guardian boundary

Editable behavior stays above the Guardian boundary. Guardian pause/stop/reset control must remain reachable independently of the mutable character framework.

## Death effect

Destruction is a renderer state, not an OS crash:

clock character -> visual corruption -> text/ASCII contamination -> terminal-style fatal screen -> fade.

No real Android crash, data deletion or system failure is required.
