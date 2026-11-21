# Nexus AI v15.9 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.9
- **Online settings removed** — the full provider / key / model settings dialog is
  gone. Nothing in the visible UI mentions online settings.
- **Hidden key access via five taps** — tap the `Nexus AI` title at the top of the
  drawer five times quickly to open a minimal `Key` dialog. The field starts empty,
  so your existing key is never shown on screen. `Cancel` leaves it untouched;
  `Save` stores it only on this device.
- **Model choices stay in the Model screen** — `Nexus 3.1 Pro`, `Nexus 2.8 Preview`
  and `Nexus Instant`, plus `More models`, `Thinking effort` and `Context length`.
- Everything else is unchanged from v15.8.

## Features carried over
- Chat-only UI: the `AUTO · online` pill, AI settings, Code IDE, web browser and
  terminal/workspace swipes have all been removed.
- Projects screen — tap `Projects` in the quick chips (or the drawer) to see a list
  of saved sites; tap one to open a full-screen local preview with a Share button.
  `Preview` on an HTML/code reply saves it as a new project.
- Model tiers, thinking and context presets map to real provider settings.
- Claude Code-inspired warm dark theme with cream text and terracotta accents.
- Skills menu with slash commands: `/review`, `/explain`, `/fix`, `/test`,
  `/refactor`, `/docs`, `/summarize` and `/brainstorm`.
- `+` button attaches any file to the chat, with the prompt box prefilled.
- Interactive chat text: copy, save, share and preview.
- Keyless image generation and file generation/sharing.
- DeepSeek, OpenRouter, Groq and Gemini online options; free offline model kept.
- Loopback-only local preview on 127.0.0.1 — nothing is exposed to the network.
- Chat backup/share and automatic low-RAM support.
- Half-width drawer that keeps the conversation visible beside it.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.9.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.
The desktop build is unchanged from v15.8; the v15.9 ZIP is byte-identical to it.

## Verify
Android APK SHA-256: `732362dcae0e089f9a56433e8dae8a9b50c7bf21c26c1bf42fb603b155ff7140`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
