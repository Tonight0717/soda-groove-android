#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
sudo chmod a+rw /dev/kvm
sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends libpulse0 libnss3 libxkbcommon-x11-0 libxcb-cursor0 libxcomposite1 libglu1-mesa
export ANDROID_AVD_HOME="$RUNNER_TEMP/soda-avds"
mkdir -p "$ANDROID_AVD_HOME"
echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -f -n SodaCheck -p "$ANDROID_AVD_HOME/SodaCheck.avd" -k 'system-images;android-30;google_apis;x86_64'
adb start-server
# Keep the virtual phone small enough for a standard hosted runner.
echo 'disk.dataPartition.size=1G' >> "$ANDROID_AVD_HOME/SodaCheck.avd/config.ini"
"$ANDROID_HOME/emulator/emulator" -avd SodaCheck -no-window -no-audio -no-boot-anim -no-snapshot -gpu software -feature -Vulkan -memory 1600 -partition-size 1024 > "$RUNNER_TEMP/emulator.log" 2>&1 &
emulator_pid=$!
trap 'tail -60 "$RUNNER_TEMP/emulator.log"; timeout 5 adb emu kill || true' EXIT
for step in $(seq 1 100); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then echo 'Emulator exited before boot'; exit 1; fi
  if [ "$(timeout 3 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = 1 ]; then break; fi
  sleep 3
done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1
curl -fsSL https://github.com/Tonight0717/soda-groove-android/releases/download/v1.0/SodaGroove.apk -o "$RUNNER_TEMP/SodaGroove.apk"
adb install "$RUNNER_TEMP/SodaGroove.apk"
adb shell wm size 780x1688
adb shell wm density 320
adb shell input keyevent 82
adb shell am start -W -n kr.sodagroove.player/.DeckActivity
python3 tools/device-check.py
