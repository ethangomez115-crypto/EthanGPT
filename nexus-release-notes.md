# Nexus AI v16.0 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v16.0
- **New `/create` skill** — tap `Skills → /create` (or type `/create`), describe
  what you want, and send. Nexus asks the model for a complete single-file HTML
  page, shows the reply in chat, saves it as a project and opens the full-screen
  preview automatically — no extra tap needed.
- The `Preview` button on any code reply still works the same way.
- Everything else is unchanged from v15.9, including the hidden five-tap key
  dialog and the removal of the Online settings screen.

## Features carried over
- Chat-only UI: the `AUTO · online` pill, AI settings, Code IDE, web browser and
  terminal/workspace swipes have all been removed.
- Projects screen — a list of saved sites that open in a full-screen local preview
  with a Share button.
- Model tiers, thinking and context presets: `Nexus 3.1 Pro`, `Nexus 2.8 Preview`
  and `Nexus Instant`, plus `More models`, `Thinking effort` and `Context length`.
- Claude Code-inspired warm dark theme with cream text and terracotta accents.
- Skills menu with slash commands: `/create`, `/review`, `/explain`, `/fix`,
  `/test`, `/refactor`, `/docs`, `/summarize` and `/brainstorm`.
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
Unzip `Nexus-AI-Desktop-v16.0.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.
The desktop build is unchanged from v15.8; the v16.0 ZIP is byte-identical to it.

## Verify
Android APK SHA-256: `9b5e0c9f8ed5fc674e1d7129a9085d62c0b4a83d83a480ee20d9032093e60a7a`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
