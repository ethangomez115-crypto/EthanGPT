# Nexus AI v15.5 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.5
- **Consistent swipe everywhere** — every tab (Chat, Terminal, Code IDE, Web) now
  uses the same swipe sensitivity and behavior as the chat screen: a short swipe
  is enough, with matching edge zones and direction thresholds.
  - Swipe right from the left edge opens the history/menu drawer.
  - Swipe left from the right edge opens the Code IDE.
  - Any other left swipe closes the drawer.

## Features carried over (v15.4)
- Google AI Studio–inspired dark UI with a centered "What can I help with?"
  welcome, tappable suggestion cards, and a cleaner prompt bar.
- Kimi AI–style violet→blue gradient pill buttons across the app.
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
Unzip `Nexus-AI-Desktop-v15.5.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.

## Verify
Android APK SHA-256: `27af822d3f4cb40b3a80e5bc7119fed6b11c1a8f147d6ee5b473f728125a6523`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
