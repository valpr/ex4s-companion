#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# push-watch.sh: Build and push the Bike Companion Wear OS debug app to a watch
# ---------------------------------------------------------------------------

PACKAGE="com.valpr.bikecompanion"
ACTIVITY="com.valpr.bikecompanion.wear.MainActivity"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# Parse optional arguments
STREAM_LOGS=false
EXPLICIT_SERIAL=""
FORCE_INSTALL=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -l|--log)
            STREAM_LOGS=true
            shift
            ;;
        -s|--serial)
            EXPLICIT_SERIAL="$2"
            shift 2
            ;;
        -f|--force)
            FORCE_INSTALL=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -l, --log         Follow logcat output after launching"
            echo "  -s, --serial <id> Target specific device serial/IP:port"
            echo "  -f, --force       Force install even if target does not report as a watch"
            echo "  -h, --help        Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Run '$0 --help' for usage."
            exit 1
            ;;
    esac
done

# 1. Resolve ADB location
find_adb() {
    if command -v adb >/dev/null 2>&1; then
        echo "adb"
        return
    fi

    if [ -f "local.properties" ]; then
        SDK_DIR=$(grep -E '^\s*sdk\.dir=' local.properties | cut -d'=' -f2- | tr -d '\r' || true)
        if [ -n "${SDK_DIR}" ] && [ -x "${SDK_DIR}/platform-tools/adb" ]; then
            echo "${SDK_DIR}/platform-tools/adb"
            return
        fi
    fi

    if [ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]; then
        echo "${HOME}/Library/Android/sdk/platform-tools/adb"
        return
    fi

    if [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME}/platform-tools/adb" ]; then
        echo "${ANDROID_HOME}/platform-tools/adb"
        return
    fi

    echo ""
}

ADB_BIN="$(find_adb)"
if [ -z "${ADB_BIN}" ]; then
    echo "❌ Error: adb binary not found in PATH, local.properties, or standard SDK paths."
    exit 1
fi

is_watch() {
    local dev="$1"
    local chars
    chars=$("${ADB_BIN}" -s "${dev}" shell getprop ro.build.characteristics 2>/dev/null || true)
    if echo "${chars}" | grep -q "watch"; then
        return 0
    fi
    if "${ADB_BIN}" -s "${dev}" shell pm list features 2>/dev/null | grep -q "android.hardware.type.watch"; then
        return 0
    fi
    return 1
}

# 2. Discover connected devices
CONNECTED_DEVICES=($("${ADB_BIN}" devices | awk 'NR>1 && $2=="device" {print $1}'))

if [ ${#CONNECTED_DEVICES[@]} -eq 0 ]; then
    echo "❌ Error: No Android devices detected via adb."
    echo ""
    echo "To connect your Wear OS watch over Wi-Fi:"
    echo "  1. On your watch: Settings -> Developer Options -> Wireless debugging (or ADB debugging over Wi-Fi)"
    echo "  2. Run: ${ADB_BIN} connect <watch-ip>:<port>"
    exit 1
fi

TARGET_SERIAL=""

if [ -n "${EXPLICIT_SERIAL}" ]; then
    TARGET_SERIAL="${EXPLICIT_SERIAL}"
elif [ -n "${SERIAL:-}" ]; then
    TARGET_SERIAL="${SERIAL}"
else
    WATCH_DEVICES=()
    for dev in "${CONNECTED_DEVICES[@]}"; do
        if is_watch "${dev}"; then
            WATCH_DEVICES+=("${dev}")
        fi
    done

    if [ ${#WATCH_DEVICES[@]} -eq 1 ]; then
        TARGET_SERIAL="${WATCH_DEVICES[0]}"
    elif [ ${#WATCH_DEVICES[@]} -gt 1 ]; then
        TARGET_SERIAL="${WATCH_DEVICES[0]}"
        echo "ℹ️ Multiple Wear OS watches detected: ${WATCH_DEVICES[*]} (using ${TARGET_SERIAL}; use -s <serial> to choose another)"
    else
        # No watch characteristics found
        echo "⚠️ No connected device identified as a Wear OS watch."
        echo "Connected devices: ${CONNECTED_DEVICES[*]}"
        echo ""
        echo "Note: Phone and Watch share the same package name ('${PACKAGE}')."
        echo "Installing the Wear APK on your phone will overwrite the phone app."
        echo ""
        if [ "${FORCE_INSTALL}" = true ]; then
            TARGET_SERIAL="${CONNECTED_DEVICES[0]}"
            echo "⚠️ Proceeding due to --force flag. Target: ${TARGET_SERIAL}"
        else
            echo "If your watch is on Wi-Fi, connect it first:"
            echo "  ${ADB_BIN} connect <watch-ip>:<port>"
            echo ""
            echo "Or use --force (or -f) if you are certain ${CONNECTED_DEVICES[0]} is your watch."
            exit 1
        fi
    fi
fi

TARGET_DEVICE_ARG="-s ${TARGET_SERIAL}"
echo "⌚ Target watch: ${TARGET_SERIAL}"

# 3. Build & Install via Gradle targeting the selected device
echo "🚀 Building and installing :wear:installDebug..."
export ANDROID_SERIAL="${TARGET_SERIAL}"
./gradlew :wear:installDebug

# 4. Launch the application
echo "▶️ Launching ${PACKAGE}/${ACTIVITY}..."
"${ADB_BIN}" ${TARGET_DEVICE_ARG} shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null

# 5. Optional logcat stream if -l or --log is requested
if [ "${STREAM_LOGS}" = true ]; then
    echo "📋 Streaming watch logcat (filter: ${PACKAGE})... Press Ctrl+C to stop."
    "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat -c
    "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat --pid="$("${ADB_BIN}" ${TARGET_DEVICE_ARG} shell pidof -s "${PACKAGE}")" 2>/dev/null || \
        "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat -v time | grep -i "${PACKAGE}"
fi

echo "✅ Done!"
