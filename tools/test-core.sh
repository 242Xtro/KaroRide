#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
task_java_home=${JAVA_HOME:-"$PWD/.toolchain/jdk"}
if [ ! -x "$task_java_home/bin/javac" ]; then
    echo 'JDK 17 fehlt. Noch keine JVM-Simulation ausgeführt; siehe docs/BUILD.md.' >&2
    exit 2
fi
mkdir -p core/build/manual-tests
task_test_dir=$(mktemp -d "$PWD/core/build/manual-tests/run.XXXXXX")
# Build outputs are kept for inspection, no source or user files are deleted.
find core/src/main/java core/src/test/java -name '*.java' -print > "$task_test_dir/sources.txt"
"$task_java_home/bin/javac" --release 17 -d "$task_test_dir/classes" @"$task_test_dir/sources.txt"
"$task_java_home/bin/java" -cp "$task_test_dir/classes" de.markus.aceride.core.ControllerTest
printf 'Testklassen: %s\n' "$task_test_dir/classes"

