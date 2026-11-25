#!/usr/bin/env bash
# Builds the guard jar against the Minecraft 26.2 and Fabric Loader jars Loom already downloaded (26.x isn't obfuscated).
set -euo pipefail
cd "$(dirname "$0")"
JDK="${JAVA_HOME:-$HOME/.jdks/jdk-25.0.4.1+1}"
MC="$HOME/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar"
BRIG="$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.mojang/brigadier" -name "brigadier-*.jar" ! -name "*sources*" | head -1)"
LOADER="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-loader" -name 'fabric-loader-0.19.5.jar' | head -1)"
rm -rf build && mkdir -p build/classes
"$JDK/bin/javac" --release 21 -nowarn -cp "$MC:$LOADER:$BRIG" -d build/classes $(find src -name '*.java')
cp fabric.mod.json build/classes/
(cd build/classes && "$JDK/bin/jar" --create --file ../PolyPlus-Swylow-Guard-1.0.0.jar .)
echo "built build/PolyPlus-Swylow-Guard-1.0.0.jar"
