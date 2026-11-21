#!/bin/sh
# Nexus AI desktop launcher for Linux / macOS.
# Requires Java 11 or newer. Put nexus-ai.jar next to this script.
DIR=$(cd "$(dirname "$0")" && pwd)
exec java -jar "$DIR/nexus-ai.jar" "$@"
