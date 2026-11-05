<p align="center">
  <img src="docs/brand/elliot-hero.png" width="100%" alt="ELLIOT, voice companion for Android. A cyan waveform threads through a dark glass orb." />
</p>

<h1 align="center">ELLIOT for Android</h1>

<p align="center">
  <strong>Say “Hey Elliot”. Ask about your phone. Hear the answer.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/source-0.6.3--dev.24-20D8E5?style=flat-square" alt="Source version 0.6.3-dev.24" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-54D88D?style=flat-square&logo=android&logoColor=white" alt="Android 8.0 or later" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-9D8AFF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose" />
  <img src="https://img.shields.io/badge/speech-on--device-14AABF?style=flat-square" alt="On-device speech" />
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> &nbsp;·&nbsp;
  <a href="#meet-the-app">Meet the app</a> &nbsp;·&nbsp;
  <a href="#speech-modes">Speech modes</a> &nbsp;·&nbsp;
  <a href="#how-voice-works">How voice works</a> &nbsp;·&nbsp;
  <a href="#development-snapshot">Project status</a> &nbsp;·&nbsp;
  <a href="#build-from-source">Build from source</a>
</p>

<p align="center">
  <a href="#how-voice-works"><img src="docs/brand/elliot-signal.gif" width="100%" alt="Decorative animated cyan waveform" /></a>
</p>

ELLIOT is a voice and text companion for Android. Say the wake phrase, tap the microphone, or type. Built-in commands can check your battery, set a timer, and change media volume offline. Wake detection, bundled speech recognition, and bundled voice playback run on the phone. For broader conversations, you can select a supported on-device or cloud provider in Settings.

<table>
  <tr>
    <td width="33%" valign="top"><strong>🎙️ Talk</strong><br /><br />Wake, tap, or type.</td>
    <td width="33%" valign="top"><strong>🧠 Speech</strong><br /><br />Bundled STT or optional Whisper.</td>
    <td width="33%" valign="top"><strong>⚙️ Control</strong><br /><br />Tune capture, filters, and providers.</td>
  </tr>
</table>

## Quick start

1. [Build the ARM64 preview from source](#build-from-source) and install your build on an **ARM64 Android 8.0+** device. In-place updates require the same package ID and signing certificate.
2. Open **ELLIOT**, tap **Resume**, grant microphone access, and enable **Voice & wake word** in Settings.
3. Say **“Hey Elliot”**, pause, and ask **“What is my battery level?”** You can also tap the microphone to start a request.
4. For an optional speech model, open **Settings → Check speech recognition**, select a pack and language, and choose **Use for live requests** once the pack is ready.

The preview uses the package ID `com.eiliot.app.preview`.

## Meet the app

<table>
  <thead>
    <tr>
      <th width="33%">01 &nbsp; Companion</th>
      <th width="33%">02 &nbsp; Vault</th>
      <th width="33%">03 &nbsp; Settings</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="docs/ui/0.6.2/companion-360-1x.png" width="240" alt="Companion conversation with the microphone, Live Call, and navigation controls" /></td>
      <td align="center"><img src="docs/ui/0.6.2/vault-360-1x.png" width="240" alt="Vault with Notes, History, and Web feed tabs and an erase control" /></td>
      <td align="center"><img src="docs/ui/0.6.2/settings-360-1x.png" width="240" alt="Settings with intelligence provider selection and voice options" /></td>
    </tr>
    <tr>
      <td align="center"><strong>Your conversation</strong><br />Talk, type, or open Live Call.</td>
      <td align="center"><strong>Your saved context</strong><br />Manage notes, history, and references.</td>
      <td align="center"><strong>Your preferences</strong><br />Choose voice, provider, and privacy controls.</td>
    </tr>
  </tbody>
</table>

<sub>Actual Jetpack Compose renders from 0.6.2 with sample data. Current voice options have since expanded.</sub>

| Feature | What you can do |
| --- | --- |
| **Personal wake phrase** | Calibrate “Hey Elliot” using your pronunciation. Fresh examples and control phrases must pass before a profile can be saved. |
| **Voice in a noisy room** | Compare the same wake recording with and without enhanced noise reduction. Eligible full-phrase profiles can listen before that filter while commands still use it. |
| **Offline phone commands** | Ask for battery status, set a timer, adjust volume, or open an app. Actions depend on the permissions you grant. |
| **Optional multilingual speech** | Download Whisper Small or Large-v3 Turbo. Choose Indian English, Hindi-English, Bengali-English, or Auto for short requests. |
| **Your choice of conversation mode** | Use offline commands, on-device Nano on supported phones, or a configured cloud provider for broader conversation. |

## Speech modes

<p align="center">
  <img src="docs/brand/speech-modes.svg" width="100%" alt="Optional Whisper language modes. Indian English, Hindi-English and Bengali-English are selectable. Auto is also available. Live accuracy is under evaluation." />
</p>

Choose a language setting and an optional **Whisper Small** or **Large-v3 Turbo** pack for short requests. The same model family handles these settings. Wake calibration adapts phrase matching separately. Hindi-English and Bengali-English still need more real-speech accuracy testing.

<details>
<summary><strong>Compare the speech engine options</strong></summary>

| Engine | Role in the app | Current evidence |
| --- | --- | --- |
| **Bundled Zipformer** | Default streaming English STT and Live Call | Runs offline. Misheard the live battery question in an earlier Vivo test. |
| **Whisper Small** | Optional multilingual short requests | Downloadable pack. Bengali-English results have included word errors and incomplete output. |
| **Whisper Large-v3 Turbo int8** | Optional multilingual short requests | Recognized the live Indian English battery question. Load and transcription took about 10 seconds in that run. |

</details>

## How voice works

<p align="center">
  <img src="docs/brand/voice-pipeline.svg" width="100%" alt="Android microphone audio passes through GTCRN. Eligible calibrated profiles can use input before GTCRN for wake detection. Wake or tapping opens request capture. Requests use on-device STT, then an offline tool or the chosen conversation provider." />
</p>

The wake detector runs while the microphone listener is active. For eligible calibrated profiles, the wake path can use input before GTCRN while request audio still goes through it. A private `:speech` process isolates Sherpa-onnx native inference from the app UI. Cloud conversation is optional and can send recognized text and relevant context to the selected provider.

<details>
<summary><strong>Explore the request-routing diagram</strong></summary>

```mermaid
flowchart LR
    MIC["Android microphone"] --> FILTER["Optional GTCRN<br/>noise reduction"]
    FILTER --> WAKE["Hey Elliot<br/>Zipformer keyword spotting"]
    MIC -. Calibrated bypass .-> WAKE
    FILTER --> CAPTURE["Capture request"]
    WAKE --> CAPTURE
    TAP["Tap to talk"] --> CAPTURE
    CAPTURE --> STT["On-device speech to text<br/>Zipformer or Whisper"]
    STT --> ROUTE{"Request routing"}
    ROUTE --> LOCAL["Offline phone tool"]
    ROUTE --> CHAT["Configured conversation provider"]
    LOCAL --> REPLY["Text and spoken reply"]
    CHAT --> REPLY

    classDef audio fill:#0b2330,stroke:#26cfe4,color:#ebfbff,stroke-width:2px
    classDef route fill:#231a38,stroke:#a992ef,color:#f1eaff,stroke-width:2px
    class MIC,WAKE,CAPTURE,FILTER,STT,TAP audio
    class ROUTE,LOCAL,CHAT,REPLY route
```

</details>

## Development snapshot

The current source is **0.6.3-dev.24**. These observations come from the Vivo checks recorded in the [agent handoff](AGENTS.md).

| Check | Observed result |
| --- | --- |
| 🟢 **Wake from a seat beside the table** | With the phone upright and the calibrated wake path listening before GTCRN, “Hey Elliot” opened listening and “Hey Ellie” was ignored in the live check. |
| 🟢 **Live Indian English request** | Whisper Turbo recognized the battery question and the local phone-status command returned the answer. Model load and transcription took about **10 seconds** in that run. |
| 🟠 **Still under evaluation** | The flat-table recording missed. Performance across rooms, distances, and speakers remains unmeasured. Hindi-English and Bengali-English modes are selectable and need further live accuracy testing. |

### Next validation targets

```mermaid
flowchart LR
    A["01 · Capture quality<br/>Flat + upright / quiet + fan"] --> B["02 · Wake reliability<br/>Real wakes + similar phrases"]
    B --> C["03 · Language accuracy<br/>Indian English + mixed speech"]
    C --> D["04 · Device behavior<br/>Latency + memory + power"]

    classDef next fill:#101f30,stroke:#5b8ca8,color:#e8f5fb,stroke-width:2px
    class A,B,C,D next
```

See the [voice evaluation guide](evaluation/voice/README.md) for the test procedure and [speech recovery notes](SPEECH_FIX.md) for the earlier native-worker crash fix.

## Build from source

Use **JDK 21**, **Android SDK 36**, and **Build Tools 35.0.0**. Set your SDK location in an untracked `local.properties` file or through your Android SDK environment.

```bash
git clone https://github.com/modhack2003/ELLIOT_ANDROID.git
cd ELLIOT_ANDROID
./gradlew :app:assemblePreview -PvoiceArm64Only=true
./gradlew :app:testPreviewUnitTest
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`. The ARM64 APK is built under `app/build/outputs/apk/preview/`. A locally signed preview uses your own debug signing key.

<details>
<summary><strong>Run native checks on a dedicated Android test device</strong></summary>

```bash
./gradlew :app:connectedPreviewAndroidTest -PvoiceArm64Only=true
```

Instrumentation runs the packaged speech engines and stops the companion service during setup. See the [voice evaluation guide](evaluation/voice/README.md) for optional model-pack tests and interpretation of results.

</details>

## Project resources

| Resource | Where to look |
| --- | --- |
| Current issues and next steps | [Agent handoff](AGENTS.md) |
| Architecture and implementation | [System design](EILIOT_SYSTEM_DESIGN.md) · [Android source](app/src/main/java/com/eiliot/app) |
| Speech models and versions | [Model sources](MODEL_SOURCES.json) · [Voice evaluation](evaluation/voice/README.md) |
| Terms and attribution | [Third-party models and runtime](THIRD_PARTY_MODELS.md) |

The speech runtime, voices, and model weights carry their upstream terms. The Ryan voice model card lists noncommercial dataset terms. See the third-party notices before redistributing an app or model pack.
