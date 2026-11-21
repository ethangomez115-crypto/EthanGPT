# Nexus AI v15.3 — Free Public Release

Nexus AI is an original, privacy-first AI assistant for Android 8.0+ and desktop
(Windows / Linux / macOS via Java). Free to download and use, forever.

## What's new in v15.3
- **Swipe to the Code IDE** — swipe in from the right edge on any tab to reveal the
  Code IDE instantly (left edge still opens the chat/menu).
- **Clean code editor** — the Code IDE is now focused purely on viewing and editing
  code, like Google AI Studio: file list, syntax highlighting, Copy / Preview / Ask
  Nexus. The old run console was removed (script execution lives in the Terminal tab).

## Features carried over (v15.2)
- **Interactive chat text** — every assistant reply is selectable and has quick
  actions: Copy, Save file, Share, Open in IDE (for code) and Preview (for HTML).
- **Image generation** — create an image from a text prompt with no key needed,
  then Save, Share or Open it. Images go to `Download/NexusAI/Images`.
- **File generation & sharing** — save any reply as a `.txt` in `Download/NexusAI/Files`
  and share files/images to WhatsApp, Gmail, Drive or any app.
- **DeepSeek online option** alongside OpenRouter, Groq and Gemini. The free,
  on-device offline model is kept — add a key only if you want stronger answers.
- **Local website preview** — the Code IDE's "Preview site" button serves your HTML
  from a loopback-only server (127.0.0.1) and shows it in the Web tab / system browser.
- **Code IDE connected to chat** — "Ask Nexus" drops code into the chat input, and a
  "Code IDE" chip in chat opens the editor.
- **Settings dialog** in the drawer to pick a provider, paste your own key and set a model.

## Features carried over
- **Chat backup & share** — chats auto-backup to `Download/NexusAI/NexusAI-Chats-Backup.json`,
  survive uninstall/reinstall, and can be shared to WhatsApp / Gmail / Drive.
- **Low-RAM support** — on phones under 8 GB RAM the heavy offline model is skipped.
- **Real WebView browser** with "Open in browser" and "Share link".
- **Terminal & Code IDE tabs**.
- **Cross-platform desktop app** using the same portable AI core.

## Privacy
No API keys are bundled in the Android APK or desktop JAR. No telemetry, analytics
or ads. Your key (if you add one) is stored only on your device and sent only to
the provider you choose. Image generation uses a free public endpoint and needs
an internet connection.

## Desktop app
Unzip `Nexus-AI-Desktop-v15.3.zip`, then run `java -jar nexus-ai.jar`
(or `nexus-ai.sh` on Linux/macOS, `nexus-ai.bat` on Windows). Java 11+ required.

## Verify
Android APK SHA-256: `21039f382fe9a3f49f2dcc060720ac6d818d242baf11d24105f71a3336c9b850`
Desktop ZIP SHA-256: `644fad44841f1ff48dc5a63e3a162273f5896eb65112780cf44c206ebe2597e3`
