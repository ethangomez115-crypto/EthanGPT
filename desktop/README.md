# Nexus AI Desktop

A cross-platform desktop front-end (Windows / Linux / macOS) for the same
portable AI core used by the Android app. No account, no backend, no internet
required for the offline model.

## What it does
- **Chat** — offline built-in model (no key), or online via your own free API
  key (OpenRouter / Groq / Gemini).
- **Terminal** — local shell with this app's permissions (no root).
- **Code IDE** — editor with a shell-script runner and "Ask Nexus".
- **Web** — opens pages in your system browser.
- **Backup / Restore / Share chats** — JSON file, portable with the Android app.
- Settings (provider, model, key) saved locally in `~/.nexusai/`.

## Run
Requires **Java 11+** (Java 17 recommended).

- Windows: double-click `nexus-ai.bat`
- Linux/macOS: `sh nexus-ai.sh` (make executable with `chmod +x`)

Or run directly: `java -jar nexus-ai.jar`

## Privacy
- No telemetry, no analytics, no bundled API keys.
- API keys are stored only in `~/.nexusai/config.properties`.
- Chats are stored only in `~/.nexusai/chats.json`.

## Build from source
`bash build.sh` (needs `javac` and `org.json`; downloads are not part of the build).
