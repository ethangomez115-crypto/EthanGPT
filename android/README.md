# EthanGPT Android companion app (v2)

`build/app.apk` is the signed **v2** app (`com.buddyapps.ethangpt`, versionCode 2).
It is a real chat front end for the Python model: start the server in Termux
with `python main.py serve`, then this app talks to it over HTTP.

## Features
- Chat screen with a scrollable conversation.
- Mode buttons: **offline** (local 10M model), **opencode**, **ollama**, **browser**.
- **Info** button reads `GET /info` from the server (parameters, vocabulary, mode).
- **Web** button opens the server page in the phone browser.
- **Termux** button jumps back to the terminal.
- Server address field, default `http://127.0.0.1:8080` (app + Termux on one phone).

The offline model needs no internet at all. Browser mode opens a web search;
Open Code / Ollama are free, local AI connectors if you install them.

## Install / update
Copy the APK to the phone and tap it. Android asks you to allow installs from
that source; the final Install/Update and fingerprint confirm are yours to tap.
Because versionCode increased from 1 to 2, tapping installs an **update** and
keeps your data.

## Build from source (no Android Studio needed)
On a phone with the Buddy android-app skill toolchain:

```bash
bash ~/.buddy/work/.claude/skills/android-app/setup-toolchain.sh
bash ~/.buddy/work/.claude/skills/android-app/build-apk.sh <this-folder>
```

Output: `<this-folder>/build/app.apk`.

## Source layout
```
AndroidManifest.xml                 (INTERNET permission, cleartext traffic)
app.properties                      (versionCode=2, versionName=2.0, minSdk=26)
java/com/buddyapps/ethangpt/MainActivity.java
build/app.apk
```
