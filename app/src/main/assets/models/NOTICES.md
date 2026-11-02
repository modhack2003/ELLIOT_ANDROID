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
