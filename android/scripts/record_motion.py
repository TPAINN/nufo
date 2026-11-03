"""Record every main interaction as video and as a frame strip (30 fps), to review motion frame by frame.

Usage: python -I android/scripts/record_motion.py <serial> <out_dir>
For each interaction writes <name>.webm and <name>-frames.jpg (one row per 10 frames, 1/3 s per row).
Needs ffmpeg on PATH. Expects the app freshly installed (starts from a cleared state).
"""
import re, subprocess, sys, time
from pathlib import Path

SERIAL, OUT = sys.argv[1], Path(sys.argv[2])
PKG = "com.nufo.app"


def adb(*args: str, binary: bool = False):
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def find(*labels: str, timeout: float = 15):
    end = time.time() + timeout
    while time.time() < end:
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        for m in re.finditer(r"<node [^>]*>", xml):
            n = m.group(0)
            label = re.search(r'text="([^"]*)"', n).group(1) + " " + re.search(r'content-desc="([^"]*)"', n).group(1)
            if any(l.lower() in label.lower() for l in labels):
                x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
        time.sleep(0.5)
    raise SystemExit(f"not found: {labels}")


def tap_xy(xy) -> None:
    adb("shell", "input", "tap", str(xy[0]), str(xy[1]))


def record(name: str, action, seconds: float = 2.5) -> None:
    """Record the action, then measure how its change spreads over frames.

    Writes <name>.webm, <name>-frames.jpg (30 fps strip, 10 frames per row) and appends to motion.tsv:
    frames that changed, transition length in ms, and the share of the change in the single biggest frame.
    A cut puts ~100 % of the change in one frame; a smooth transition spreads it over many.
    """
    # The emulator's host-side recorder: a true constant 60 fps. The on-device screenrecord drops most frames
    # on emulators and makes smooth transitions look like cuts.
    video = (OUT / f"{name}.webm").resolve()
    adb("emu", "screenrecord", "start", "--fps", "60", "--size", "540x1200", str(video))
    time.sleep(1.0)
    action()
    time.sleep(seconds)
    adb("emu", "screenrecord", "stop")
    time.sleep(2.5)  # the encoder finishes the file after stop
    frames = OUT / f"{name}-raw"
    frames.mkdir(exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-vf", "fps=60,scale=135:-1", str(frames / "%04d.png")])
    from PIL import Image, ImageChops
    files = sorted(frames.glob("*.png"))
    diffs = []
    for a, b in zip(files, files[1:]):
        d = ImageChops.difference(Image.open(a).convert("L"), Image.open(b).convert("L"))
        diffs.append(sum(d.getdata()) / (d.width * d.height))
    moving = [i for i, d in enumerate(diffs) if d > 0.4]
    first = moving[0] if moving else 0
    total = sum(diffs) or 1
    with open(OUT / "motion.tsv", "a", encoding="utf-8") as f:
        span = (moving[-1] - first + 1) * 1000 // 60 if moving else 0
        f.write(f"{name}\t{len(moving)}\t{span}\t{max(diffs or [0]) / total:.0%}\n")
    # Strip: 60 frames at 30 fps from just before the first change.
    start = max(0, first - 2) / 60
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", f"{start:.2f}", "-t", "2.0", "-i", str(video),
                    "-vf", "fps=30,scale=135:-1,tile=10x6:padding=2:color=white", "-frames:v", "1",
                    str(OUT / f"{name}-frames.jpg")])
    for f in files:
        f.unlink()
    frames.rmdir()
    print("recorded", name)


def tap(*labels: str):
    """Locate first (the UI dump is slow), return an action that only taps."""
    xy = find(*labels)
    return lambda: tap_xy(xy)


def back() -> None:
    adb("shell", "input", "keyevent", "4")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    record("01-welcome-to-home", tap("Get started", "Ξεκινήστε"))
    record("02-open-search", tap("Search", "Αναζήτηση"))
    record("03-type-query", lambda: adb("shell", "input", "text", "yogurt"), seconds=5)
    adb("shell", "input", "keyevent", "66")  # submit: clears focus, stops the blinking cursor so the UI goes idle
    time.sleep(1)
    record("04-open-product", tap("kcal"), seconds=5)
    record("05-open-score-method", tap("How is this calculated", "Πώς υπολογίζεται"))
    record("06-close-sheet", back)
    record("07-scroll-product", lambda: adb("shell", "input", "swipe", "540", "1900", "540", "500", "350"))
    record("08-back-to-search", back)
    back()
    time.sleep(1)
    record("09-tab-history", tap("History", "Ιστορικό"))
    record("10-tab-settings", tap("Settings", "Ρυθμίσεις"))
    record("11-tab-home", tap("Home", "Αρχική"))
    record("12-open-scanner", tap("Scan", "Σάρωση"), seconds=3)
    record("13-close-scanner", back)
