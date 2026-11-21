# Nexus AI v15.8 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.8
- **Cleaner top bar** — the `AUTO · online` pill and the AI settings menu entry are
  gone. The top bar now has just the menu and sound buttons.
- **Code IDE and web browser removed** — chat is the only screen. Generated sites
  are saved as projects and previewed on their own screen.
- **Projects screen** — tap `Projects` in the quick chips (or the drawer) to see a
  list of saved sites; tap one to open a full-screen preview with a Share button.
  `Preview` on an HTML/code reply saves it as a new project.
- **Model tiers, thinking and context** — a `Model` chip opens the new model screen:
  `Nexus 3.1 Pro`, `Nexus 2.8 Preview` and `Nexus Instant`, plus `More models`,
  `Thinking effort` (Low / Medium / High) and `Context length` (Short / Standard /
  Long). These presets map to the provider's models and really change reasoning
  effort, reply length and how much chat history is sent.
- **Hidden key access** — long-press the drawer title to open provider / API key
  settings. No visible settings button, and no key is ever bundled.
- **Half-width drawer** — the swipe-out menu still opens to half the screen so the
  conversation stays visible beside it.

## Features carried over
- Claude Code-inspired warm dark theme with cream text and terracotta accents.
- Skills menu with slash commands: `/review`, `/explain`, `/fix`, `/test`,
  `/refactor`, `/docs`, `/summarize` and `/brainstorm`.
- `+` button attaches any file to the chat, with the prompt box prefilled.
- Interactive chat text: copy, save, share and preview.
- Keyless image generation and file generation/sharing.
- DeepSeek, OpenRouter, Groq and Gemini online options; free offline model kept.
- Loopback-only local preview on 127.0.0.1 — nothing is exposed to the network.
- Chat backup/share and automatic low-RAM support.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.8.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.
The desktop build is unchanged from v15.5; recent restyles are Android-only.

## Verify
Android APK SHA-256: `80c1830975fb8798d754785a5c24f0e19edfb6e10a8ce4397bceddc1a7061042`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
