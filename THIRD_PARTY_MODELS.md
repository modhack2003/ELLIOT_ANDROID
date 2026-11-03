# Bundled speech models — ELLIOT 0.6.1

The application bundles actual model weights; no default acoustic model download is needed.

| Component | Source | Attribution / terms |
| --- | --- | --- |
| Runtime | https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8 | Sherpa source Apache-2.0, ONNX Runtime MIT; this pre-2.0 TTS binary includes GPL-3.0-or-later eSpeak phonemization. Runtime source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8 ; eSpeak source: https://github.com/espeak-ng/espeak-ng ; retain corresponding-source/build access for redistribution. |
| Zipformer English 20M int8 | https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models | Upstream Sherpa/icefall model; source/readme retained below |
| GigaSpeech English KWS 3.3M int8 | https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models | Upstream model; only explicit ELLIOT keyword token sequences configured |
| Silero VAD v5 | https://github.com/snakers4/silero-vad | MIT |
| Piper Ryan medium int8 | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models | RyanSpeech dataset attribution: https://www.kaggle.com/datasets/roholazandie/ryanspeech ; upstream model card identifies CC BY-NC-SA 4.0. Noncommercial restriction must be resolved before commercial distribution. |

Ryan is the requested named upstream male voice, not the Mr. Robot actor's voice. Keep its model card/attribution and upstream notices. Source code/runtime licenses do not supersede a voice/dataset's restrictions. No claim of unrestricted commercial voice rights is made.

The blueprint estimates 45 MB for all weights. Actual selected upstream int8 ASR encoder alone is about 42.8 MB; the build records real sizes instead of supplying placeholder/tiny invalid model files. Native libraries and phonemizer data add further size.

## Upstream Ryan model card

# Model card for ryan (medium)

* Language: en_US (English, United States)
* Speakers: 1
* Quality: medium
* Samplerate: 22,050Hz

## Dataset

* URL: https://www.kaggle.com/datasets/roholazandie/ryanspeech
* License: CC BY-NC-SA 4.0

## Training

Finetuned from U.S. English lessac voice (medium quality).

## Upstream ASR README

---
license: apache-2.0
---
# Introduction

This model is exported from https://huggingface.co/desh2608/icefall-asr-librispeech-pruned-transducer-stateless7-streaming-small.

Please see https://github.com/k2-fsa/icefall/pull/903.

It can be run with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).
# Optional multilingual speech pack

Whisper Small multilingual (244M parameters), int8 ONNX export. Task is
transcription, not translation. English, Hindi and Bengali options use the same
multilingual weights. Mixed-language quality must be measured on actual speech.

- Original model and MIT license: https://github.com/openai/whisper
- Export: https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small
- Pinned revision: `8f3c18b358db4d1f2fc1eae49d75cd20989e4309`
- Encoder: 112442483 bytes, SHA-256 `4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9`
- Decoder: 262226114 bytes, SHA-256 `acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee`
- Tokens: 816730 bytes, SHA-256 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126`

The 375485327-byte pack is downloaded separately, checked and activated only
after all files verify. It is not embedded in the base APK. The upstream MIT
license is included as `app/src/main/assets/whisper-LICENSE.txt`. The original
bundled English model remains available for comparison.

## Optional Whisper Large-v3 Turbo pack

This is a separate int8 multilingual candidate using the same pinned Sherpa
runtime. It is evaluated in diagnostics before any normal-command integration.

- Original model: https://huggingface.co/openai/whisper-large-v3-turbo (MIT).
- ONNX export: https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo
- Pinned revision: `2ca6ff69fc878651b770880507669577ac41c2ff`
- Encoder: 674716297 bytes, SHA-256 `b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b`
- Decoder: 361080764 bytes, SHA-256 `20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547`
- Tokens: 816730 bytes, SHA-256 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126`

Total: 1036613791 bytes. The upstream Whisper license is retained in the APK.
Both optional packs use `task=transcribe`. Their support for individual languages
does not establish Bengali-English or Hindi-English accuracy on this phone.
