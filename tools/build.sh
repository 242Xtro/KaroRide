#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-"$PWD/.toolchain/jdk"}
export ANDROID_HOME=${ANDROID_HOME:-"$PWD/.toolchain/android-sdk"}
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.toolchain/android-user"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$PWD/.toolchain/tmp" "$ANDROID_USER_HOME" "$PWD/.toolchain/java-prefs"
export TMPDIR="$PWD/.toolchain/tmp"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR -Djava.util.prefs.userRoot=$PWD/.toolchain/java-prefs -Duser.home=$PWD/.toolchain/user-home"
task_gradle="$PWD/.toolchain/gradle-8.13/bin/gradle"
if [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -x "$task_gradle" ] || [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
    echo 'Build-Werkzeuge fehlen. Keine Installation ausgelöst. Siehe docs/BUILD.md.' >&2
    exit 2
fi
if [ "$#" -eq 0 ]; then set -- :core:simulationTest; fi
exec "$task_gradle" --no-daemon --console=plain "$@"
