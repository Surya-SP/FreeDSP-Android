#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
if [ "$(uname -s)" = Darwin ]; then
    # macOS lacks linux/usbdevice_fs.h; use the pinned NDK's Linux UAPI headers only as a fallback include path.
    : "${ANDROID_HOME:?Set ANDROID_HOME to the SDK containing ndk/27.0.12077973}"
    sysroot="$ANDROID_HOME/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include"
    cc -dynamiclib -fPIC -Wall -Wextra -Werror -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
        -idirafter "$sysroot" -idirafter "$sysroot/aarch64-linux-android" \
        "$project_dir/app/src/main/cpp/access.c" -o "$test_dir/libfreedsp_access.so"
else
    cc -shared -fPIC -Wall -Wextra -Werror -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" "$project_dir/app/src/main/cpp/access.c" -o "$test_dir/libfreedsp_access.so"
fi
"$JAVA_HOME/bin/javac" -d "$test_dir" "$project_dir/native-tests/NativeHidAccess.java"
"$JAVA_HOME/bin/java" -cp "$test_dir" com.wellmosphere.freedsp.cable.NativeHidAccess "$test_dir/libfreedsp_access.so"
