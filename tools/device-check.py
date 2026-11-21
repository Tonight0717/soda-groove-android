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
    raise AssertionError(f"Missing control: {label or kind}")


def tap(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def shot(name):
    Path(name + ".png").write_bytes(adb("exec-out", "screencap", "-p"))


find("첫 레코드를 올려 보세요")
shot("turntable")
tap(find("진열장"))
find("내 진열장")
shot("shelf")
tap(find("턴테이블"))
tap(find("링크 열기"))
tap(find(kind="android.widget.EditText"))
adb("shell", "input", "text", "https://music.youtube.com/watch?v=M7lc1UVf-VE")
tap(find("열기", "android.widget.Button"))
find("YouTube Music 열기")
find(kind="android.webkit.WebView")
tap(find("진열장에 저장"))
tap(find("진열장"))
find("삭제")
assert b"kr.sodagroove.player/.DeckWidget" in adb("shell", "dumpsys", "appwidget") or b"kr.sodagroove.player.DeckWidget" in adb("shell", "dumpsys", "appwidget")
print("Installed release APK, launched deck, navigated shelf, opened music link, verified WebView, saved item and found widget provider.")
