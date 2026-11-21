# Nexus AI v15.6 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.6
- **Claude Code-inspired warm theme** — a warm charcoal canvas, cream text and
  terracotta accents across Chat, Terminal, Code IDE and Web. Voice-mode orb and
  buttons use the same warm palette.
- **Skills menu with slash commands** — open the drawer to browse real coding
  skills: `/review`, `/explain`, `/fix`, `/test`, `/refactor`, `/docs`,
  `/summarize` and `/brainstorm`. Tapping one loads a full prompt into the chat
  box so you can edit it before sending; typing the command works too.
- **Bottom tabs removed** — navigation is swipe-only, so every workspace gets
  the full screen. Swipe right from the left edge for the menu, left from the
  right edge for the next workspace (Chat → Terminal → Code IDE → Web), and
  right anywhere else for the previous workspace.
- **`+` button now attaches files** — choose any document, image or file and it
  is added to the chat (text is read up to 20,000 characters, binary files by
  size), with the prompt box prefilled so you can ask about it immediately.
  New chat is still available from the menu and the welcome chips.

## Features carried over (v15.5)
- Google AI Studio–inspired welcome with tappable suggestion cards and a clean
  prompt bar.
- Clean, edit-only code editor with file list, syntax highlighting and
  Copy/Preview/Ask Nexus.
- Interactive chat text: select, copy, save to file, share, open in IDE, preview HTML.
- Keyless image generation (save/share/open) and file generation/sharing.
- DeepSeek, OpenRouter, Groq and Gemini online options; free offline model kept.
- Local website preview served on 127.0.0.1; Code IDE connected to chat.
- Chat backup/share, low-RAM support, real WebView browser.
- Swipe sensitivity and behavior unified across all workspaces.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.6.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.
The desktop build is unchanged from v15.5; this v15.6 restyle is Android-only.

## Verify
Android APK SHA-256: `bcf7e17e6922029f76ad7bad05a065e159548709dda9d691f8a5fbc0ae39c4d7`
Desktop ZIP SHA-256: `1b23365651b54d07862b4c2eb347d4e4cbade4b80f87100fc2dbe3b7f911f807`
