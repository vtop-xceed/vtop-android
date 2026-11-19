#!/usr/bin/env python3
"""Render usage/usage-chart.svg from the vtop-usage /stats JSON.

Usage: chart.py stats.json out.svg

Stacked area of device-days per version over the trailing 30 days.
Device-days, not unique users. Today's slice is live (raw pings, dotted
outline) and rolls into the chart tomorrow at 03:17 UTC. Days with no
pings in agg_daily plot as 0: that measured zero, it was not measured
zero-if-nobody-wanted-to-know days.
"""
import json
import math
import sys
from datetime import date, timedelta

W, H = 720, 330
ML, MR, MT, MB = 36, 14, 64, 66

PALETTE = ["#c62828", "#1565c0", "#2e7d32", "#e65100", "#6a1b9a", "#00695c", "#4e342e", "#ad1457"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


def esc(s):
    return str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def day_label(d):
    return f"{d.day} {MONTHS[d.month - 1]}"


def semver_key(v):
    try:
        return tuple(int(p) for p in v.split("."))
    except ValueError:
        return (0, 0, 0)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    stats = json.load(open(src, "r", encoding="utf-8"))

    by_day = {d["day"]: dict(d["versions"]) for d in stats.get("days", [])}
    today_iso = date.today().isoformat()
    # The live slice replaces the pre-dawn roll for the current day.
    live = bool(stats.get("today"))
    if live:
        by_day[today_iso] = dict(stats["today"])

    if not by_day:
        day_keys = [today_iso]
    else:
        first = date.fromisoformat(min(by_day))
        last = date.fromisoformat(max(by_day))
        day_keys = [(first + timedelta(i)).isoformat() for i in range((last - first).days + 1)]

    versions = sorted({v for vs in by_day.values() for v in vs}, key=semver_key)
    color = {v: PALETTE[i % len(PALETTE)] for i, v in enumerate(versions)}

    totals = [sum(by_day.get(d, {}).values()) for d in day_keys]
    ymax = max(max(totals, default=0), 1)
    step = max(1, math.ceil(ymax / 4))
    while step * 4 < ymax:
        step += 1
    ymax = step * 4

    plot_w, plot_h = W - ML - MR, H - MT - MB
    base = MT + plot_h
    span = max(len(day_keys) - 1, 1)
    xs = [ML + (plot_w * i / span if len(day_keys) > 1 else plot_w / 2) for i in range(len(day_keys))]

    def y_at(value):
        return base - plot_h * value / ymax

    layers = []
    cumul = [0] * len(day_keys)
    for v in versions:
        vals = [by_day.get(d, {}).get(v, 0) for d in day_keys]
        top = [c + val for c, val in zip(cumul, vals)]
        layers.append((v, list(cumul), list(top)))
        cumul = top

    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img">',
         f'<rect width="{W}" height="{H}" fill="#000000"/>',
         f'<text x="{ML}" y="18" font-family="system-ui,sans-serif" font-size="13" font-weight="700" fill="#f0f6fc">VTOP++ Android — device-days by version</text>',
         f'<text x="{ML}" y="33" font-family="system-ui,sans-serif" font-size="8.5" fill="#8b949e">opt-in anonymous ping only · device-days, not unique users · UTC · refreshed daily</text>',
         f'<text x="{ML}" y="46" font-family="system-ui,sans-serif" font-size="8.5" fill="#8b949e">all-time: ' + ' · '.join(
             f"v{esc(v)} {n}" for v, n in sorted(stats.get("allTime", {}).items(), key=lambda kv: semver_key(kv[0]))) + '</text>']

    for i in range(5):
        y = base - plot_h * i / 4
        s.append(f'<line x1="{ML}" y1="{y:.1f}" x2="{W - MR}" y2="{y:.1f}" stroke="#21262d" stroke-width="1"/>')
        s.append(f'<text x="{ML - 4}" y="{y + 3:.1f}" text-anchor="end" font-family="system-ui,sans-serif" font-size="8" fill="#8b949e">{step * i}</text>')

    for v, bottom, top in layers:
        pts_top = " ".join(f"{x:.1f},{y_at(t):.1f}" for x, t in zip(xs, top))
        pts_bot = " ".join(f"{x:.1f},{y_at(b):.1f}" for x, b in zip(reversed(xs), reversed(bottom)))
        s.append(f'<polygon points="{pts_top} {pts_bot}" fill="{color[v]}" fill-opacity="0.95"><title>v{esc(v)}</title></polygon>')

    if live and len(day_keys) >= 2:
        top_pts = " ".join(f"{x:.1f},{y_at(t):.1f}" for x, t in zip(xs, layers[-1][2]))
        s.append(f'<polyline points="{top_pts}" fill="none" stroke="#ffffff" stroke-width="1.1" stroke-dasharray="3 3" opacity="0.8"/>')
        x = xs[-1]
        s.append(f'<line x1="{x:.1f}" y1="{y_at(cumul[-1]):.1f}" x2="{x:.1f}" y2="{base:.1f}" stroke="#8b949e" stroke-width="1" stroke-dasharray="3 3" opacity="0.8"/>')
        s.append(f'<text x="{x - 3:.1f}" y="{y_at(cumul[-1]) - 5:.1f}" text-anchor="end" font-family="system-ui,sans-serif" font-size="7.5" fill="#c9d1d9">today (live)</text>')

    ticks = 6 if len(day_keys) >= 6 else len(day_keys)
    for i in range(ticks):
        idx = round(i * (len(day_keys) - 1) / max(ticks - 1, 1))
        d = date.fromisoformat(day_keys[idx])
        anchor = 'start' if i == 0 else ('end' if i == ticks - 1 else 'middle')
        s.append(f'<text x="{xs[idx]:.1f}" y="{base + 12}" text-anchor="{anchor}" font-family="system-ui,sans-serif" font-size="7.5" fill="#c9d1d9">{day_label(d)}</text>')

    lx = ML
    for v in versions:
        s.append(f'<rect x="{lx}" y="{H - 30}" width="9" height="9" fill="{color[v]}" rx="2"/>')
        s.append(f'<text x="{lx + 13}" y="{H - 22}" font-family="system-ui,sans-serif" font-size="8.5" fill="#f0f6fc">v{esc(v)}</text>')
        lx += 13 + 8 * len(v) + 22

    gen = (stats.get("generated") or "")[:10]
    s.append(f'<text x="{W - MR}" y="{H - 6}" text-anchor="end" font-family="system-ui,sans-serif" font-size="7" fill="#6e7681">day with no pings plots as zero · generated {esc(gen)}</text>')
    s.append(f'<text x="{ML}" y="{H - 6}" font-family="system-ui,sans-serif" font-size="7" fill="#6e7681">window: trailing 30 days, oldest data drops off</text>')
    s.append("</svg>")

    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(s) + "\n")


if __name__ == "__main__":
    main()
