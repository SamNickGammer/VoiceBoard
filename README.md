# VoiceBoard

> A floating mic that lives over any Android keyboard. Tap, speak, get an intent-aware message back in the field you're typing in — in your script (Hindi → Hinglish on an English keyboard, Devanagari on a Hindi keyboard).

VoiceBoard is **not** a custom keyboard and **not** a transcription tool. It's a floating overlay that sits on top of whatever keyboard you already use, with an LLM in the loop that resolves self-corrections, drops filler, and produces the message you actually meant to send.

```
"let's connect at 4, no no let's connect at 5"   →   Let's connect at 5.
"send him 200 rupees wait 250"                   →   Send him 250 rupees.
"kal milte hain 4 baje nahi nahi 5 baje"         →   Kal milte hain 5 baje.   (English keyboard)
                                                 →   कल मिलते हैं 5 बजे।      (Hindi keyboard)
```

## How it works

```
┌──────────────────┐    focus / IME    ┌────────────────────────┐
│  Accessibility   │ ────────────────▶ │  OverlayService (fg)   │
│     service      │                   │  └─ floating pill view │
└──────────────────┘ ◀───────────────  │  └─ MediaRecorder/PCM  │
        ▲    inject text via           └────────┬───────────────┘
        │    ACTION_SET_TEXT                    │
        │                                       ▼
        │                              ┌─────────────────────┐
        │                              │  TranscriptionRouter│
        │                              │   ├─ GroqEngine     │  Whisper-large-v3-turbo
        │                              │   └─ LocalWhisper   │  whisper.cpp via JNI
        │                              └────────┬────────────┘
        │                                       ▼
        │                              ┌─────────────────────┐
        └───────────────────────────── │   ModeProcessor     │  llama-3.3-70b on Groq
                  final text           │   (intent recovery) │
                                       └─────────────────────┘
```

- **Accessibility service** tracks the focused editable node and whether the soft keyboard is visible. The pill is shown only when both are true.
- **Foreground service** owns the `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`), the `AudioRecord` capture, and the transcribe → process → inject pipeline.
- **Transcription engines** are pluggable: free **Groq Whisper** (online, ~1 s) or **on-device whisper.cpp** (offline, model downloaded into the app). The same single Groq key powers both Whisper and the post-processing LLM.
- **Mode LLM** runs *after* transcription. It's not asked to "clean up" — it's asked to figure out what the user meant, apply self-corrections, drop filler, and emit the final message. Three modes:
  - `default` — keep the user's voice, just resolve corrections + drop noise
  - `formal` — same recovery, then polished tone
  - `generate` — treats the speech as a brief, writes the actual message
- **Mode picker lives in the notification shade** so a slow drag of the pill doesn't accidentally pop a menu.

## Features

- 🎤 Floating dictation pill that shows only when a keyboard is up
- 🧠 LLM-powered intent recovery (self-corrections, filler removal)
- 🌐 Hindi / Hinglish / English routing driven by the active IME locale
- 🔌 Two transcription backends: Groq Whisper (cloud) or whisper.cpp (local)
- 📦 In-app model downloader with live progress console
- 🔔 Mode switcher in the notification shade — no on-screen menu clutter
- 🪟 Pill is draggable; position persists; foreground service survives reboots
- 🔒 API key stored in Android `EncryptedSharedPreferences`

## Setup (run on a device)

```bash
git clone https://github.com/SamNickGammer/VoiceBoard.git
cd VoiceBoard
npm install

# Plug in an Android device (real device recommended — emulators don't do IME or mic well)
adb devices
npx react-native run-android
```

The first build downloads + compiles `whisper.cpp v1.7.4` via CMake `FetchContent`. Expect ~10 minutes on a clean build; subsequent builds are fast.

### On the phone, in order:

1. Grant **Display over other apps**.
2. Enable the **VoiceBoard accessibility service** in Android Settings (the warning dialog is expected — VoiceBoard only writes back the text *you* dictated, it doesn't read your typing).
3. Grant the **microphone** permission.
4. Get a free Groq API key from [console.groq.com](https://console.groq.com) → paste it in Settings → tap **Test**.
5. *(Optional)* Settings → Local Whisper models → Download `ggml-base.bin` (~142 MB). Set it active for offline transcription.
6. Home → **Start overlay**. Open any text field in any app. The pill slides in.

## Permissions, explained

| Permission | Why VoiceBoard needs it |
|---|---|
| `RECORD_AUDIO` | Recording what you dictate |
| `SYSTEM_ALERT_WINDOW` | Drawing the floating pill over other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Detecting which text field is focused + writing the result back into it |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keeping the mic + overlay alive when you switch apps |
| `INTERNET` | Calling Groq's API (only used when Groq engine is selected) |
| `POST_NOTIFICATIONS` | Showing the persistent service notification with mode switcher |

The accessibility service **only writes back** dictated text via `ACTION_SET_TEXT` on the focused node. It never reads or stores the contents of what you type.

## Tech stack

- React Native 0.85 (bridgeless, Fabric)
- Kotlin Android — `InputMethodService` (Phase 1, removed), `AccessibilityService`, foreground `Service` + `WindowManager`
- whisper.cpp v1.7.4 via NDK + CMake `FetchContent` + a small JNI wrapper
- Groq for cloud Whisper (`whisper-large-v3-turbo`) + LLM (`llama-3.3-70b-versatile`)
- `EncryptedSharedPreferences` (AndroidX Security) for API keys
- React Navigation native stack on the JS side

## Project layout

```
android/app/src/main/
├── AndroidManifest.xml
├── cpp/
│   ├── CMakeLists.txt          # builds appmodules.so AND voiceboardwhisper.so
│   └── whisper/whisper_jni.cpp # JNI wrapper around whisper.cpp
├── java/com/samnick/voiceboard/
│   ├── MainActivity.kt
│   ├── MainApplication.kt
│   ├── a11y/                   # accessibility service + A11yBus
│   ├── bridge/                 # RN ↔ native module
│   ├── core/                   # PrefsBridge, ModeProcessor, KeyboardLocale
│   ├── overlay/                # OverlayService, OverlayPillView, AudioCapture
│   └── transcription/          # engine interface, GroqEngine, LocalWhisperEngine, downloader
└── res/
    ├── drawable/               # icons + adaptive icon foreground
    ├── mipmap-anydpi-v26/      # adaptive launcher icons
    └── xml/                    # accessibility_service_config.xml
src/
├── components/                 # StatusCard, ConsoleView
├── native/VoiceBoardModule.ts  # typed wrapper around the native module
├── screens/                    # HomeScreen, SettingsScreen
└── utils/                      # storage.ts, prompts.ts
```

## Roadmap

- [ ] iOS port (no overlay equivalent — would need a keyboard extension instead)
- [ ] Voice activity detection so you don't have to tap to stop
- [ ] Dictation history with re-inject
- [ ] More granular language detection (auto-detect per-utterance, not per-keyboard)
- [ ] Streaming partial transcripts onto the pill while you speak

## License

MIT
