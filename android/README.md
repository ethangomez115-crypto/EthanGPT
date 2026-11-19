# EthanGPT Android app (v3) — model built in

`build/app.apk` is the signed **v3** app (`com.buddyapps.ethangpt`, versionCode 3).
**The model is embedded in the app.** There is no Termux, no server and no
internet: the tokenizer and the 10M-parameter N-gram model live in
`assets/model.txt` and run directly in Java on the phone.

## Features
- Chat screen backed by the inbuilt model (works in airplane mode).
- **Web** button hands a search to your browser for harder questions.
- **Info** button shows the real model stats (parameters, vocabulary, order).
- **Clear** button starts a fresh conversation.
- No permissions requested at all; no network calls.

The app is honest about scope: it is a count-based N-gram model, not a neural
transformer, and it says so in the Info panel.

## Install / update
Copy the APK to the phone and tap it. Android asks you to allow installs from
that source; the final Install/Update and fingerprint confirm are yours to tap.
versionCode increased 2 → 3, so tapping installs an **update** and keeps data.

## Build from source (no Android Studio needed)
On a phone with the Buddy android-app skill toolchain:

```bash
# 1. (re)generate the embedded model from the Python project
python3 ../EthanGPT/tools/export_android_model.py assets desktoptest/vectors.txt

# 2. verify the Java port on a desktop JVM (optional but recommended)
javac -encoding UTF-8 -d /tmp/etest java/com/buddyapps/ethangpt/{BPETokenizer,NGramModel,ModelLoader,ChatBot}.java desktoptest/SelfTest.java
java -cp /tmp/etest com.buddyapps.ethangpt.SelfTest assets/model.txt desktoptest/vectors.txt

# 3. build the signed APK
bash ~/.buddy/work/.claude/skills/android-app/build-apk.sh <this-folder>
```

Output: `<this-folder>/build/app.apk`.

## Source layout
```
AndroidManifest.xml                 (no permissions; fully offline)
app.properties                      (versionCode=3, versionName=3.0, minSdk=26)
assets/model.txt                    (embedded tokenizer + N-gram model)
java/com/buddyapps/ethangpt/
    MainActivity.java               (Android UI)
    BPETokenizer.java               (pure-Java tokenizer)
    NGramModel.java                 (pure-Java model + sampling)
    ChatBot.java                    (offline Q/A + history)
    ModelLoader.java                (parses assets/model.txt)
desktoptest/SelfTest.java           (desktop verification, not in APK)
build/app.apk
```
