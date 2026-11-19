# EthanGPT Android companion app

`build/app.apk` is the signed Stage 1 app. It is a real chat-style interface, but
it deliberately does **not** pretend to be a trained model: the transformer is
still being built in the Python stages. It shows project status and opens Termux
so you can run the Python checks.

## Package
`com.buddyapps.ethangpt` — version 1.0 (versionCode 1), minSdk 26.

## Build from source (no Android Studio needed)
On a phone with the Buddy android-app skill toolchain:

```bash
bash ~/.buddy/work/.claude/skills/android-app/setup-toolchain.sh
bash ~/.buddy/work/.claude/skills/android-app/build-apk.sh <this-folder>
```

Output: `<this-folder>/build/app.apk`.

## Install
Copy the APK to the phone and tap it. Android will ask you to allow installs from
the source you used; the final Install/Confirm taps must be done by you.

## Source layout
```
AndroidManifest.xml
app.properties
java/com/buddyapps/ethangpt/MainActivity.java
build/app.apk
```
