#!/usr/bin/env python3
"""Render usage/usage-chart.svg from the vtop-usage /stats JSON.

Usage: chart.py stats.json out.svg

Device-days, not unique users. The "today" bar comes from live raw pings and
is drawn semi-transparent; it rolls into the chart tomorrow at 03:17 UTC.
"""
import json
import math
import sys
from collections import OrderedDict

W, H = 720, 320
ML, MR, MT, MB = 36, 14, 64, 60

PALETTE = ["#c62828", "#1565c0", "#2e7d32", "#e65100", "#6a1b9a", "#00695c", "#4e342e", "#ad1457"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


def esc(s):
    return str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def day_label(iso):
    try:
        y, m, d = iso.split("-")
        return f"{int(d)} {MONTHS[int(m) - 1]}"
    except Exception:
        return iso


def semver_key(v):
    try:
        return tuple(int(p) for p in v.split("."))
    except ValueError:
        return (0, 0, 0)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    stats = json.load(open(src, "r", encoding="utf-8"))

    import datetime
    days = [{"day": d["day"], "versions": d["versions"], "live": False} for d in stats.get("days", [])]
    if stats.get("today"):
        today_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        # The live bar replaces the pre-dawn roll for the current day.
        days = [d for d in days if d["day"] != today_iso]
        days.append({"day": today_iso, "versions": stats["today"], "live": True})

    versions = sorted({v for d in days for v in d["versions"]}, key=semver_key)
    color = {v: PALETTE[i % len(PALETTE)] for i, v in enumerate(versions)}

    totals = [sum(d["versions"].values()) for d in days]
    ymax = max(max(totals, default=0), 1)
    step = max(1, math.ceil(ymax / 4))
    while step * 4 < ymax:
        step += 1
    ymax = step * 4

    plot_w, plot_h = W - ML - MR, H - MT - MB
    base = MT + plot_h
    band = plot_w / max(len(days), 1)
    bar_w = min(30, band * 0.6)

    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img">',
         f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
         f'<text x="{ML}" y="18" font-family="system-ui,sans-serif" font-size="13" font-weight="700" fill="#1a1a1a">VTOP++ Android — device-days by version</text>',
         f'<text x="{ML}" y="33" font-family="system-ui,sans-serif" font-size="8.5" fill="#666">opt-in anonymous ping only · device-days, not unique users · UTC · refreshed daily</text>',
         f'<text x="{ML}" y="46" font-family="system-ui,sans-serif" font-size="8.5" fill="#666">all-time: ' + ' · '.join(
             f"v{esc(v)} {n}" for v, n in sorted(stats.get("allTime", {}).items(), key=lambda kv: semver_key(kv[0]))) + '</text>']

    for i in range(5):
        y = base - plot_h * i / 4
        s.append(f'<line x1="{ML}" y1="{y:.1f}" x2="{W - MR}" y2="{y:.1f}" stroke="#e3e3e3" stroke-width="1"/>')
        s.append(f'<text x="{ML - 4}" y="{y + 3:.1f}" text-anchor="end" font-family="system-ui,sans-serif" font-size="8" fill="#888">{step * i}</text>')

    for i, d in enumerate(days):
        x = ML + band * i + (band - bar_w) / 2
        y = base
        for v in versions:
            n = d["versions"].get(v, 0)
            if n <= 0:
                continue
            h = plot_h * n / ymax
            y -= h
            op = ' opacity="0.5"' if d["live"] else ''
            s.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w:.1f}" height="{h:.1f}" fill="{color[v]}"{op}><title>{esc(d["day"])} · v{esc(v)}: {n}</title></rect>')
        label = "today*" if d["live"] else day_label(d["day"])
        anchor = 'end' if band < 24 else 'middle'
        lx = x + bar_w if band < 24 else x + bar_w / 2
        s.append(f'<text x="{lx:.1f}" y="{base + 11}" text-anchor="{anchor}" font-family="system-ui,sans-serif" font-size="7.5" fill="#555" transform="rotate(-45 {lx:.1f} {base + 11})">{esc(label)}</text>')

    lx = ML
    for v in versions:
        s.append(f'<rect x="{lx}" y="{H - 26}" width="9" height="9" fill="{color[v]}" rx="2"/>')
        s.append(f'<text x="{lx + 13}" y="{H - 18}" font-family="system-ui,sans-serif" font-size="8.5" fill="#333">v{esc(v)}</text>')
        lx += 13 + 8 * len(v) + 22

    gen = (stats.get("generated") or "")[:10]
    s.append(f'<text x="{W - MR}" y="{H - 4}" text-anchor="end" font-family="system-ui,sans-serif" font-size="7" fill="#aaa">*today is live so far and rolls in tomorrow · generated {esc(gen)}</text>')
    s.append("</svg>")

    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(s) + "\n")


if __name__ == "__main__":
    main()
