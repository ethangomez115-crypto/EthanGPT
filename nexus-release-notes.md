# Nexus AI v15.7 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.7
- **AI settings entry removed** — the drawer no longer has an "AI settings"
  section. Tap the `AUTO · online` pill in the top bar to change provider, API key
  or model instead.
- **Half-width drawer** — the swipe-out menu now opens to half the screen, like
  the chat history panel, so your conversation stays visible beside it.

## Features carried over (v15.6)
- Claude Code-inspired warm dark theme with cream text and terracotta accents.
- Skills menu with slash commands: `/review`, `/explain`, `/fix`, `/test`,
  `/refactor`, `/docs`, `/summarize` and `/brainstorm`. Tapping one loads a real
  prompt into the chat box; typing the command works too.
- Bottom tabs removed — navigation is swipe-only (left edge: menu; left from the
  right edge: next workspace; right swipe: previous workspace).
- `+` button attaches any file to the chat, with the prompt box prefilled.
- Interactive chat text: select, copy, save, share, open in IDE, preview HTML.
- Keyless image generation and file generation/sharing.
- DeepSeek, OpenRouter, Groq and Gemini online options; free offline model kept.
- Local website preview on 127.0.0.1; Code IDE connected to chat.
- Chat backup/share, low-RAM support, real WebView browser.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.7.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.
The desktop build is unchanged from v15.5; recent restyles are Android-only.

## Verify
Android APK SHA-256: `2fa9d935f7ea2a7be45ef03cc82ddd9c74c3ce7cbc298d47c06647567cc94cd0`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
