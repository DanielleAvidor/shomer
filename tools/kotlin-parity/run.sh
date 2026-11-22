#!/usr/bin/env bash
# Compiles app/.../ShomerEngine.kt on a plain JVM (Gradle ships a Kotlin compiler; no Android SDK needed) and checks that it
# gives the same results as the Python engine on the eval corpus plus ~400 generated messages.
# Needs: java, and a Gradle install (GRADLE_LIB, default /opt/gradle-8.14.3/lib). Does NOT test the Android screens.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."; L="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"; W="$(mktemp -d)"
first() { ls $L/$1 2>/dev/null | head -1; }   # the jar names carry versions that change with the Gradle release
K=$(first 'kotlin-compiler-embeddable-*.jar'); STD=$(first 'kotlin-stdlib-[0-9]*.jar')
CP="$K:$STD:$(first 'kotlin-script-runtime-*.jar'):$(first 'kotlin-reflect-*.jar'):$(first 'kotlin-daemon-embeddable-*.jar'):$(first 'kotlinx-coroutines-core-jvm-*.jar'):$(first 'trove4j-*.jar'):$(first 'annotations-*.jar')"
mkdir -p "$W/out"
javac -d "$W/out" "$HERE"/shim/org/json/*.java
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD:$W/out" -d "$W/out" "$ROOT/app/app/src/main/java/il/shomer/ShomerEngine.kt" "$HERE/Main.kt"
python3 "$HERE/gen_inputs.py" "$ROOT" "$W"
java -cp "$STD:$W/out" MainKt "$ROOT/app/app/src/main/assets/directory.json" "$W/in.jsonl" "$W/kt.jsonl"
python3 "$HERE/compare.py" "$W"
