# Miss Minutes Engine & Framework Instructions

## Runtime map

Miss Minutes is a native Android application.

- Kotlin: Android application layer, UI, memory, permissions, framework bridge, capability routing, and speech backend selection.
- C++17: native brain bridge and local GGUF inference through the vendored llama.cpp tree.
- JNI: connects Kotlin to the C++ brain.
- Gradle Kotlin DSL: Android build configuration.
- Android XML: manifest/resources/service declarations.
- Native Android Views/Canvas: current UI and mascot renderer. Do not change the renderer unless explicitly requested.
- GGUF: current local language-model format.
- Speech: replaceable backend boundary. The app can use Android TTS engines now; a future custom neural engine can implement the same SpeechBackend contract.

## Current brain

The local brain is loaded from a GGUF file and executed by llama.cpp through the native C++ layer.
The C++ inference path is: Kotlin MainActivity -> NativeBrain -> JNI -> missminutes.cpp -> llama.cpp -> GGUF.
The model is prompted to return one JSON object: {"text":"...","emotion":"...","gesture":"...","pace":"..."}.
Never treat model text as source code. Model output is data.

## Framework self-extension

The intended extension direction is declarative capability modules, not arbitrary self-modifying APK code.

A capability module should describe:
- stable capability id
- human-readable name
- version
- required permissions
- input schema
- output schema
- implementation type
- endpoint/action details
- safety/authorization requirements

For simple web APIs, a declarative module can be represented as JSON and executed by a generic HTTP capability executor. For Android-native features, a Kotlin implementation is required and must be compiled into the application or a separately installed plugin/service.

Miss Minutes may identify missing capabilities, research their APIs, draft a capability manifest, test the manifest, and place it in the framework workspace. It must never claim a capability exists until an executor actually loaded and successfully invoked it.

## Self-improvement rule

If a user asks for a capability that is not currently available:
1. Inspect the framework/capability documentation.
2. Determine whether the capability can be expressed as an existing generic operation.
3. If yes, create a declarative capability proposal.
4. If no, produce a concrete implementation plan specifying Kotlin/Java, C++/JNI, manifest, permission, or external-service work that is required.
5. Verify the resulting capability before claiming it is active.

The local Qwen model itself cannot compile or mutate the APK. A future agent/tool layer can perform those repository/build operations. Until such a tool layer is connected, do not hallucinate self-modification.

## API research

When external documentation is available, prefer primary API documentation. Record endpoint/action, authentication method, request schema, response schema, rate/usage constraints, required Android permissions, and whether the operation is local, networked, or user-authorized.
Keep API secrets out of source code and model prompts.

## Languages and files

Do not invent another programming language just because a feature is difficult. Prefer Kotlin for Android work and C++17 only where native performance/JNI is justified.