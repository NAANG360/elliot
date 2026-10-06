# Voice Architecture

## Boundary

Speech is a replaceable subsystem. The brain decides what Miss Minutes says; the voice backend turns authorized text into audio.

Current implementation:
- Android TextToSpeech through VoiceBackend.
- No actor-specific voice model is bundled.

Future backends may include:
- an original Miss Minutes-inspired voice,
- a properly licensed third-party voice,
- an explicitly authorized performance/voice model.

## Actor-specific voice

The project should not silently turn a celebrity performance into a recognizable voice clone and then treat the private repository as a universal licence.

The current code therefore has no Tara Strong voice model, extracted performance dataset, or copied show dialogue.

If explicit authorization/licensing is obtained later, the voice backend can be swapped without changing the memory, brain, native UI, action system or character renderer.

## Character fidelity without copying a performance

The voice direction can still target high-level traits such as:
- bright theatrical delivery,
- warm retro-cartoon energy,
- mischievous timing,
- precise diction,
- occasional Southern-coded phrasing,
- controlled menace when the scene calls for it.

Those are behavior/style targets, not a claim that the output is Tara Strong's actual performance.
