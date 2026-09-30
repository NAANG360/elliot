# Miss Minutes visual + local-brain invariants

## Visual reference invariant
Use the recurring Miss Minutes design language seen across Loki reference stills: warm orange clock body, oversized expressive white eyes with dark pupils/lashes, central nose dot, sparse dark clock ticks, thin arms, white gloves, narrow dark legs, orange boots, and warm retro/filmic lighting. The project should treat online reference stills as visual references, not as packaged copyrighted image assets.

Reference examples:
- GamesRadar still: https://www.gamesradar.com/loki-villain-kang-sylvie-immortus-miss-minutes/
- Serienjunkies still: https://www.serienjunkies.de/news/serien/loki-miss-minutes-trailer-mit-neuen-szenen-aus-der-marvel-serie-92609045.html
- The Brag still: https://thebrag.com/tara-strong-voice-of-miss-minutes-loki/

## Local AI invariant
Inference is local-first. The UI can select a GGUF model, and the Android bridge reserves the model boundary for a native llama.cpp backend. The model is kept separate from the APK so the application package remains installable on ordinary Android storage.

The framework, memories, checkpoints, and evolution ledger remain local. Self-modification must checkpoint first and remain reversible through Wind Up the Clock.


## User-provided visual references (2026-09-30)

The newly supplied reference frames reinforce these non-negotiable visual invariants:
- Miss Minutes is an animated anthropomorphic clock mascot, not a human-shaped or human-like character.
- The face is a broad warm-orange circular clock face with visible tick marks drawn directly on the face.
- The eyes are tall, expressive cream/white ovals with dark pupils and small lash groups; they are not human eyes.
- A tiny circular nose sits between the eyes.
- The smile is small and curved, with a simple cartoon read rather than a large realistic mouth.
- The arms are thin orange clock-mascot limbs ending in clearly white, five-fingered cartoon gloves.
- The legs are narrow dark stems ending in simple orange boots.
- The silhouette reads as a 2D/2.5D cartoon clock first; 3D depth should support the mascot rather than turn the face into a realistic human-like object.
- Do not place a large horizontal torus/rim across the front of the face. Outer depth/rims belong around the perimeter.
