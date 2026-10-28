#!/usr/bin/env python3
"""Measures the same Live card built four ways on a connected device or emulator.

Usage: python3 scripts/measure_footprint.py [runs] [adb-serial]

Each approach runs in its own process (see the debug manifest). After a 15 second warm-up it
samples 30 seconds of CPU time and rendered frames, and reads the proportional set size.
"""
import re
import statistics
import subprocess
import sys
import time

PKG = "example.livematch.android"
CASES = [
    ("Compose local", "ComposeLocalActivity", ":compose"),
    ("SDUI JSON", "SduiActivity", ":sdui"),
    ("WebView", "WebViewActivity", ":web"),
    ("Remote Compose", "RemoteCardActivity", ":remote"),
]
runs = int(sys.argv[1]) if len(sys.argv) > 1 else 3
serial = ["-s", sys.argv[2]] if len(sys.argv) > 2 else []


def sh(*args):
    return subprocess.run(["adb", *serial, "shell", *args], capture_output=True, text=True).stdout


def cpu_ticks(pid):
    fields = sh("cat", f"/proc/{pid}/stat").rsplit(")", 1)[1].split()
    return int(fields[11]) + int(fields[12])  # utime + stime


def pss_mb(process):
    return int(re.search(r"TOTAL PSS:\s+(\d+)", sh("dumpsys", "meminfo", process)).group(1)) / 1024


def frames(process):
    return int(re.search(r"Total frames rendered:\s+(\d+)", sh("dumpsys", "gfxinfo", process)).group(1))


tick_hz = int(sh("getconf", "CLK_TCK").strip() or 100)
results = {name: [] for name, _, _ in CASES}
for _ in range(runs):
    for name, activity, suffix in CASES:
        process = PKG + suffix
        sh("am", "force-stop", PKG)
        sh("input", "keyevent", "KEYCODE_HOME")
        time.sleep(1)
        started = time.time()
        sh("am", "start", "-W", "-n", f"{PKG}/.{activity}")
        launch_ms = (time.time() - started) * 1000
        time.sleep(15)
        pid = sh("pidof", process).split()[0]
        ticks_before, frames_before = cpu_ticks(pid), frames(process)
        time.sleep(30)
        ticks_after, frames_after = cpu_ticks(pid), frames(process)
        results[name].append(
            (launch_ms, pss_mb(process), (ticks_after - ticks_before) / tick_hz * 1000 / 30, (frames_after - frames_before) / 30)
        )
sh("am", "force-stop", PKG)

print(f"{'approach':<16}{'launch ms':>12}{'PSS MB':>10}{'CPU ms/s':>12}{'frames/s':>10}   (median of {runs}, range in brackets)")
for name, rows in results.items():
    cells = []
    for column in range(4):
        values = [row[column] for row in rows]
        cells.append(f"{statistics.median(values):.1f} [{min(values):.0f}-{max(values):.0f}]")
    print(f"{name:<16}" + "".join(f"{cell:>20}" for cell in cells))
