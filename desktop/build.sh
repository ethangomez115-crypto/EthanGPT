#!/bin/bash
# Build the cross-platform Nexus AI desktop JAR (Windows / Linux / macOS).
# Requires only a JDK (javac + jar). No Gradle, no Maven, no network at build time.
set -euo pipefail
P=$(cd "$(dirname "$0")" && pwd)
JSON_JAR=${JSON_JAR:-/tmp/json-20240303.jar}
OUT=$P/build

[ -f "$JSON_JAR" ] || { echo "org.json jar missing at $JSON_JAR" >&2; exit 1; }
[ -f "$P/assets/model.txt" ] || { echo "model.txt missing" >&2; exit 1; }
command -v javac >/dev/null || { echo "javac missing" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/jar"

echo "1/4 Compile"
javac --release 11 -encoding UTF-8 -cp "$JSON_JAR" \
  -d "$OUT/classes" $(find "$P/java" -name '*.java')

echo "2/4 Bundle dependencies"
cd "$OUT/classes"
jar xf "$JSON_JAR"
cd "$P"

echo "3/4 Bundle assets"
cp "$P/assets/model.txt" "$OUT/classes/model.txt"

echo "4/4 Package JAR"
cat > "$OUT/manifest.txt" <<'EOF'
Manifest-Version: 1.0
Main-Class: com.buddyapps.ethangpt.DesktopApp

EOF
jar cfm "$OUT/nexus-ai.jar" "$OUT/manifest.txt" -C "$OUT/classes" .

echo "Built $OUT/nexus-ai.jar"
ls -lh "$OUT/nexus-ai.jar"
