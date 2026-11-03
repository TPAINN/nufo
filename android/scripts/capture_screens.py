"""Walk the app's main states on a device and save one screenshot per state, for before/after comparisons.

Usage: python -I android/scripts/capture_screens.py <serial> <out_dir> [apk]
Same taps every run: welcome, home, search (Greek query), product (score card, nutrition), history, settings,
and the same screens at 200 % font scale and in dark mode.
"""
import re, subprocess, sys, time
from pathlib import Path

SERIAL, OUT = sys.argv[1], Path(sys.argv[2])
APK = sys.argv[3] if len(sys.argv) > 3 else None
PKG = "com.nufo.app"


def adb(*args: str, binary: bool = False):
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name: str) -> None:
    time.sleep(1.2)  # let springs settle
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    print("saved", name)


def nodes() -> list:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    found = []
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        text = re.search(r'text="([^"]*)"', n).group(1)
        desc = re.search(r'content-desc="([^"]*)"', n).group(1)
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        found.append((text, desc, (x1 + x2) // 2, (y1 + y2) // 2))
    return found


def tap(*labels: str, timeout: float = 15) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        for text, desc, x, y in nodes():
            if any(l.lower() in (text + " " + desc).lower() for l in labels):
                adb("shell", "input", "tap", str(x), str(y))
                return True
        time.sleep(1)
    print("not found:", labels)
    return False


def walk(prefix: str) -> None:
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    shot(f"{prefix}01-welcome")
    tap("Get started", "Ξεκινήστε", "Ξεκίνα")
    shot(f"{prefix}02-home")
    tap("Search", "Αναζήτηση")
    time.sleep(1)
    adb("shell", "input", "text", "yogurt")
    time.sleep(4)
    shot(f"{prefix}03-search")
    adb("shell", "input", "keyevent", "111")  # hide keyboard
    rows = [n for n in nodes() if "kcal" in (n[0] + n[1]).lower()]
    if rows:
        adb("shell", "input", "tap", str(rows[0][2]), str(rows[0][3]))
        time.sleep(4)
        shot(f"{prefix}04-product")
        if tap("How is this calculated", "Πώς υπολογίζεται", timeout=4):
            shot(f"{prefix}06-score-method")
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
        adb("shell", "input", "swipe", "540", "1800", "540", "700", "400")
        shot(f"{prefix}05-product-scrolled")
        adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    tap("History", "Ιστορικό")
    shot(f"{prefix}07-history")
    tap("Settings", "Ρυθμίσεις")
    shot(f"{prefix}08-settings")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    if APK:
        adb("install", "-r", APK)
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "no")
    walk("")
    adb("shell", "settings", "put", "system", "font_scale", "2.0")
    walk("large-font-")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "yes")
    walk("dark-")
    adb("shell", "cmd", "uimode", "night", "no")
