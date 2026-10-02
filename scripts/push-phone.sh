#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# push-phone.sh: Build and push the Bike Companion debug app to a connected phone
# ---------------------------------------------------------------------------

PACKAGE="com.valpr.bikecompanion"
ACTIVITY=".MainActivity"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

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
            echo "  -f, --force       Force install even if target reports as a watch"
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

# 2. Check for connected devices
CONNECTED_DEVICES=($("${ADB_BIN}" devices | awk 'NR>1 && $2=="device" {print $1}'))

if [ ${#CONNECTED_DEVICES[@]} -eq 0 ]; then
    echo "❌ Error: No Android devices detected via adb."
    echo "Please connect your phone with USB Debugging enabled."
    exit 1
fi

TARGET_SERIAL=""

if [ -n "${EXPLICIT_SERIAL}" ]; then
    TARGET_SERIAL="${EXPLICIT_SERIAL}"
elif [ -n "${SERIAL:-}" ]; then
    TARGET_SERIAL="${SERIAL}"
else
    PHONE_DEVICES=()
    for dev in "${CONNECTED_DEVICES[@]}"; do
        if ! is_watch "${dev}"; then
            PHONE_DEVICES+=("${dev}")
        fi
    done

    if [ ${#PHONE_DEVICES[@]} -eq 1 ]; then
        TARGET_SERIAL="${PHONE_DEVICES[0]}"
    elif [ ${#PHONE_DEVICES[@]} -gt 1 ]; then
        TARGET_SERIAL="${PHONE_DEVICES[0]}"
        echo "ℹ️ Multiple phones detected: ${PHONE_DEVICES[*]} (using ${TARGET_SERIAL}; use -s <serial> to choose another)"
    else
        echo "⚠️ All connected devices report as Wear OS watches."
        echo "Connected devices: ${CONNECTED_DEVICES[*]}"
        if [ "${FORCE_INSTALL}" = true ]; then
            TARGET_SERIAL="${CONNECTED_DEVICES[0]}"
            echo "⚠️ Proceeding due to --force flag. Target: ${TARGET_SERIAL}"
        else
            echo "Use scripts/push-watch.sh to target a watch, or pass --force (or -f) to install the phone app anyway."
            exit 1
        fi
    fi
fi

TARGET_DEVICE_ARG="-s ${TARGET_SERIAL}"
echo "📱 Target phone: ${TARGET_SERIAL}"

# 3. Build & Install via Gradle
echo "🚀 Building and installing :app:installDebug..."
export ANDROID_SERIAL="${TARGET_SERIAL}"
./gradlew :app:installDebug

# 4. Launch the application
echo "▶️ Launching ${PACKAGE}${ACTIVITY}..."
"${ADB_BIN}" ${TARGET_DEVICE_ARG} shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null

# 5. Optional logcat stream if -l or --log is passed
if [ "${STREAM_LOGS}" = true ]; then
    echo "📋 Streaming phone logcat (filter: ${PACKAGE})... Press Ctrl+C to stop."
    "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat -c
    "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat --pid="$("${ADB_BIN}" ${TARGET_DEVICE_ARG} shell pidof -s "${PACKAGE}")" 2>/dev/null || \
        "${ADB_BIN}" ${TARGET_DEVICE_ARG} logcat -v time | grep -i "${PACKAGE}"
fi

echo "✅ Done!"
