"""Smoke test on the disposable Actions emulator. Run only on a fresh test device."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args):
    return subprocess.check_output(["adb", *args])


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/soda-check.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/soda-check.xml"))


def find(label=None, kind=None):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        for node in screen().iter("node"):
            if label is not None and node.get("text") != label:
                continue
            if kind is not None and node.get("class") != kind:
                continue
            return node
        time.sleep(0.5)
    print([(n.get("class"),n.get("text"),n.get("content-desc")) for n in screen().iter("node")], flush=True)
    print(adb("logcat","-d","-b","crash").decode(errors="replace"),flush=True)
    raise AssertionError(f"Missing control: {label or kind}")


def tap(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def shot(name):
    Path(name + ".png").write_bytes(adb("exec-out", "screencap", "-p"))


find("첫 레코드를 올려 보세요")
shot("turntable")
save_button = find("진열장에 저장")
shelf_button = find("진열장")
tap(find("진열장"))
find("내 진열장")
shot("shelf")
tap(find("턴테이블"))
tap(find("링크 열기"))
tap(find(kind="android.widget.EditText"))
adb("shell", "input", "text", "https://music.youtube.com/watch?v=M7lc1UVf-VE")
print("Link entered:", find(kind="android.widget.EditText").get("text"), flush=True)
adb("shell","input","keyevent","KEYCODE_BACK")
tap(find("열기", "android.widget.Button"))
# Streaming WebViews continually update accessibility content. A hierarchy dump
# waits for the entire screen to become idle, which a live player cannot guarantee.
for _ in range(20):
    if b"android.webkit.WebView{" in adb("shell", "dumpsys", "activity", "top"):
        break
    time.sleep(1)
else:
    raise AssertionError("Native WebView did not mount after link entry")
shot("linked-track")
tap(save_button)
time.sleep(1)
tap(shelf_button)
find("삭제")
assert b"kr.sodagroove.player/.DeckWidget" in adb("shell", "dumpsys", "appwidget") or b"kr.sodagroove.player.DeckWidget" in adb("shell", "dumpsys", "appwidget")
print("Installed release APK, launched deck, navigated shelf, opened music link, verified WebView, saved item and found widget provider.")
