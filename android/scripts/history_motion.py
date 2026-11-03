"""60 fps recordings of every History state and interaction, with a check for instant jumps.

Usage: python -I android/scripts/history_motion.py <serial> <out_dir> <apk>
Installs the benchmark build fresh, records the empty states, seeds the sample week (SeedDiaryReceiver), then
records each interaction. For each one it prints how the visual change spreads over frames: a transition whose
largest single frame carries most of the change ("peak share" near 1) is an instant cut, not an animation.
Needs ffmpeg and numpy + Pillow.
"""
import subprocess, sys, time
from pathlib import Path

import numpy as np
from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")
SERIAL, OUT, APK = sys.argv[1], Path(sys.argv[2]).resolve(), sys.argv[3]
OUT.mkdir(parents=True, exist_ok=True)
sys.argv = [sys.argv[0], SERIAL, str(OUT)]
sys.path.insert(0, str(Path(__file__).parent))
import record_motion as rm  # noqa: E402  (find, tap_xy, adb)

adb = rm.adb


def analyse(video: Path) -> str:
    frames = OUT / video.stem
    frames.mkdir(exist_ok=True)
    for f in frames.glob("*.png"):
        f.unlink()
    subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(video), "-vf", "fps=60,scale=270:-1", str(frames / "%04d.png")], check=True)
    imgs = [np.asarray(Image.open(p).convert("L"), dtype=np.int16) for p in sorted(frames.glob("*.png"))]
    diffs = np.array([np.abs(b - a).mean() for a, b in zip(imgs, imgs[1:])])
    moving = diffs > 0.15
    if not moving.any():
        return "no change"
    # Peak share over the busiest 1 s window: one frame doing most of the work means a cut.
    best = max(range(len(diffs)), key=lambda i: diffs[i:i + 60].sum())
    window = diffs[best:best + 60]
    share = window.max() / window.sum()
    return f"frames moving {int(moving.sum())}, peak share {share:.2f}{'  <-- CUT?' if share > 0.5 else ''}"


def record(name: str, action, seconds: float = 2.2) -> None:
    video = OUT / f"{name}.webm"
    adb("emu", "screenrecord", "start", "--fps", "60", "--size", "540x1200", str(video))
    time.sleep(0.8)
    action()
    time.sleep(seconds)
    adb("emu", "screenrecord", "stop")
    time.sleep(2.5)
    videos.append(video)  # analysed at the end: ffmpeg time would eat into the next step (an Undo toast's time)


videos: list[Path] = []


def tap(label: str):
    return lambda: rm.tap_xy(rm.find(label))


def swipe(x1, y1, x2, y2, ms=300):
    return lambda: adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


adb("uninstall", "com.nufo.app")
subprocess.run(["adb", "-s", SERIAL, "install", APK], check=True, capture_output=True)
# Compiled as a real install ends up: the first launch lets ProfileInstaller write the baseline profile, then
# Android compiles it (here forced, normally in background dexopt). A JIT-only run stalls each screen's first frame.
adb("shell", "am", "start", "-W", "-n", "com.nufo.app/.MainActivity")
time.sleep(8)
adb("shell", "am", "force-stop", "com.nufo.app")
adb("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", "com.nufo.app")
adb("shell", "am", "start", "-W", "-n", "com.nufo.app/.MainActivity")
time.sleep(4)
rm.tap_xy(rm.find("Get started"))
time.sleep(2)

# Empty states
record("01-open-history-empty", tap("History"))
record("02-tab-meals-empty", tap("Meals"))
record("03-tab-search-empty", tap("Search"))

# Seeded week
adb("shell", "am", "broadcast", "-n", "com.nufo.app/.bench.SeedDiaryReceiver")
time.sleep(4)
rm.tap_xy(rm.find("Scan")); time.sleep(1.5)
record("04-open-history-full", tap("History"))
record("05-tab-meals-full", tap("Meals"))
record("06-scroll-meals", swipe(540, 1900, 540, 700, 400))
record("07-scroll-back", swipe(540, 700, 540, 1900, 400))
record("08-open-meal", tap("Ομελέτα με λουκάνικο"), 2.6)
record("09-back-from-meal", lambda: adb("shell", "input", "keyevent", "4"), 2.6)
_, y = rm.find("Ομελέτα με λουκάνικο")
# One recording: the toast's 4 s would run out between two recordings.
record("10-swipe-remove-undo", lambda: (swipe(1000, y, 80, y, 250)(), time.sleep(0.6), rm.tap_xy(rm.find("Undo", timeout=3))), 2.4)
_, y = rm.find("Ομελέτα με λουκάνικο")
record("12-remove-then-timeout", swipe(1000, y, 80, y, 250), 9.0)
record("13-tab-search-full", tap("Search"))
record("14-tab-meals-again", tap("Meals"))

for v in videos:
    print(f"{v.stem:28s} {analyse(v)}")
