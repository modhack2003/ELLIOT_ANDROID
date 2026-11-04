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

## Controlled Turbo decoder comparison (dev.8)

Diagnostic pack `whisper-turbo-full-decoder` reuses the pinned Turbo int8 encoder
and token table above, replacing only the decoder with `turbo-decoder.onnx` from
the same repository/revision. Decoder: 636209532 bytes, SHA-256
`b24db5d90fa230c5eaa6b823d74862ced9e0d1dc3e01ec46601968e8db0e09ec`.
Total pack: 1311742559 bytes. This is an experimental comparison, not an accuracy
recommendation. The installer reuses matching local files after hash verification.

WhisperTokenTransport derives an ASCII-hex token rendering table in app cache
from the verified original token table. This works around per-token UTF-8 cleanup
in Sherpa 1.13.8; the original downloads, token IDs and weights are unchanged.
The complete native result is decoded back to UTF-8 in Kotlin. No native inverse
text normalization or homophone rules may be enabled on this encoded text.

## GTCRN local speech denoiser

Optional streaming filter and diagnostic original/cleaned comparison through Sherpa-onnx 1.13.8 CPU.
- Upstream: https://github.com/Xiaobin-Rong/gtcrn (MIT, copyright 2024 Rong Xiaobin).
- Export: https://github.com/k2-fsa/sherpa-onnx/releases/download/speech-enhancement-models/gtcrn_simple.onnx
- GitHub asset ID: 236067657; 535638 bytes; upstream asset had no digest field.
- Bundled path: app/src/main/assets/models/denoise/gtcrn.onnx.
- SHA-256: e77603ac0c23dac3227dd2d7135b3a585cbee2679048aecfa886657d3ae1b534.
- License included alongside the model as denoise/LICENSE.txt.

All bundled files are verified by the updated model manifest before use.
Processing occurs locally in the private speech worker. No voice upload occurs.

## Phoneme wake model candidate

Zipformer zh-en 3M (2025-12-20), chunk 16, left context 64. The encoder and
joiner are int8 and the decoder is FP32. The upstream author lists Apache 2.0.
The APK includes the license in models/kws-phone/LICENSE.txt and identifies the
exact graphs, tokens and keywords through the model manifest.

- Training source [pkufool model card](https://modelscope.cn/models/pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20)
- Export [Sherpa release archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2)
- Archive SHA-256 `68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6`

The English dictionary phones come from en.phone. The accent phones are a
calibration candidate derived from local inference on the authorized sample.
