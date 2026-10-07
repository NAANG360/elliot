# Custom Voice Engine Specification

Miss Minutes supports two speech paths:

1. Native Android TTS: uses an installed Android TextToSpeech engine and its voices.
2. Custom voice engine: reserved for a local neural voice backend implementing SpeechBackend.

## Ready-to-use model format

For a Piper-compatible local neural voice, the standard portable pair is:
- voice.onnx
- voice.onnx.json

The JSON contains the model/phoneme/audio configuration. The ONNX file contains the neural voice model. Keep the model's license/authorization information next to the model.

Piper training uses a dataset directory containing WAV audio and metadata.csv. For a single speaker, metadata.csv is:

id|transcript

Example:
utt0001.wav|Hello, this is Miss Minutes.
utt0002.wav|Well hello there, sugar!

The current Piper documentation describes 22050 Hz as a common training sample rate and recommends consistent, clean recordings. See the upstream training documentation before training a model.

## Recording ingestion

Raw recordings are training data, not a finished neural voice model. A recorder/importer can collect WAV samples plus transcripts, but the recordings must be preprocessed and trained/fine-tuned by a compatible voice-training pipeline before the Android runtime can synthesize arbitrary text.

Do not assume that dropping recordings into the APK magically creates a voice. The intended pipeline is:

recordings -> transcript metadata -> preprocessing -> training/fine-tuning -> exported model -> model validation -> Android custom voice backend

## Custom backend contract

A custom backend implements:
- speak(text)
- stop()
- shutdown()

It may use an embedded ONNX runtime/native inference engine or an installed Android TTS engine. The brain never depends on the implementation details.

## Authentic character voice

A custom voice model may only be used when the model and recordings are authorized for that use. The app architecture intentionally does not bundle an unauthorized performer voice clone.

An authorized model can be loaded without changing the brain, memory, or renderer.