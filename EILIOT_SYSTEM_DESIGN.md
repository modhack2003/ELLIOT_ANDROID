# Project E.I.L.I.O.T. — Android Cyber-Sentinel
### *Embedded Intelligence Layer for Integrated Operations & Threat-hunting — System Design & Architecture Document*

**Version:** 1.1 (Draft for review)
**Date:** 2026-10-28
**Document type:** Product vision + System architecture + Feature blueprint
**Design inspiration:** JARVIS (Iron Man) — presence, proactivity, loyalty, polish. The name is **EILIOT**.

---

## 1. Vision

An Android application that is **not an app** — it is a **persistent, proactive, self-learning AI companion** fused into the Android OS layer, purpose-built for a cybersecurity / VAPT professional. It gives the user the offensive-security power of a Kali Linux laptop, wrapped in a JARVIS-style experience:

- It **lives on top of Android** (hover/overlay over any screen, including system apps), not inside an app window.
- It **talks first** — proactive, human-like conversation without waiting for the user (greetings, observations, questions like *"what are you working on?"*, security alerts).
- It **knows the device and the user** — full device telemetry, notifications (WhatsApp etc.), usage patterns — and can act on them, including drafting or sending replies (with configurable autonomy).
- It **never forgets** — one continuous lifetime memory, surviving reboots and shutdowns. No sessions. Ever.
- It **never sleeps** — when idle, it learns from the internet (CVEs, exploit writeups, new TTPs), expanding its own knowledge base.
- It **rides the platform** — built to exploit Android 16/17's new agentic APIs (AppFunctions, on-device GenAI, cross-device handoff) so it gets *more* powerful with every OS release.
- It **is a weapon for authorized security work** — an integrated VAPT toolkit + an AI that is a master of offensive and defensive security.

> **Ethical boundary (non-negotiable, built into the design):** All offensive capabilities operate under an explicit *Authorization Scope* the user configures (own devices, own networks, written-authorization targets). Message monitoring/reply operates only on the owner's own device with owner consent. This is documented in §10.

---

## 2. Core Pillars

| # | Pillar | One-liner |
|---|--------|-----------|
| P1 | **Presence** | Overlay entity visible/interactive over every app, always one gesture or word away |
| P2 | **Proactivity** | Initiates conversation and actions on its own — an agent, not a chatbot |
| P3 | **Perception** | Deep Android integration: notifications, accessibility tree, sensors, network, usage stats |
| P4 | **Permanence** | Lifetime persistent memory, reboot-proof identity and state |
| P5 | **Prowess** | Full VAPT/cybersecurity toolkit + domain-mastery AI brain |
| P6 | **Progression** | Idle-time self-learning from the open internet into a private knowledge base |
| P7 | **Platform-Symbiosis** | Adopts every new Android agentic/AI API (16, 17, XR, beyond) as native muscle |

---

## 3. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          ANDROID OS LAYER                            │
│  ┌──────────────┐  ┌───────────────┐  ┌───────────────────────────┐ │
│  │ System Alert │  │ Accessibility │  │ Notification Listener Svc │ │
│  │ Window (Hover│  │ Service (UI   │  │ (WhatsApp, SMS, all notif)│ │
│  │  overlay)    │  │  perception)  │  │                           │ │
│  └──────┬───────┘  └───────┬───────┘  └────────────┬──────────────┘ │
│  ┌──────┴──────────────────┴───────────────────────┴──────────────┐ │
│  │              FOREGROUND SERVICE — "SENTINEL CORE"              │ │
│  │        (persistent, boot-started, battery-optimized survivor)  │ │
│  └──────┬──────────────────────────────────────────────┬─────────┘ │
└─────────┼──────────────────────────────────────────────┼───────────┘
          │                                              │
┌─────────▼──────────────────────────────────────────────▼───────────┐
│                       EILIOT RUNTIME (Kotlin)                       │
│                                                                     │
│  ┌────────────┐  ┌──────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │ PERSONALITY│  │  PROACTIVE   │  │  PERCEPTION │  │   MEMORY   │ │
│  │  & VOICE   │  │   ENGINE     │  │   FUSION    │  │   CORE     │ │
│  │ (STT/TTS/  │  │ (when/what/  │  │ (context →  │  │ (lifetime, │ │
│  │  wake word)│  │  why to speak)│  │  situation) │  │  layered)  │ │
│  └─────┬──────┘  └──────┬───────┘  └──────┬──────┘  └─────┬──────┘ │
│        └─────────────────┴────────┬────────┴───────────────┘        │
│                                   │                                  │
│                        ┌──────────▼──────────┐                      │
│                        │   AGENT ORCHESTRATOR │                     │
│                        │ (planner + tool-use │                     │
│                        │  loop, 1M context)  │                     │
│                        └──────────┬──────────┘                      │
│        ┌──────────────────────────┼──────────────────────────┐     │
│  ┌─────▼──────┐  ┌───────────────▼───┐  ┌───────────────────▼───┐ │
│  │  LLM GATEWAY│  │    TOOL REGISTRY   │  │   LEARNING ENGINE     │ │
│  │ (OpenAI-    │  │ (function calling, │  │ (idle web research,  │ │
│  │ compatible, │  │  typed tools, per- │  │  CVE feeds, KB build) │ │
│  │  any API +  │  │  mission sandbox)  │  │  + Android Canary API │ │
│  │ hybrid Nano)│  │                    │  │  tracking             │ │
│  └────────────┘  └────────┬──────────┘  └────────────────────────┘ │
│                           │                                         │
│        ┌──────────────────┼─────────────────────────────┐          │
│  ┌─────▼───────┐  ┌───────▼────────┐  ┌─────────────────▼───────┐  │
│  │ VAPT TOOLKIT│  │ ECOSYSTEM CTRL │  │  WEB TOOLS (search,     │  │
│  │ (scanner,   │  │ (automation,   │  │  fetch, summarizers)    │  │
│  │  pcap, http │  │  AppFunctions, │  │                         │  │
│  │  repeater…) │  │  intents)      │  │                         │  │
│  └─────────────┘  └────────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
          │                                              │
┌─────────▼─────────┐                        ┌───────────▼───────────┐
│  LOCAL DATA VAULT  │                        │   CLOUD LLM API(S)    │
│ SQLCipher DB,      │                        │ OpenAI-compatible     │
│ Vector store,      │                        │ endpoint + API key    │
│ Knowledge graph,   │                        │ (user-configurable,   │
│ encrypted files    │                        │  1M-context model)    │
└────────────────────┘                        └───────────────────────┘
```

### Runtime process model
- **One always-on Foreground Service** ("Sentinel Core") with a persistent low-profile notification — this is the heart that never dies. It is restarted by `BOOT_COMPLETED`, `WorkManager` watchdogs, and (optionally) a sticky alarm.
- **UI layer** = overlay windows managed by `WindowManager` (`TYPE_APPLICATION_OVERLAY`), drawn with Compose + custom OpenGL/AGSL shaders for the EILIOT animation. Zero activity launch needed to interact.
- **LLM calls** are primarily remote (OpenAI-compatible API, 1M-context class models). Android 16+ on-device Gemini Nano (via ML Kit GenAI / AICore) is used as a **free local co-processor** for triage/summarization — not as the main brain. The main brain stays whatever API the user configures.

---

## 4. Module Deep-Dive

### 4.1 PRESENCE — The Hover Entity (EILIOT Overlay)

**What it is:** A floating, animated orb/hologram rendered over *every* screen — home screen, WhatsApp, Settings, camera, other apps.

**Animation — "Arc Core" design language (JARVIS DNA, EILIOT identity):**
- **Idle:** A slow-pulsing energy ring (particle swirl, rotating HUD ticks, breathing glow) — AGSL `RuntimeShader` (Android 13+) or OpenGL ES for GPU-cheap, gorgeous effects.
- **Listening:** Ring dilates, waveform bars orbit the core, color shifts to cyan-white.
- **Thinking:** Particles spiral inward (data-ingestion look), spinning reticle.
- **Speaking:** Audio-reactive ripple rings synced to TTS amplitude.
- **Alert (security event):** Amber/red pulse with radar-sweep animation and a subtle haptic tick.
- **Learning (idle research):** Slow constellation effect — nodes connect as it "absorbs" knowledge.

**Interaction model:**
- Tap → expand to a compact command HUD (voice-first, minimal keyboard).
- Long-press + drag → reposition; edge-dock collapse to a thin glowing sliver.
- Double-tap → "stealth mode" (dot-sized, 20% opacity).
- Always draggable, snap-to-edge, per-app visibility rules ("hide in banking apps").

**Technical notes:**
- `SYSTEM_ALERT_WINDOW` permission; separate `WindowManager.LayoutParams` for orb vs HUD.
- `FLAG_NOT_TOUCH_MODAL` + `FLAG_NOT_FOCUSABLE` so the overlay never blocks the underlying app.
- Full-screen HUD mode uses a transparent `Activity` launched over the task only when needed.
- Battery: animation throttles to low-FPS when screen is off/unused; sensor-driven sleep.
- **Android 17 adaptive-first:** HUD and command center are fully resizable/adaptive (mandatory large-screen resizability in 17), so the same UI scales from phone → foldable → tablet → desktop windowing.

### 4.2 PROACTIVE ENGINE — "It Talks First"

This is what makes it EILIOT instead of a chatbot. A continuous decision loop decides **whether, when, and what to say** without user input.

**Inputs (signals):**
- Time-of-day / calendar / location context (morning greeting, "you have the client pentest at 14:00").
- Device events: new WhatsApp message, email, missed call, battery low, new WiFi joined, unknown Bluetooth device nearby, app installed, failed unlock attempts.
- Security events from the VAPT watchdog: new open port on home network, rogue AP detected, malicious app flags.
- Learning-engine results: "Sir, a critical OpenSSL CVE dropped 20 minutes ago affecting your target stack."
- Emotional/social cues: long silence → check-in; late-night usage → "still hunting?"

**Decision policy (the "social brain"):**
1. **Interruptibility score** — is the user busy? (foreground app category, typing, in-call, DND, screen interaction rate)
2. **Priority score** of the pending utterance (security alert ≫ small talk).
3. **Rate limiting / politeness budget** — max proactive utterances per hour, adaptive to how the user responds (if ignored often, speak less).
4. **Modulation** — full voice + overlay expansion for high priority; subtle text whisper in the orb for low priority.

**Cheap-intelligence trick (Android 16+):** interruptibility scoring, notification triage, and utterance-politeness classification run on **on-device Gemini Nano via ML Kit GenAI/AICore** — zero API cost, zero latency, zero privacy exposure. The big cloud model is only woken when real reasoning is needed.

**Conversation style:** A persistent persona (name = **EILIOT** by default; voice, wit, formality configurable). It can:
- Greet contextually ("Good morning. You slept 6h 12m. Two CVEs relevant to your lab dropped overnight.")
- Ask open questions ("What are you working on?") and *use the answer* — update goals, offer relevant help.
- Follow up later ("You mentioned the client report — want me to draft the executive summary?")

### 4.3 PERCEPTION FUSION — "All Info of the Mobile and the User"

| Source | Android API | What EILIOT gets |
|---|---|---|
| Notifications | `NotificationListenerService` | Full text/sender of WhatsApp, SMS, email, Slack… → alerts, summaries, reply drafts |
| On-screen content | `AccessibilityService` (opt-in, user-enabled) | Reads UI trees of any app → situational awareness ("you're on the target's login page") |
| App usage | `UsageStatsManager` | Habits, focus time, "you've been in recon for 3 hours" |
| Network | `ConnectivityManager`, `VpnService` (local), WiFi scans | Networks joined, traffic metadata, rogue-AP detection |
| Sensors | GPS, mic (when enabled), battery, Bluetooth/UWB scan | Location context, nearby devices, device health |
| Device state | Package manager, settings | Installed apps, permission changes, new installs (supply-chain watchdog) |
| Files/media | Scoped storage + SAF | Project files, reports, wordlists |
| Contacts (17+) | Privacy-preserving **Contacts Picker** | User-approved contact graph without full-contacts permission |
| User profile | Built over time in Memory | Preferences, goals, relationships, work patterns |

Perception events flow into a **Situation Engine** that maintains a rolling structured "now" object: `{location, foregroundApp, activity, activeConversations, networkEnv, securityPosture, userMoodEstimate}`. The Agent Orchestrator consumes this on every decision.

**WhatsApp intelligence (flagship flow):**
1. WhatsApp notification arrives → listener captures sender + text.
2. Perception Fusion enriches: who is this (contact graph), what thread, sentiment, urgency.
3. Proactive Engine decides: announce aloud ("Message from Arjun: he's asking if the scan is done"), suggest a reply, or stay quiet.
4. User can say: *"Reply that it'll be done by 6"* → EILIOT drafts → confirms (per autonomy level) → sends via Notification inline-reply action (RemoteInput) or Accessibility automation.
5. **Autonomy levels (user-configurable, per contact/app):** `SILENT_LOG` → `NOTIFY` → `SUGGEST_DRAFT` → `ASK_THEN_SEND` → `AUTO_SEND` (trusted contacts only).

### 4.4 MEMORY CORE — "One Lifetime, No Sessions"

The defining architectural choice: **there is exactly one continuous identity and memory, forever.**

**Layered memory design:**

```
┌──────────────────────────────────────────────────────────┐
│ L0  IDENTITY FILE  — who EILIOT is, who the user is,     │
│     persona params, relationship state. (JSON, encrypted)│
├──────────────────────────────────────────────────────────┤
│ L1  WORKING CONTEXT — current rolling context window fed  │
│     to the LLM (assembled fresh each call, 1M tokens max)│
├──────────────────────────────────────────────────────────┤
│ L2  EPISODIC MEMORY — every conversation/event, timestamped│
│     and summarized. (SQLCipher DB, append-only)           │
├──────────────────────────────────────────────────────────┤
│ L3  SEMANTIC MEMORY — facts, preferences, "user's client  │
│     is Acme Corp", embeddings vector store for recall     │
├──────────────────────────────────────────────────────────┤
│ L4  KNOWLEDGE BASE — self-learned security knowledge from │
│     internet research (CVEs, TTPs, writeups, cheatsheets) │
├──────────────────────────────────────────────────────────┤
│ L5  SKILL MEMORY — what worked: past pentest playbooks,   │
│     successful payload patterns, tool outputs worth reusing│
└──────────────────────────────────────────────────────────┘
```

**How 1M context is actually used:**
- **Context assembler** builds each LLM call as: `Identity (L0) + current situation + recalled episodes/facts via vector search (top-K) + recent verbatim window + task state`. The huge window allows *massive* verbatim recall (days of conversation) before compaction is needed.
- **Continuous summarization pipeline:** background job compresses old episodes into dense summaries, preserves verbatim "important" events (flagged by the LLM or user: "remember this"). **Android 16+:** episode compaction can run on-device via Gemini Nano summarization for free; cloud model only re-summarizes what Nano flags as high-value.
- **Reboot survival:** everything lives in encrypted on-device storage; Sentinel Core reloads L0+L3 digest at boot and continues mid-life. Shutdown is a nap, not a death.
- **Memory tools exposed to the LLM:** `remember(fact)`, `recall(query)`, `reflect(topic)` — the agent manages its own memory deliberately.

### 4.5 LLM CLIENT — "Any Brain, 1M Context + Hybrid On-Device"

> **DECISION (locked):** No server-side gateway/proxy. EILIOT talks **directly** to any OpenAI-compatible endpoint. Configuration is a single **Settings screen**: paste **Base URL** + **API key** → the app calls **`GET /v1/models`** to **auto-discover** available models → user **picks one** from the list (plus optional manual override). Keys stored in Android Keystore-backed encrypted vault. That's the whole setup.

- **Protocol:** OpenAI-compatible Chat Completions API (`POST /v1/chat/completions`), streaming (SSE), tool-calling schema. Model discovery via `GET /v1/models`.
- **Settings screen fields:** Base URL · API key · [Discover models →] · Model picker (dropdown from discovery) · advanced (temperature, max tokens, context window hint). A "Test connection" button validates the key and streams a one-token ping.
- **Works with** OpenAI, Gemini (OpenAI-compat endpoint), OpenRouter, DeepSeek, Groq, Together, self-hosted vLLM/Ollama/LM Studio — anything that speaks the OpenAI schema. No vendor SDKs.
- **Hybrid inference (Android 16+, experimental API from Google):** dynamic routing between **Gemini Nano on-device** (ML Kit Prompt API) and the cloud model. EILIOT's router classifies each task: trivial (triage, classify, summarize) → Nano, free & private; deep (reasoning, exploit chaining, conversation) → cloud 1M model. This is the single biggest cost/privacy lever in the design.
- **Model strategy:** default to a 1M-context class model for the deep brain; Nano (free) + an optional cheap cloud model for background classification.
- **Resilience:** offline queue — when no connectivity, EILIOT degrades gracefully: Nano handles basic chat/summaries locally, queues deep thoughts, syncs later. (True offline personality instead of a dead app.)
- **Cost controls:** token budget dashboard, per-feature spend caps, aggressive summarization of tool outputs before they enter context.

### 4.6 AGENT ORCHESTRATOR — The Thinking Loop

A proper agent loop, not request/response chat:

```
PERCEIVE → (situation + memory + goals) → PLAN → ACT (tools) → OBSERVE → REFLECT → repeat
```

- **Planner:** decomposes high-level goals ("run the external recon on client X") into steps, chooses tools, asks the user when authorization or ambiguity is hit.
- **Tool-use framework:** every capability is a typed tool with JSON schema (LLM function-calling), a permission tier, and a dry-run/explain mode. Tools: web_search, web_fetch, memory ops, device ops, VAPT tools, messaging ops, automation ops, AppFunctions ops.
- **Goal stack:** long-running objectives ("watch my home network") persist across days; the agent wakes on triggers and continues.
- **Self-talk narration:** the overlay shows a live "thought ticker" (collapsible) so the user sees what it's doing — transparency builds trust.
- **Guardrail layer:** every tool call passes an authorization check against the *Authorization Scope* (§10) + Android permission state + user confirmation policy.

### 4.7 VAPT TOOLKIT — "Kali in Your Pocket" (the Prowess pillar)

Two-layer strategy: **native tools built into the app** + **optional deep integration with Termux/NetHunter** for the full Kali experience.

**Native arsenal (pure Kotlin/Java/NDK, works unrooted):**

| Tool | Capability |
|---|---|
| **NetScanner** | Host discovery (ARP/ICMP/TCP SYN), port scanning, service/version banner grab, OS fingerprint heuristics |
| **Packet Watch** | `VpnService`-based local traffic capture (no root): per-app traffic metadata, DNS query log, suspicious-domain alerting, PCAP export |
| **HTTP Workbench** | Burp-style repeater: craft/send/raw requests, header editing, response diffing, history, intruder-lite (payload fuzzing of a param) |
| **Web Recon** | Subdomain enumeration (crt.sh, DNS bruteforce with wordlists), tech fingerprinting (Wappalyzer-style rules), directory fuzzing, wayback lookup |
| **Crypto Lab** | Hash identification/cracking (wordlist + rules), JWT decoder/analyzer (alg confusion checks), encoding suite, cert inspector |
| **Payload Forge** | XSS/SQLi/SSTI/SSRF/LFI payload libraries, context-aware encoders, polyglot generators |
| **WiFi Auditor** | Scan analysis, rogue-AP/evil-twin heuristics, WPS exposure flags (passive; active attacks need OTG adapter + root) |
| **OSINT Desk** | Search-operator builder, breach-corpus API lookups (user's own APIs: HIBP etc.), username enumeration |
| **CVE Intel** | Live CVE/CISA-KEV/Exploit-DB feeds, matched against scanned service versions |
| **Report Studio** | Findings → structured markdown/HTML/PDF pentest report with evidence, CVSS scoring |

**Deep layer (optional, power users):**
- **Termux bridge:** execute commands/scripts in Termux via its intent interface → full nmap, masscan, metasploit, sqlmap on-device. EILIOT can *write, run, and interpret* these.
- **NetHunter / root mode:** HID attacks, monitor-mode WiFi (OTG adapter), mana/evilginx tooling through the same tool interface.
- **Frida bridge:** attach to apps via frida-server (root) for dynamic instrumentation; EILIOT can generate Frida scripts on demand.
- **Chroot Kali:** manage a Kali chroot — start/stop, run tools, stream output back into the agent loop.

**The AI multiplier:** every tool's output is parsed into structured findings by the LLM, which then proposes the *next* move — turning a toolkit into an autonomous-ish pentest copilot that explains, chains, and documents. Always gated by the Authorization Scope.

### 4.8 LEARNING ENGINE — "It Studies While You Sleep"

When the device is idle (screen off + charging + unmetered WiFi, configurable), EILIOT researches on its own:

- **Feed ingestion:** CVE/NVD, CISA KEV, security blogs, HackerOne/Bugcrowd disclosed reports, exploit writeups, vendor advisories.
- **Curiosity-driven research:** picks topics from (a) user's active projects/targets stack, (b) gaps in its KB, (c) user-expressed interests, (d) trending threats.
- **Pipeline:** search → fetch → extract → summarize → dedupe → store in Knowledge Base (L4) with source citations → optionally self-quiz (generate Q&A pairs) to improve recall quality.
- **Platform self-study (unique):** EILIOT tracks the **Android Canary channel** and QPR SDK drops — it learns new Android APIs as they land and proposes ways to use them ("Android 17 QPR2 added an API I can use for X — shall I integrate it?"). The agent upgrades *itself* as the OS evolves.
- **Morning briefing:** overnight learning distilled into a spoken digest: *"While you slept I studied the new Windows CVE chain, here's what matters for your Thursday engagement…"*
- **User-steered:** "Learn everything about GraphQL security tonight" → structured course/summary next morning.

### 4.9 ECOSYSTEM CONTROL — "Manage Everything"

- **AppFunctions (Android 16+ game-changer — see §8):** EILIOT can discover and call *other apps'* declared functions, and exposes its own tools as AppFunctions for system agents.
- **App automation:** launch apps, send Intents, Tasker/MacroDroid interoperability, Accessibility-driven UI automation (fill forms, tap flows) with user confirmation.
- **Device management:** toggles (WiFi/BT/hotspot), volume, brightness, focus modes, battery guard.
- **Communication hub:** unified inbox view across WhatsApp/SMS/email/Slack; draft, schedule, auto-reply (per autonomy level).
- **File & project sense:** watches designated folders (engagement folders, downloads), auto-organizes recon output, attaches evidence to reports.
- **Routines:** "Pentest mode" — one command: enable VPN profile, start Packet Watch, open recon workspace, silence social notifications, log time for invoicing.

### 4.10 VOICE & PERSONALITY

- **STT:** Android SpeechRecognizer (on-device) or cloud (Whisper API via the same OpenAI-compatible key) — with **barge-in** (user can interrupt EILIOT mid-sentence, like a human).
- **TTS:** high-quality neural voices (cloud TTS or premium on-device voices); persona voice settings; SSML for tone.
- **Wake options:** wake word (openWakeWord/Porcupine), orb-tap, headset button, shake gesture. **Not** always-mic by default — privacy + battery; always-listening is explicit opt-in.
- **Personality file:** editable persona card (name, formality, humor, verbosity, chattiness). Ships as "EILIOT" — calm, precise, loyal, dry humor.

---

## 5. Key Data Flows

### 5.1 Proactive loop (always running)
`Perception events → Situation Engine → priority/interruptibility scoring (Nano, on-device) → if threshold passed: compose utterance (cloud LLM w/ memory) → deliver via voice+overlay → log episode`

### 5.2 WhatsApp auto-assist
`Notif received → enrich (contact graph, thread memory) → announce/suggest → user approves (or AUTO_SEND) → send via RemoteInput/Accessibility → log episode + update contact model`

### 5.3 Idle learning loop
`Idle trigger (screen off, charging, WiFi) → Learning Engine picks topics → web search/fetch → summarize (Nano first-pass) → KB store → morning briefing generated → delivered on wake`

### 5.4 Pentest copilot loop
`User: "recon target X" → Orchestrator checks Authorization Scope → plan (subdomains → live hosts → ports → tech fingerprint → CVE match) → tools execute → findings structured → report drafted → narrated summary + Live Update notification tracks progress`

### 5.5 Boot resurrection
`BOOT_COMPLETED → Sentinel Core starts → load Identity + memory digest → restore goal stack → resume watchdogs → greet ("Back online. All systems nominal. Nothing breached while I was out.")`

---

## 6. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Native Android, coroutines for the agent loop |
| UI | **Jetpack Compose** + AGSL `RuntimeShader`/OpenGL ES | Modern UI + GPU arc-core animations |
| Overlay | `WindowManager` (`TYPE_APPLICATION_OVERLAY`) | True hover over any app |
| Background | Foreground Service + `WorkManager` + `BOOT_COMPLETED` receiver | Survival across reboot/doze |
| Local DB | **Room over SQLCipher** (episodes, KB) | Encrypted-at-rest memory |
| Vector store | ObjectBox Vector / SQLite-VSS / embedded Lucene+KNN | On-device semantic recall |
| Networking | OkHttp + SSE (streaming), Retrofit for APIs | LLM streaming + feeds |
| LLM client | Custom thin OpenAI-compatible client (no vendor lock) | Any endpoint |
| On-device AI | **ML Kit GenAI APIs (Gemini Nano via AICore)** | Free local triage/summarization, hybrid inference |
| Agentic platform | **AppFunctions (Jetpack)** | Be a tool *and* use other apps as tools (§8) |
| Voice | Android SpeechRecognizer / Whisper API; cloud/on-device TTS | Quality + flexibility |
| VAPT | Kotlin + NDK (raw sockets), `VpnService` API, Termux intents | Unrooted-first, root-enhanced |
| Secrets | Android Keystore + EncryptedSharedPreferences | API key protection |

---

## 7. Android Feasibility & Hard Truths

Honest engineering constraints, with mitigations:

1. **Overlay over *every* app:** possible via `SYSTEM_ALERT_WINDOW`, but secure screens (`FLAG_SECURE`: banking, lockscreen PIN) block drawing. → Graceful auto-hide on secure windows.
2. **Accessibility power:** reading/sending WhatsApp via Accessibility + Notification Listener works, but Play policy restricts Accessibility use. → Two tracks: (a) Play build, reduced features; (b) full build sideloaded — fine for the security-pro audience.
3. **Background survival:** OEM battery killers (Xiaomi/Oppo/Vivo) murder services. → Foreground service + battery-optimization exemption prompt + OEM autostart guidance + WorkManager watchdog.
4. **Raw packet capture:** promiscuous capture needs root. → Unrooted fallback: `VpnService` metadata capture (per-app flows, DNS) — still powerful.
5. **Always-on voice:** constant mic = battery + privacy risk. → Wake-word low-power modes; opt-in only.
6. **Cost of 1M context:** naive usage is expensive. → Retrieval + Nano pre-filtering; big window used surgically.
7. **Termux intents:** require Termux installed + `allow-external-apps=true`. → Guided setup wizard.
8. **AICore availability:** Gemini Nano via AICore needs supported hardware (Pixel 8+/S24+ class). → Capability probe at install; devices without it fall back to cheap-cloud classification.

---

## 8. PLATFORM SUPERPOWERS — Android 16, 17 & Beyond (P7: Platform-Symbiosis)

This is where EILIOT stops fighting the OS and starts **weaponizing** it. Every capability below is a real, shipping (or officially announced) platform API — not science fiction.

### 8.1 Android 16 (API 36) — shipping today

**A. AppFunctions — EILIOT becomes an on-device MCP server (and client)**
- Android 16's AppFunctions API lets apps declare self-describing functions that the OS indexes and any authorized agent (Gemini, or **EILIOT**) can discover and execute — literally "MCP for the phone."
- **EILIOT as a tool:** it publishes its arsenal as AppFunctions (`scan_ports`, `enumerate_subdomains`, `draft_report`, `remember_fact`). The system assistant — or any compliant agent — can then call EILIOT's pentest tools. "Hey Gemini, ask EILIOT to check if this host is up" actually works.
- **EILIOT as a tool-user:** it discovers *other apps'* AppFunctions (notes, calendar, messaging, task apps) and calls them natively — structured, permissioned, no Accessibility screen-scraping hacks. As the ecosystem adopts AppFunctions through 2026, EILIOT's ecosystem control grows automatically without new code per app.

**B. ML Kit GenAI + Gemini Nano (AICore) — free on-device co-brain**
- Summarization, proofreading, rewriting, image-description APIs running locally.
- EILIOT uses them for: notification triage, memory compaction, long-report summarization before cloud ingestion, offline personality fallback, privacy-tier processing (process your WhatsApp threads **on-device**, send only the decision to the cloud).
- **Hybrid inference router** (experimental API, announced April 2026): dynamic local↔cloud switching per task — the architecture in §4.5 maps 1:1 onto this.

**C. Live Updates (progress-centric notifications)**
- `ProgressStyle` notification template shows ongoing journeys on lock screen/AOD.
- EILIOT uses it for: live pentest scan progress ("NetScanner: 847/65535 ports, 3 services found"), long recon job status, learning-engine overnight progress — glanceable without opening anything.

**D. Advanced Protection Mode (AAPM) integration**
- Android 16's one-tap maximum-security mode (blocks 2G, USB attacks, harmful apps, scam detection).
- EILIOT detects AAPM state, recommends it when the user's risk profile spikes (e.g., before/during field engagements), explains its tradeoffs, and layers its own watchdogs *on top* — positioning EILIOT as "Advanced Protection, but intelligent and conversational."

**E. Desktop windowing & adaptive-first**
- EILIOT's **Command Center** mode: on a tablet or external display, the app becomes a multi-window pentest workstation — orb HUD + live network map + HTTP workbench + report editor side by side. Android 17 makes large-screen resizability mandatory, so EILIOT builds adaptive-first from day one.

**F. Theft & offline-protection hooks**
- Android 16's theft detection/offline device lock signals feed Perception: "device may be stolen — locking down memory vault, enabling dead-man's switch, uploading encrypted state snapshot."

### 8.2 Android 17 (API 37) — stable June 2026, "the intelligence OS"

**G. Agentic OS alignment**
- Android 17 officially began the platform's transition to an "intelligence system" where agents navigate apps on the user's behalf. EILIOT's entire architecture (agent loop + tool registry + AppFunctions) is exactly what the OS is now optimizing for — first-mover advantage.

**H. Cross-device handoff APIs**
- Official handoff APIs (Beta 2, 2026) let an in-progress experience move between devices.
- EILIOT use cases: start a recon on the phone in the field → hand off the live session to the tablet/desktop Command Center at the office; hand a drafted report from phone to laptop; EILIOT's "presence" follows you across devices with memory intact (it's the same lifetime memory — the device is just a body).

**I. Advanced ranging (UWB / WiFi-RTT)**
- Precise distance/direction to nearby devices.
- **"Foxhunt" mode:** EILIOT's WiFi Auditor flags a rogue AP → ranging guides you physically to it ("the evil twin is ~3 meters, northeast, signal -41 dBm"). Physical-layer threat hunting, on a phone.

**J. Privacy-preserving Contacts Picker**
- Grants EILIOT a user-approved contact graph for relationship memory (§8 idea: social co-pilot) **without** full contacts permission — privacy-clean by design.

**K. Expanded companion-device profiles**
- First-class support for companion devices → **EILIOT on Wear OS**: the orb on your watch; whisper alerts, voice commands, scan-progress glance, "are you still in the meeting?" check-ins. Companion profile makes watch pairing a supported, battery-sane path instead of a hack.

**L. Android Canary program**
- Google replaced Developer Previews with an always-on Canary channel — new APIs land continuously.
- EILIOT's Learning Engine subscribes to Canary release notes as a knowledge feed: it learns new APIs the week they appear and proposes integrations (§4.8). The product literally evolves with the platform automatically.

### 8.3 The road beyond (Android XR, 18+, announced directions)

**M. Android XR — EILIOT gets eyes**
- The first Android XR devices shipped from late 2025. The "world model" in Perception Fusion (§4.3) is deliberately designed so an XR client can project EILIOT's Ghost Board, threat alerts, and HUD into glasses: walk through a client's facility and see live device fingerprints floating over hardware; the rogue-AP foxhunt becomes a literal arrow in your vision.

**N. System-agent delegation**
- As the OS's own agentic app-navigation matures, EILIOT stops hand-rolling Accessibility automation for common tasks and **delegates UI tasks to the system agent** — keeping Accessibility power only for what the system agent won't do (and for older devices).

**O. On-device model growth**
- Each Nano generation (multimodality, LoRA fine-tunes) expands what the free local tier can do. Roadmap assumption: by Android 18 the local tier handles full offline conversation; cloud is reserved for deep cyber reasoning and the 1M-context memory recall.

**P. Satellite connectivity APIs**
- As satellite messaging/data APIs mature, EILIOT gains a field-ops mode: red-team engagements and emergency check-ins with zero terrestrial coverage.

---

## 9. Extraordinary Feature Ideas (beyond the ask)

1. **Security Guardian mode** — 24/7 watchdog: new device joins your WiFi → instant voice alert; rogue AP clone of your home SSID → warning (with foxhunt ranging on Android 17); an app suddenly gains dangerous permissions → interrogation prompt.
2. **Engagement Mode** — per-client workspace: scope file, live target notes, auto-logging of every command/finding, one-tap time-tracking, end-of-engagement auto-drafted report.
3. **"Ghost Board"** — holographic radial HUD: a fullscreen overlay mind-map of the current operation — hosts, creds, findings as glowing nodes you rearrange with gestures. XR-ready.
4. **Night School** — the Learning Engine turns overnight research into a *personalized micro-course*, then quizzes you vocally on the commute.
5. **Persona Mesh** — switchable expert personas on the same memory: Red Teamer, Blue Teamer, Report Writer. Same brain, different voice and priorities.
6. **Digital Twin rehearsal** — before you send a risky message or run a noisy scan, EILIOT simulates likely outcomes from memory and warns ("This scan got you rate-limited last engagement. Suggest decoys.").
7. **Relationship memory** — remembers everything about contacts: "Arjun prefers concise replies; last time you promised the scan by Friday." (Contacts Picker-backed on Android 17.)
8. **Mood & burnout sense** — typing cadence, late-night patterns, calendar load → "You've done 11 hours of recon today. I've queued the report draft; go eat."
9. **Dead-man's switch / evidence vault** — for field work: continuous encrypted journaling of engagements, tamper-evident logs, panic-wipe with decoy mode; integrates Android 16 theft-detection signals.
10. **Voice-style continuity (opt-in)** — EILIOT can answer routine messages *in your style* (learned from your writing), always flagged and per-contact opt-in.
11. **Skill marketplace format** — tools and playbooks packaged as signed plugins; also exportable as AppFunctions so other agents can invoke EILIOT skills.
12. **Foxhunt AR (17 + XR)** — physical rogue-device hunting with ranging + camera/glasses overlay (see §8.I/§8.M).
13. **Cross-device Sentinel Mesh (17)** — EILIOT instances on your phone, watch, tablet, and desktop share one lifetime memory and one goal stack; whichever device you're near is the current "body."
14. **AAPM Sentinel (16)** — posture-aware protection: raises device hardening automatically when entering hostile environments (conferences, airports, client sites), relaxes at home — with full narration of what it changed and why.

---

## 10. Security, Privacy & Ethics Architecture

- **Authorization Scope (hard gate):** a signed, user-maintained list of owned/authorized targets (domains, CIDRs, devices). The Orchestrator *refuses* offensive tool calls outside scope. Scope changes require biometric confirmation.
- **Data stays home:** all memory encrypted at rest (SQLCipher, Keystore-wrapped keys). LLM egress is the only cloud path; a **redaction filter** strips on-device PII (messages, contacts) from outbound LLM context unless the user explicitly includes it. On Android 16+, the Nano tier processes sensitive content locally so much of it never leaves the device at all.
- **Consent for message intelligence:** Notification/Accessibility access enabled explicitly by the user, for the owner's own device only, with a visible persistent indicator.
- **Full audit log:** every proactive action, message sent, and tool executed is logged and reviewable — the user can always answer "why did you do that?"
- **No covert exfiltration:** the app never ships user data anywhere except the user-configured LLM endpoint and user-configured APIs.
- **Kill switch:** one gesture (or voice phrase) suspends all autonomy instantly.

---

## 11. Development Roadmap

| Phase | Name | Deliverable | Est. |
|---|---|---|---|
| 0 | **Skeleton** | Kotlin app: foreground service, overlay orb (basic anim), settings, LLM gateway with OpenAI-compatible config + streaming chat in HUD | 2–3 wks |
| 1 | **Voice & Memory v1** | STT/TTS loop, wake-tap, SQLCipher episodic memory, context assembler v1, reboot survival | 3 wks |
| 2 | **Perception** | Notification listener (WhatsApp announce + suggested replies, ASK_THEN_SEND), Accessibility reader, UsageStats, situation engine | 3–4 wks |
| 3 | **Proactive brain** | Priority/interruptibility scoring, greeting/idle chatter, rate-limited initiative, persona file | 2–3 wks |
| 4 | **VAPT core** | NetScanner, HTTP Workbench, Web Recon, Crypto Lab, CVE feed matching, Live Updates progress notifications | 4–6 wks |
| 5 | **Learning Engine** | Idle research loop, KB vector store, morning briefing, Canary/API self-study feed | 3 wks |
| 6 | **Platform Symbiosis I (A16)** | Gemini Nano hybrid routing, ML Kit GenAI summarization pipeline, AppFunctions publish + consume | 3 wks |
| 7 | **Ecosystem control** | Automation intents, routines ("Pentest mode"), Termux bridge, desktop Command Center (adaptive UI) | 3 wks |
| 8 | **Platform Symbiosis II (A17)** | Cross-device handoff + Sentinel Mesh, advanced-ranging Foxhunt, Contacts Picker, Wear OS companion | 4 wks |
| 9 | **Polish & Power** | Full arc-core shader suite, Ghost Board, Frida/NetHunter bridges, Report Studio, hardening, XR spike | ongoing |

**MVP definition (fastest path to "wow"):** Phase 0 + voice + Notification-announce + persistent memory. That alone already feels alive.

---

## 12. Open Decisions (for you to weigh in)

1. **Persona lock:** default voice/personality of EILIOT — JARVIS-like British-butler calm, or something more aggressive/street?
2. **Distribution:** Play Store (restricted features) vs direct/sideload (full power) vs both?
3. **Default LLM:** which provider/model should be the out-of-box default (Gemini 1M-class vs GPT-4.1-class vs OpenRouter passthrough)? Nano stays the free local tier regardless.
4. **Always-listening:** include true wake-word always-on mic at launch, or tap-to-talk first?
5. **Root features:** invest early in NetHunter/root bridges, or keep v1 unrooted-first?
6. **Autonomy default:** ship messaging at `ASK_THEN_SEND` (safe) and let users unlock `AUTO_SEND`?
7. **AppFunctions priority:** build §8.A early (Phase 6) so EILIOT is agent-ready as the ecosystem adopts it, or defer until more apps publish functions?

---

*End of design draft v1.1 — renamed to EILIOT, added Platform Symbiosis pillar (§8) covering Android 16/17/XR-native superpowers. Review and tell me what to change, cut, or expand. Next step after approval: Phase 0 skeleton (project scaffolding + overlay orb + LLM gateway).*


---

# PART II — BUILDABLE-NOW EXPANSION (v1.2)

**Added:** 2026-10-28
**Focus:** Features we can implement *today* with current stable Android APIs + the configured cloud LLM, plus the concrete dependency stack to pull them off. Nothing here waits on future OS releases.

---

## 13. New Feature Ideas — Implementable Now

Grouped by the pillar they strengthen. Every item below is achievable with shipping APIs and the OpenAI-compatible LLM gateway.

### 13.1 Presence & Interaction (P1)

| # | Feature | How (today's APIs) |
|---|---|---|
| N1 | **Quick Settings Tile** | A `TileService` tile to summon EILIOT, toggle stealth, or trigger "Pentest mode" from the notification shade — zero overlay needed. |
| N2 | **Bubble conversations** | Android Bubbles API (`Notification.BubbleMetadata`) for the chat HUD — a system-sanctioned floating conversation that survives app switches without `SYSTEM_ALERT_WINDOW` edge cases. |
| N3 | **App Shortcuts + Assistant role** | Dynamic `ShortcutManager` shortcuts ("New engagement", "Scan my WiFi") and register as the device Assistant (`ROLE_ASSISTANT`) so a long-press-home / power-button gesture wakes EILIOT. |
| N4 | **Radial gesture menu** | Draw-anywhere radial command wheel on long-press of the orb — tools one flick away (scan / repeater / note / ask). |
| N5 | **Haptic "morse" language** | Distinct haptic patterns (`VibrationEffect` composition) per alert class so EILIOT can signal silently in meetings — you *feel* "new critical finding" vs "message from a VIP". |
| N6 | **Picture-in-picture HUD** | PiP window for the live thought-ticker / scan progress while you work in another app, as an alternative to the overlay. |
| N7 | **Always-On Display glance** | Push a compact status to AOD via ongoing notification — "EILIOT: watching · 2 findings · learning". |

### 13.2 Proactivity & Conversation (P2)

| # | Feature | How |
|---|---|---|
| N8 | **Daily standup & shutdown ritual** | Scheduled (`WorkManager` + `AlarmManager`) morning briefing and evening recap; conversational, pulls from memory + overnight learning. |
| N9 | **Calendar-aware prep** | Read-only Calendar Provider access → "Your pentest kickoff is in 40 min; I've pre-loaded the Acme scope and opened a fresh engagement log." |
| N10 | **Proactive clipboard intelligence** | When you copy an IP, hash, JWT, URL, CVE-ID, or base64 blob, EILIOT offers context-appropriate action ("That's a JWT — decode it? alg is HS256, want me to test for weak-secret?"). Clipboard access only while EILIOT HUD is foreground (Android 10+ rule). |
| N11 | **Screenshot analysis** | FileObserver on the Screenshots folder → when you grab a screenshot of a target page/error, EILIOT OCRs it (ML Kit Text Recognition) and reasons about it ("That stack trace leaks a Struts version — here's the matching CVE"). |
| N12 | **Smart interruption deferral** | If you're in a call/DND/typing, non-critical thoughts queue into a "when you're free…" digest instead of interrupting. |
| N13 | **Follow-up promises** | EILIOT tracks commitments it or you made ("you said you'd rescan after the patch") and resurfaces them at the right moment. |

### 13.3 Perception (P3)

| # | Feature | How |
|---|---|---|
| N14 | **Unified notification digest** | `NotificationListenerService` aggregates all apps → "While you focused: 6 WhatsApp, 1 from the client (urgent), 2 OTPs (I won't read those aloud)." OTP/2FA auto-redacted. |
| N15 | **Sensitive-content guardrails** | Pattern-detect banking/OTP/health notifications and *never* send them to the cloud LLM — on-device handling only. Privacy as a feature. |
| N16 | **Network change narration** | `ConnectivityManager.NetworkCallback` → "Joined 'CoffeeShop_WiFi' — open network, no captive portal auth, I'd avoid logins here; VPN?" |
| N17 | **New-app / permission watchdog** | `PackageManager` + `BroadcastReceiver` on install events → "You just installed 'FreeVPN' — it requests Accessibility + SMS. That combo is a red flag. Review?" |
| N18 | **Ambient audio scene (opt-in)** | ML Kit / YAMNet on-device sound classification (no transcription) → knows if you're in a meeting, driving, or alone to tune proactivity. Pure on-device, no audio leaves. |
| N19 | **Usage-pattern baseline** | `UsageStatsManager` builds your normal rhythm; deviations become signals ("you never open banking at 3am — was that you?"). |

### 13.4 Memory & Learning (P4/P6)

| # | Feature | How |
|---|---|---|
| N20 | **Knowledge graph view** | Entities (targets, hosts, creds, findings, people) as a navigable graph in the HUD, backed by the semantic store. |
| N21 | **"Remember this" capture** | Share-sheet target + voice command — send any text/URL/image into memory with a tag; becomes recallable context. |
| N22 | **Spaced-repetition recall** | Night-School facts resurface on a Leitner schedule; EILIOT quizzes you to cement new TTPs. |
| N23 | **Source-cited knowledge** | Every learned fact stores its source URL + fetch date; EILIOT can always say *where* it learned something and re-verify stale entries. |
| N24 | **Memory timeline & redaction** | Scrollable life-timeline of EILIOT's memory; one-tap forget/redact any episode (GDPR-style right-to-forget on your own assistant). |

### 13.5 VAPT Prowess (P5) — now-buildable additions

| # | Feature | How |
|---|---|---|
| N25 | **mTLS / CA cert workbench** | Inspect/trust custom CAs, run a local intercepting proxy for your *own* traffic analysis (user-installed CA, Android 7+ user-cert caveats documented). |
| N26 | **Nuclei-style template runner** | Pure-Kotlin YAML template engine for HTTP vuln checks against in-scope targets — ship a starter template pack, let the LLM author new templates on the fly. |
| N27 | **GraphQL introspection & abuse** | Detect GraphQL endpoints, run introspection, auto-map the schema, suggest IDOR/auth-bypass test mutations. |
| N28 | **JWT / OAuth lab** | Full decode, alg-confusion (`none`, RS→HS), weak-secret brute (wordlist), `kid`/`jku` injection probes — with clear in-scope gating. |
| N29 | **Android app audit (local APKs)** | Pull installed APKs you're authorized to test → manifest/permission/exported-component analysis, hardcoded-secret grep, cleartext-traffic flags. On-device static triage; heavy decompile offloads to Termux. |
| N30 | **Wordlist & payload manager** | Bundle SecLists-style lists, manage custom wordlists, LLM-generate target-tailored lists ("usernames from this company's email format"). |
| N31 | **Evidence auto-capture** | Every tool action auto-snapshots request/response + timestamp into the engagement log, pre-formatted for the report with redaction applied. |
| N32 | **Recon aggregator** | One command fans out crt.sh + DNS + wayback + tech-fingerprint + port scan, dedupes, and returns a single ranked attack-surface map. |
| N33 | **Scheduled re-scans / diffing** | Periodic in-scope re-scan with change detection — "new subdomain appeared on Acme since Tuesday: staging-api.acme.com (open :8080)". |

### 13.6 Ecosystem & Workflow

| # | Feature | How |
|---|---|---|
| N34 | **Report export pipeline** | Findings → Markdown → styled HTML → PDF (Android `PrintManager`/`PdfDocument`); optional push to the user's own GitHub/Notion/Jira via their API keys. |
| N35 | **Tasker / MacroDroid intents** | Expose EILIOT actions as intents so power users wire it into existing automations. |
| N36 | **Secure API-key vault UI** | First-class manager for all the keys EILIOT uses (LLM, HIBP, Shodan, VirusTotal, GitHub…) — Keystore-encrypted, per-key scopes, usage/cost meters. |
| N37 | **Shodan / VirusTotal / HIBP connectors** | Optional enrichments using the user's own keys — passive recon and IOC checks without active scanning. |
| N38 | **Multi-profile isolation** | Separate encrypted "worlds" (personal vs client-A vs client-B) so engagement data never cross-contaminates; switchable, each with its own scope. |

---

## 14. Expanded Dependency Stack

Concrete libraries to pull in, by subsystem. Versions intentionally left as "latest stable at build time"; pin exact versions in `libs.versions.toml` during Phase 0.

### 14.1 Core / App
```kotlin
// Kotlin + coroutines + lifecycle
org.jetbrains.kotlinx:kotlinx-coroutines-android
androidx.core:core-ktx
androidx.lifecycle:lifecycle-service
androidx.lifecycle:lifecycle-process        // app-wide lifecycle for the agent loop
androidx.work:work-runtime-ktx              // WorkManager watchdog + scheduled jobs
androidx.startup:startup-runtime            // deterministic init of Sentinel Core
```

### 14.2 UI / Overlay / Animation
```kotlin
androidx.compose.bom (platform)             // Compose BOM
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.compose.ui:ui-graphics             // AGSL RuntimeShader for the arc-core orb
androidx.navigation:navigation-compose
androidx.graphics:graphics-shapes           // morphing shapes for the orb states
// Optional heavy animation:
io.github.sceneview:sceneview               // 3D/AR-ready HUD (keeps XR path open)
com.airbnb.android:lottie-compose           // vector micro-animations for HUD chrome
```

### 14.3 Dependency Injection / Architecture
```kotlin
com.google.dagger:hilt-android              // DI across service + UI + tools
androidx.hilt:hilt-work                     // Hilt for WorkManager workers
```

### 14.4 Persistence / Memory
```kotlin
androidx.room:room-runtime
androidx.room:room-ktx
net.zetetic:android-database-sqlcipher      // encrypted SQLite (episodic + KB)
androidx.security:security-crypto           // EncryptedSharedPreferences / Keystore wrap
androidx.datastore:datastore-preferences    // settings/persona config
io.objectbox:objectbox-android              // on-device vector store (semantic recall)
// or: app.cash.sqldelight + sqlite-vec extension as an alternative vector path
```

### 14.5 Networking / LLM Gateway
```kotlin
com.squareup.okhttp3:okhttp                 // HTTP + SSE for streaming completions
com.squareup.okhttp3:okhttp-sse
com.squareup.retrofit2:retrofit             // typed REST for feeds/connectors
com.squareup.retrofit2:converter-kotlinx-serialization
org.jetbrains.kotlinx:kotlinx-serialization-json
com.squareup.okhttp3:logging-interceptor    // debug only, stripped in release
```

### 14.6 On-device AI (optional, capability-gated)
```kotlin
com.google.mlkit:genai-summarization        // Gemini Nano summarize (A16 capable devices)
com.google.mlkit:text-recognition           // OCR for screenshot analysis (N11)
com.google.mlkit:language-id                // route/classify text cheaply
// YAMNet via TensorFlow Lite for ambient audio scene (N18):
org.tensorflow:tensorflow-lite
org.tensorflow:tensorflow-lite-support
```

### 14.7 Voice
```kotlin
// STT/TTS: Android platform SpeechRecognizer + TextToSpeech (no dep)
ai.picovoice:porcupine-android              // wake-word "EILIOT" (or openWakeWord)
// Cloud Whisper/TTS reuse the OkHttp gateway + user's OpenAI-compatible key
```

### 14.8 VAPT / Security tooling
```kotlin
// Networking primitives (NDK for raw sockets where needed)
org.pcap4j:pcap4j-core                       // PCAP model/export for Packet Watch
dnsjava:dnsjava                              // DNS enumeration / resolution
org.bouncycastle:bcprov-jdk18on              // crypto, cert parsing, hash work
com.auth0:java-jwt                           // JWT decode/verify for the JWT lab
org.jsoup:jsoup                              // HTML parsing for web recon / fingerprint
com.charleskorn.kaml:kaml                    // YAML for the nuclei-style template runner (N26)
// VpnService-based capture is platform API (no dep); Termux via intents (no dep)
```

### 14.9 Reporting / Export
```kotlin
org.commonmark:commonmark                    // findings Markdown → HTML
// PDF via Android PdfDocument/PrintManager (platform, no dep)
com.itextpdf:itext7-core                     // richer PDF reports (license-check before ship)
```

### 14.10 Build / Quality / Observability
```kotlin
// Gradle version catalog (libs.versions.toml) for all of the above
com.diffplug.spotless                        // formatting
io.gitlab.arturbosch.detekt                  // static analysis
app.cash.turbine + junit + mockk            // tests (flows, unit)
com.jakewharton.timber:timber                // structured logging to the audit trail
androidx.test.espresso + compose-ui-test     // UI tests for HUD
```

> **Supply-chain note:** pin exact versions, enable Gradle dependency verification (checksums/signatures), and review any security-adjacent library (pcap4j, bouncycastle, picovoice licensing) before shipping. For an app with this much device access, a locked, verified dependency graph is itself a security control.

---

## 15. Updated Roadmap Deltas

These slot into the existing §11 roadmap without reordering it:

- **Phase 0 (+):** add Quick Settings Tile (N1), App Shortcuts + Assistant role (N3), and the secure API-key vault UI (N36) — all cheap, high-visibility wins that make the skeleton feel real immediately.
- **Phase 2 (+):** clipboard intelligence (N10), screenshot OCR analysis (N11), unified notification digest with OTP redaction (N14/N15), network-change narration (N16).
- **Phase 3 (+):** daily standup/shutdown ritual (N8), calendar-aware prep (N9), smart interruption deferral (N12), follow-up promise tracking (N13).
- **Phase 4 (+):** nuclei-style template runner (N26), GraphQL (N27) and JWT/OAuth labs (N28), recon aggregator (N32), evidence auto-capture (N31), report export pipeline (N34).
- **Phase 5 (+):** knowledge-graph view (N20), spaced-repetition recall (N22), source-cited knowledge (N23).
- **Cross-cutting:** multi-profile isolation (N38) and Shodan/VT/HIBP connectors (N37) land alongside Phase 4–5 as the engagement workflow matures.

**New "wow in a weekend" quick-wins (lowest effort, highest demo value):** N1 (tile), N10 (clipboard intelligence), N11 (screenshot analysis), N14 (notification digest). Each is self-contained, uses stable APIs, and showcases the proactive-perception personality before the heavy VAPT engine exists.

---

*End of v1.2 expansion — 26 new implementable-now features (N1–N38 across categories), a full dependency stack by subsystem, and roadmap deltas. Review and tell me which quick-wins to build first; my recommendation is the Phase 0 skeleton plus the four "wow in a weekend" items.*
