#!/usr/bin/env bash
# Compiles app/.../ShomerEngine.kt on a plain JVM (Gradle ships a Kotlin compiler; no Android SDK needed) and checks that it
# gives the same results as the Python engine on the eval corpus plus ~400 generated messages.
# Needs: java, and a Gradle install (GRADLE_LIB, default /opt/gradle-8.14.3/lib). Does NOT test the Android screens.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."; L="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"; W="$(mktemp -d)"
CP="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar"
mkdir -p "$W/out"
javac -d "$W/out" "$HERE"/shim/org/json/*.java
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$L/kotlin-stdlib-2.0.21.jar:$W/out" -d "$W/out" "$ROOT/app/app/src/main/java/il/shomer/ShomerEngine.kt" "$HERE/Main.kt"
python3 "$HERE/gen_inputs.py" "$ROOT" "$W"
java -cp "$L/kotlin-stdlib-2.0.21.jar:$W/out" MainKt "$ROOT/app/app/src/main/assets/directory.json" "$W/in.jsonl" "$W/kt.jsonl"
python3 "$HERE/compare.py" "$W"
