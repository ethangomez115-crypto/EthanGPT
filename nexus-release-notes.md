# Nexus AI v15.4 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.4
- **Google AI Studio–inspired interface** — dark, spacious layout with a centered
  "What can I help with?" welcome, a 2×2 grid of tappable suggestion cards
  (Create image, Write code, Explain topic, Summarize text), and a cleaner prompt bar.
- **Kimi AI–style buttons** — violet→blue gradient pills for primary actions
  (send, Save, Copy code, Ask Nexus) and tinted pills with subtle borders for
  secondary ones; chat bubbles and quick chips updated to match.
- Consistent new palette across Chat, Terminal, Code IDE and Web tabs.

## Features carried over (v15.3)
- Swipe in from the right edge on any tab to reveal the Code IDE (left edge opens menu).
- Clean, edit-only code editor with file list, syntax highlighting and Copy/Preview/Ask Nexus.
- Interactive chat text: select, copy, save to file, share, open in IDE, preview HTML.
- Keyless image generation (save/share/open) and file generation/sharing.
- DeepSeek, OpenRouter, Groq and Gemini online options; free offline model kept.
- Local website preview served on 127.0.0.1; Code IDE connected to chat.
- Chat backup/share, low-RAM support, real WebView browser.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.4.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.

## Verify
Android APK SHA-256: `4c10d5352fa1185de420bd167eeaa6ec92f96b9d427d17794fae7560ec6a3fcd`
Desktop ZIP SHA-256: `a8d64b0db4816edf57c8cca3075bd30b8a0fd52ba3a47566147b6aa76d363853`
