# Elliot Growth Format (EGF) v0.1

EGF is a versioned, manifest-first container for Elliot model families. It is not a claim that arbitrary tensors can be appended to a transformer. Growth occurs through compatible releases, adapters, retrieval packs and explicitly versioned architecture migrations.

## Directory layout

```
elliot.egf/
  manifest.json
  tokenizer/
    tokenizer.json
  core/
    config.json
    weights.safetensors
  adapters/
    <capability-id>/adapter.safetensors
    <capability-id>/adapter.json
  knowledge/
    <pack-id>/index.json
    <pack-id>/documents.jsonl
  evaluations/
    <suite-id>.json
  provenance/
    datasets.jsonl
    training.json
  signatures/
    manifest.sha256
```

## Manifest contract

Required fields: `format`, `format_version`, `model_id`, `model_version`, `architecture`, `tokenizer`, `core`, `adapters`, `knowledge_packs`, `compatibility`, `provenance`, `evaluation`. Every referenced file has byte length and SHA-256. Loaders reject path traversal, unknown major versions, missing hashes and incompatible tensor shapes.

## Growth rules

1. Core architecture changes create a new immutable model version.
2. Adapters declare exact base model ID, version, architecture fingerprint, target modules and rank.
3. Knowledge packs never modify weights; each record carries source URL, publisher, publication/retrieval timestamps, content hash, licence, and trust assessment.
4. Promotion requires benchmark results against the previous stable model, including coding, reasoning, communication, factuality and safety regression suites.
5. Keep at least one previous known-good checkpoint. Rollback is atomic.
6. Training datasets are provenance-tracked and licence-reviewed. No private or access-controlled data.
7. Model upgrades cannot rewrite the app's permission system, audit ledger, or Guardian policy.

## Resource profiles

A profile declares max resident bytes, context limit, thread count, quantization and allowed adapter set. Android selects only modules fitting the current memory/thermal budget. Model size does not expand automatically on a phone; external training compute may produce a candidate, while the phone verifies and installs it.

## Integrity

Hash every payload. Sign release manifests with a maintainer key kept outside the repository. The app pins trusted public keys. A valid hash proves integrity, not that a model is truthful or safe; evaluation remains mandatory.
