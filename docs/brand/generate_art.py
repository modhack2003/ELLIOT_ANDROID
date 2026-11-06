#!/usr/bin/env python3
"""Generates the Singularity README artwork in docs/brand.

Every asset is a self-contained SVG animated with SMIL, so GitHub plays it
inside a README <img>. No scripts, external fonts or images are used.

The particle forms and the projection mirror
app/src/main/java/com/eiliot/app/overlay/HoloCoreRenderer.kt, so the orb in
the README is the same shape as the orb in the app. Each animation loops
seamlessly: rotation and every time-based deformation complete a whole
number of cycles per loop.

Run from the repository root:  python3 docs/brand/generate_art.py
"""
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
TAU = math.tau
C = dict(
    void="#020309", abyss="#060A1C", cyan="#00E5FF", violet="#8C7DFF", magenta="#FF4FD8",
    teal="#3EF2D8", uv="#B48CFF", alert="#FF5A2A", amber="#FFB54F",
    text="#EAF4FF", sub="#A3B6D0", muted="#61748F",
)
SANS = "Segoe UI, Inter, Helvetica Neue, Arial, sans-serif"
MONO = "SFMono-Regular, Consolas, Menlo, monospace"

# name, base, hot, rim: must match OrbState.kt
STATES = [
    ("IDLE", "#00E5FF", "#F2FEFF", "#8C7DFF"),
    ("LISTENING", "#8EF6FF", "#FFFFFF", "#00E5FF"),
    ("THINKING", "#8C7DFF", "#EDEAFF", "#FF4FD8"),
    ("SPEAKING", "#3EF2D8", "#FFFFFF", "#00E5FF"),
    ("ALERT", "#FF5A2A", "#FFE08A", "#FFB54F"),
    ("LEARNING", "#B48CFF", "#F3EDFF", "#FF4FD8"),
]


def n(v, d=1):
    """Compact number formatting for SVG attributes."""
    s = f"{v:.{d}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def frac(v):
    return v - math.floor(v)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def anim(attr, values, dur, d=1, extra=""):
    vals = ";".join(n(v, d) for v in values)
    return f'<animate attributeName="{attr}" dur="{n(dur, 2)}s" repeatCount="indefinite" values="{vals}"{extra}/>'


# ------------------------------------------------------------------ 3D forms

GOLDEN_ANGLE = 2.3999631


def sphere(i, count):
    y = 1 - 2 * (i + 0.5) / count
    r = math.sqrt(max(0.0, 1 - y * y))
    th = i * GOLDEN_ANGLE
    return [math.cos(th) * r, y, math.sin(th) * r]


def form(state, i, count, ph):
    """Point i of the given state's form at loop phase ph in [0, 1]."""
    if state == "IDLE":
        p = sphere(i, count)
        s = 1 + 0.03 * math.sin(TAU * ph + p[1] * 3.4)
        return [c * s for c in p]
    if state == "LISTENING":
        p = sphere(i, count)
        s = 1 + 0.13 * math.sin(p[1] * 7 - TAU * 2 * ph)
        return [c * s for c in p]
    if state == "THINKING":
        u = TAU * frac(i * 0.618034) + TAU * ph
        v = TAU * frac(i * 0.7548777) - TAU * 2 * ph
        ring = 0.80 + 0.30 * math.cos(v)
        x, z, y = ring * math.cos(u), ring * math.sin(u), 0.30 * math.sin(v)
        lean = 0.55
        return [x, y * math.cos(lean) - z * math.sin(lean), y * math.sin(lean) + z * math.cos(lean)]
    if state == "SPEAKING":
        bands = 7
        band = i * bands // count
        start = band * count // bands
        size = max(1, (band + 1) * count // bands - start)
        lat = math.pi * (band + 0.5) / bands - math.pi / 2
        direction = 1 if band % 2 == 0 else -1
        lon = TAU * (i - start) / size + band * 0.37 + direction * TAU * ph
        r = 1.04 + 0.26 * max(0.0, math.sin(TAU * 2 * ph - band * 0.9))
        return [math.cos(lat) * math.cos(lon) * r, math.sin(lat) * r, math.cos(lat) * math.sin(lon) * r]
    if state == "ALERT":
        p = sphere(i, count)
        peak = max(abs(c) for c in p)
        s = (0.88 + 0.55 * peak ** 9) * (1 + 0.05 * math.sin(TAU * 4 * ph))
        return [c * s for c in p]
    # LEARNING: double helix
    pairs = count // 2
    pair, strand = i // 2, i % 2
    y = -1.18 + 2.36 * pair / (pairs - 1)
    a = pair * 0.43 + strand * math.pi + TAU * ph
    return [0.5 * math.cos(a), y, 0.5 * math.sin(a)]


def project(p, yaw, cx, cy, radius, pitch=0.38, camera=4.4):
    x, y, z = p
    cyw, syw = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    x1 = x * cyw + z * syw
    z1 = -x * syw + z * cyw
    y2 = y * cp - z1 * sp
    z2 = y * sp + z1 * cp
    s = camera / (camera - z2)
    depth = min(1.0, max(0.0, (z2 / 1.25 + 1) / 2))
    return cx + x1 * s * radius, cy - y2 * s * radius, s, depth


def cloud(state, count, frames, dur, cx, cy, radius, dot, color, pos_d=1):
    """Animated particle cloud. Returns (svg, projected frames)."""
    proj = []
    for k in range(frames + 1):
        ph = k / frames
        proj.append([project(form(state, i, count, ph), TAU * ph, cx, cy, radius) for i in range(count)])
    parts = []
    for i in range(count):
        xs = [f[i][0] for f in proj]
        ys = [f[i][1] for f in proj]
        rs = [(0.9 + 2.1 * f[i][3]) * f[i][2] * dot for f in proj]
        ops = [0.16 + 0.84 * f[i][3] ** 1.4 for f in proj]
        parts.append(
            f'<circle cx="{n(xs[0], pos_d)}" cy="{n(ys[0], pos_d)}" r="{n(rs[0])}" fill-opacity="{n(ops[0], 2)}">'
            + anim("cx", xs, dur, pos_d) + anim("cy", ys, dur, pos_d) + anim("r", rs, dur) + anim("fill-opacity", ops, dur, 2)
            + "</circle>"
        )
    return f'<g fill="{color}">' + "".join(parts) + "</g>", proj


def helix_rungs(proj, dur, color, pos_d=0):
    """Rungs between helix strands, drawn for every third pair."""
    count = len(proj[0])
    out = []
    for i in range(0, count - 1, 6):
        def coord(j, axis):
            return [f[j][axis] for f in proj]
        ops = [0.10 + 0.5 * (f[i][3] + f[i + 1][3]) / 2 for f in proj]
        out.append(
            f'<line x1="{n(proj[0][i][0], pos_d)}" y1="{n(proj[0][i][1], pos_d)}" x2="{n(proj[0][i + 1][0], pos_d)}" y2="{n(proj[0][i + 1][1], pos_d)}" stroke-opacity="{n(ops[0], 2)}">'
            + anim("x1", coord(i, 0), dur, pos_d) + anim("y1", coord(i, 1), dur, pos_d)
            + anim("x2", coord(i + 1, 0), dur, pos_d) + anim("y2", coord(i + 1, 1), dur, pos_d)
            + anim("stroke-opacity", ops, dur, 2) + "</line>"
        )
    return f'<g stroke="{color}" stroke-width="1.2">' + "".join(out) + "</g>"


# ------------------------------------------------------------- shared pieces

def defs_common():
    return f"""
<linearGradient id="glass" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#111A3A" stop-opacity=".92"/><stop offset="1" stop-color="#060A1A" stop-opacity=".92"/></linearGradient>
<linearGradient id="sheen" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFFFFF" stop-opacity=".07"/><stop offset=".32" stop-color="#FFFFFF" stop-opacity=".015"/><stop offset=".33" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="1" y2="1"><stop stop-color="{C['cyan']}" stop-opacity=".8"/><stop offset=".45" stop-color="{C['violet']}" stop-opacity=".25"/><stop offset="1" stop-color="{C['magenta']}" stop-opacity=".6"/></linearGradient>
<linearGradient id="space" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#020309"/><stop offset=".55" stop-color="#060A1F"/><stop offset="1" stop-color="#0B0722"/></linearGradient>
<radialGradient id="nebCyan"><stop stop-color="{C['cyan']}" stop-opacity=".16"/><stop offset=".5" stop-color="{C['cyan']}" stop-opacity=".05"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity="0"/></radialGradient>
<radialGradient id="nebViolet"><stop stop-color="{C['violet']}" stop-opacity=".18"/><stop offset=".5" stop-color="{C['violet']}" stop-opacity=".06"/><stop offset="1" stop-color="{C['violet']}" stop-opacity="0"/></radialGradient>
<radialGradient id="nebMagenta"><stop stop-color="{C['magenta']}" stop-opacity=".13"/><stop offset=".5" stop-color="{C['magenta']}" stop-opacity=".04"/><stop offset="1" stop-color="{C['magenta']}" stop-opacity="0"/></radialGradient>
<filter id="soft" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="9"/></filter>"""


def svg_open(w, h, title, desc):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-labelledby="title desc">'
        f'<title id="title">{esc(title)}</title><desc id="desc">{esc(desc)}</desc>'
    )


def space(w, h, rx=26, nebulae=((0.12, 0.2, 0.8, "nebCyan"), (0.9, 0.35, 0.9, "nebViolet"), (0.55, 1.05, 0.9, "nebMagenta"))):
    out = [f'<rect width="{w}" height="{h}" rx="{rx}" fill="url(#space)"/>']
    unit = min(w, h)
    for x, y, r, grad in nebulae:
        out.append(f'<ellipse cx="{n(w * x)}" cy="{n(h * y)}" rx="{n(unit * r * 1.5)}" ry="{n(unit * r)}" fill="url(#{grad})"/>')
    return "".join(out)


def stars(w, h, count, seed):
    rnd = random.Random(seed)
    out = []
    for _ in range(count):
        x, y = rnd.uniform(8, w - 8), rnd.uniform(8, h - 8)
        r = rnd.choice([0.6, 0.7, 0.9, 1.1, 1.5])
        col = rnd.choice(["#FFFFFF", "#FFFFFF", "#FFFFFF", C["cyan"], C["uv"]])
        a = rnd.uniform(0.25, 0.9)
        out.append(
            f'<circle cx="{n(x)}" cy="{n(y)}" r="{r}" fill="{col}" opacity="{n(a, 2)}">'
            f'<animate attributeName="opacity" values="{n(a, 2)};{n(a * 0.2, 2)};{n(a, 2)}" dur="{n(rnd.uniform(2.5, 6.5))}s" begin="-{n(rnd.uniform(0, 6))}s" repeatCount="indefinite"/></circle>'
        )
    return "<g>" + "".join(out) + "</g>"


def grid(w, h, horizon, vanish_x, dur=6, frames=24, lines=12):
    """Perspective floor grid; horizontal lines scroll toward the viewer."""
    focal = (h - horizon) * 1.25
    far = 14.0
    out = [f'<g stroke="{C["cyan"]}">']
    for xw in range(-12, 13):
        top_x = vanish_x + xw * focal / far * 1.6
        bottom_x = vanish_x + xw * focal / 0.6 * 1.6
        out.append(f'<line x1="{n(top_x)}" y1="{n(horizon + focal / far)}" x2="{n(bottom_x)}" y2="{n(horizon + focal / 0.6)}" stroke-opacity=".13"/>')
    for k in range(1, lines + 1):
        ys, ops = [], []
        for f in range(frames + 1):
            z = k - f / frames + 0.6
            ys.append(horizon + focal / z)
            ops.append(0.32 * max(0.0, 1 - z / far) ** 2)
        out.append(
            f'<line x1="0" x2="{w}" y1="{n(ys[0])}" y2="{n(ys[0])}" stroke-opacity="{n(ops[0], 3)}">'
            + anim("y1", ys, dur) + anim("y2", ys, dur) + anim("stroke-opacity", ops, dur, 3) + "</line>"
        )
    out.append("</g>")
    out.append(
        f'<rect x="0" y="{n(horizon - 1)}" width="{w}" height="2" fill="url(#horizonLine)"/>'
    )
    return "".join(out)


def horizon_defs():
    return f"""<linearGradient id="horizonLine"><stop stop-color="{C['cyan']}" stop-opacity="0"/><stop offset=".35" stop-color="{C['cyan']}" stop-opacity=".7"/><stop offset=".65" stop-color="{C['magenta']}" stop-opacity=".55"/><stop offset="1" stop-color="{C['magenta']}" stop-opacity="0"/></linearGradient>
<linearGradient id="floorFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#020309" stop-opacity="0"/><stop offset="1" stop-color="#020309" stop-opacity=".55"/></linearGradient>"""


def card(x, y, w, h, accent=C["cyan"], rx=22, brackets=True):
    out = (
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="url(#glass)"/>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="url(#sheen)"/>'
        f'<rect x="{x + .5}" y="{y + .5}" width="{w - 1}" height="{h - 1}" rx="{rx}" fill="none" stroke="url(#edge)"/>'
    )
    if brackets:
        i, a = 10, 13
        out += (
            f'<path d="M{x + i} {y + i + a}V{y + i}H{x + i + a}M{x + w - i - a} {y + h - i}H{x + w - i}V{y + h - i - a}" '
            f'fill="none" stroke="{accent}" stroke-opacity=".65" stroke-width="1.5" stroke-linecap="round"/>'
        )
    return out


def ring(cx, cy, rx, ry, angle, color, dur, width=1.6, bead=True, reverse=False):
    """Gyroscope ring: far half dim, near half bright with spinning dashes and a bead."""
    sign = "-" if reverse else ""
    far = f"M{-rx} 0A{rx} {ry} 0 0 1 {rx} 0"
    near = f"M{-rx} 0A{rx} {ry} 0 0 0 {rx} 0"
    track = f"M{rx} 0A{rx} {ry} 0 1 1 {-rx} 0A{rx} {ry} 0 1 1 {rx} 0"
    back = (
        f'<g transform="translate({n(cx)} {n(cy)}) rotate({angle})">'
        f'<path d="{far}" fill="none" stroke="{color}" stroke-opacity=".28" stroke-width="{width * .8}"/></g>'
    )
    dash = rx * 0.32
    front = (
        f'<g transform="translate({n(cx)} {n(cy)}) rotate({angle})">'
        f'<path d="{near}" fill="none" stroke="{color}" stroke-opacity=".9" stroke-width="{width}" stroke-linecap="round" stroke-dasharray="{n(dash)} {n(dash * .45)}">'
        f'<animate attributeName="stroke-dashoffset" from="0" to="{sign}{n(dash * 1.45 * 4)}" dur="{dur}s" repeatCount="indefinite"/></path>'
    )
    if bead:
        front += (
            f'<circle r="{n(width * 2.4)}" fill="#FFFFFF"><animateMotion dur="{dur}s" repeatCount="indefinite" path="{track}"/>'
            f'<animate attributeName="opacity" dur="{dur}s" repeatCount="indefinite" values="1;1;.2;.2;1" keyTimes="0;.46;.54;.96;1"/></circle>'
        )
    front += "</g>"
    return back, front


# ---------------------------------------------------------------------- hero

def hero():
    w, h = 1280, 560
    cx, cy, radius = 958, 232, 108
    stage_y = 476
    parts = [svg_open(w, h, "ELLIOT, voice companion for Android",
                      "A 3D particle orb, the ELLIOT Holo Core, turns above a hologram projector in deep space. "
                      "The ELLIOT wordmark shimmers with an aurora gradient beside the tagline: Say Hey Elliot. Ask about your phone. Hear the answer.")]
    parts.append("<defs>" + defs_common() + horizon_defs() + f"""
<linearGradient id="mark" gradientUnits="userSpaceOnUse" x1="72" y1="0" x2="600" y2="0" spreadMethod="repeat">
<stop stop-color="{C['cyan']}"/><stop offset=".33" stop-color="{C['violet']}"/><stop offset=".66" stop-color="{C['magenta']}"/><stop offset="1" stop-color="{C['cyan']}"/>
<animateTransform attributeName="gradientTransform" type="translate" from="0 0" to="528 0" dur="9s" repeatCount="indefinite"/></linearGradient>
<radialGradient id="halo"><stop stop-color="{C['cyan']}" stop-opacity=".30"/><stop offset=".45" stop-color="{C['violet']}" stop-opacity=".10"/><stop offset="1" stop-color="{C['violet']}" stop-opacity="0"/></radialGradient>
<radialGradient id="core"><stop stop-color="#FFFFFF" stop-opacity=".95"/><stop offset=".38" stop-color="{C['cyan']}" stop-opacity=".55"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity="0"/></radialGradient>
<linearGradient id="streak"><stop stop-color="{C['violet']}" stop-opacity="0"/><stop offset=".5" stop-color="#F2FEFF" stop-opacity=".8"/><stop offset="1" stop-color="{C['violet']}" stop-opacity="0"/></linearGradient>
<linearGradient id="cone" x1="0" y1="0" x2="0" y2="1"><stop stop-color="{C['cyan']}" stop-opacity="0"/><stop offset=".55" stop-color="{C['cyan']}" stop-opacity=".06"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity=".24"/></linearGradient>
<radialGradient id="emit"><stop stop-color="{C['cyan']}" stop-opacity=".42"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity="0"/></radialGradient>
<clipPath id="frame"><rect width="{w}" height="{h}" rx="26"/></clipPath>""" + "</defs>")
    parts.append('<g clip-path="url(#frame)">')
    parts.append(space(w, h))
    parts.append(stars(w, h * 0.72, 140, 7))
    parts.append(grid(w, h, 418, 958))
    parts.append(f'<rect y="418" width="{w}" height="{h - 418}" fill="url(#floorFade)"/>')

    # ---- projector stage
    parts.append(f'<path d="M{cx - 150} {stage_y}L{cx - 44} {cy + 40}H{cx + 44}L{cx + 150} {stage_y}Z" fill="url(#cone)"/>')
    for k in range(3):
        parts.append(
            f'<ellipse cx="{cx}" cy="{stage_y}" rx="150" ry="33" fill="none" stroke="{C["cyan"]}" stroke-width="1.2" opacity="0">'
            f'<animate attributeName="cy" values="{stage_y};{cy + 50}" dur="4s" begin="-{n(k * 4 / 3, 2)}s" repeatCount="indefinite"/>'
            f'<animate attributeName="rx" values="150;52" dur="4s" begin="-{n(k * 4 / 3, 2)}s" repeatCount="indefinite"/>'
            f'<animate attributeName="ry" values="33;11" dur="4s" begin="-{n(k * 4 / 3, 2)}s" repeatCount="indefinite"/>'
            f'<animate attributeName="opacity" values=".55;0" dur="4s" begin="-{n(k * 4 / 3, 2)}s" repeatCount="indefinite"/></ellipse>'
        )
    parts.append(f'<ellipse cx="{cx}" cy="{stage_y}" rx="220" ry="52" fill="url(#emit)"/>')
    for k, (rx, op, sw) in enumerate([(160, .75, 2), (122, .5, 1.3), (84, .35, 1.1)]):
        parts.append(f'<ellipse cx="{cx}" cy="{stage_y}" rx="{rx}" ry="{n(rx * .22)}" fill="none" stroke="{C["cyan"]}" stroke-opacity="{op}" stroke-width="{sw}"/>')
    parts.append(
        f'<ellipse cx="{cx}" cy="{stage_y}" rx="180" ry="40" fill="none" stroke="{C["violet"]}" stroke-width="2.4" stroke-linecap="round" stroke-dasharray="34 54">'
        f'<animate attributeName="stroke-dashoffset" from="0" to="-352" dur="7s" repeatCount="indefinite"/></ellipse>'
    )
    parts.append(f'<ellipse cx="{cx}" cy="{stage_y}" rx="16" ry="4" fill="#FFFFFF" opacity=".85"/>')

    # ---- the orb, bobbing
    parts.append('<g><animateTransform attributeName="transform" type="translate" values="0 -6;0 6;0 -6" dur="6s" repeatCount="indefinite" calcMode="spline" keySplines=".45 0 .55 1;.45 0 .55 1"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="250" fill="url(#halo)"/>')
    rings = [ring(cx, cy, 142, 40, -16, C["violet"], 9), ring(cx, cy, 158, 70, 28, C["cyan"], 12, reverse=True), ring(cx, cy, 174, 26, 6, C["magenta"], 15, width=1.3)]
    for back, _ in rings:
        parts.append(back)
    orb, _ = cloud("IDLE", 170, 30, 24, cx, cy, radius, 1.55, "#7BF3FF")
    parts.append(orb)
    parts.append(
        f'<circle cx="{cx}" cy="{cy}" r="66" fill="url(#core)"><animate attributeName="r" values="62;72;62" dur="4.8s" repeatCount="indefinite"/></circle>'
        f'<circle cx="{cx}" cy="{cy}" r="8" fill="#FFFFFF"><animate attributeName="r" values="7;10;7" dur="4.8s" repeatCount="indefinite"/></circle>'
    )
    for _, front in rings:
        parts.append(front)
    parts.append(
        f'<rect x="{cx - 240}" y="{cy - 2}" width="480" height="4" fill="url(#streak)"><animate attributeName="opacity" values=".6;1;.6" dur="4.8s" repeatCount="indefinite"/></rect>'
    )
    parts.append("</g>")

    # ---- wordmark and tagline
    parts.append(f'<g font-family="{SANS}">')
    parts.append(f'<text x="74" y="150" font-family="{MONO}" font-size="14" letter-spacing="4" fill="{C["teal"]}">● ON-DEVICE VOICE COMPANION · ANDROID</text>')
    parts.append(f'<text x="70" y="282" font-size="132" font-weight="200" letter-spacing="20" fill="url(#mark)" filter="url(#soft)" opacity=".55">ELLIOT</text>')
    parts.append(f'<text x="70" y="282" font-size="132" font-weight="200" letter-spacing="20" fill="url(#mark)">ELLIOT</text>')
    parts.append(f'<text x="74" y="336" font-size="27" font-weight="300" fill="{C["text"]}">Say “Hey Elliot”. Ask about your phone.</text>')
    parts.append(f'<text x="74" y="372" font-size="27" font-weight="300" fill="{C["text"]}">Hear the answer.</text>')
    x = 74
    for label, col in [("WAKE · STT · TTS ON DEVICE", C["cyan"]), ("ANDROID 8.0+", C["violet"]), ("0.6.3-DEV.24", C["magenta"])]:
        width = 22 + len(label) * 8.6
        parts.append(
            f'<rect x="{n(x)}" y="412" width="{n(width)}" height="32" rx="16" fill="{col}" fill-opacity=".12" stroke="{col}" stroke-opacity=".6"/>'
            f'<text x="{n(x + width / 2)}" y="433" text-anchor="middle" font-family="{MONO}" font-size="12" letter-spacing="1.6" fill="{C["text"]}">{label}</text>'
        )
        x += width + 12
    parts.append("</g>")
    parts.append("</g>")
    parts.append(f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="26" fill="none" stroke="url(#edge)"/>')
    parts.append("</svg>")
    return "".join(parts)


# ---------------------------------------------------------------- orb states

CAPTIONS = {
    "IDLE": ("Geodesic sphere,", "slow gyroscope drift"),
    "LISTENING": ("Surface waves", "follow your voice"),
    "THINKING": ("Folds into", "a turning torus"),
    "SPEAKING": ("Rings pulse", "with every reply"),
    "ALERT": ("Spiked star", "with a haptic tick"),
    "LEARNING": ("A turning", "double helix"),
}


def orb_states():
    w, h = 1280, 470
    cell, gap = 196, 12
    x0 = (w - 6 * cell - 5 * gap) / 2
    parts = [svg_open(w, h, "The six Holo Core states",
                      "Six 3D particle forms, one per orb state: a sphere for idle, a wave sphere for listening, a torus for thinking, "
                      "pulsing rings for speaking, a spiked star for alert and a double helix for learning. Each one turns in 3D.")]
    defs = defs_common()
    for name, base, _, _ in STATES:
        defs += f'<radialGradient id="h{name}"><stop stop-color="{base}" stop-opacity=".28"/><stop offset="1" stop-color="{base}" stop-opacity="0"/></radialGradient>'
    parts.append("<defs>" + defs + f'<clipPath id="frame"><rect width="{w}" height="{h}" rx="26"/></clipPath></defs>')
    parts.append('<g clip-path="url(#frame)">' + space(w, h) + stars(w, h, 70, 11))
    parts.append(f'<g font-family="{SANS}">')
    parts.append(f'<text x="44" y="50" font-family="{MONO}" font-size="13" letter-spacing="3" fill="{C["teal"]}">THE HOLO CORE</text>')
    parts.append(f'<text x="44" y="92" font-size="32" font-weight="300" fill="{C["text"]}">Six states. Six 3D forms.</text>')
    parts.append(f'<text x="44" y="124" font-size="17" fill="{C["sub"]}">When ELLIOT changes state, every particle flies to its place in the next form.</text>')
    for idx, (name, base, hot, rim) in enumerate(STATES):
        x = x0 + idx * (cell + gap)
        ccx, ccy = x + cell / 2, 262
        parts.append(card(round(x, 1), 152, cell, 290, accent=base))
        parts.append(f'<rect x="{n(x + 24)}" y="152" width="{cell - 48}" height="2" fill="{base}"/>')
        parts.append(f'<circle cx="{n(ccx)}" cy="{ccy}" r="92" fill="url(#h{name})"/>')
        back, front = ring(ccx, ccy, 74, 20, [-14, 0, 22, 4, 12, -6][idx], rim, [10, 6, 4, 8, 3, 12][idx], width=1.2, bead=False)
        parts.append(back)
        count = 72 if name != "LEARNING" else 64
        radius = 46 if name != "LEARNING" else 52
        orb, proj = cloud(name, count, 20, 12, ccx, ccy, radius, 1.0, base, pos_d=0)
        if name == "LEARNING":
            parts.append(helix_rungs(proj, 12, base))
        parts.append(orb)
        parts.append(f'<circle cx="{n(ccx)}" cy="{ccy}" r="5" fill="{hot}" opacity=".9"/>')
        parts.append(front)
        parts.append(f'<text x="{n(ccx)}" y="378" text-anchor="middle" font-family="{MONO}" font-size="13" letter-spacing="2.4" fill="{base}">{name}</text>')
        l1, l2 = CAPTIONS[name]
        parts.append(f'<text x="{n(ccx)}" y="404" text-anchor="middle" font-size="14" fill="{C["sub"]}">{l1}</text>')
        parts.append(f'<text x="{n(ccx)}" y="424" text-anchor="middle" font-size="14" fill="{C["sub"]}">{l2}</text>')
    parts.append("</g></g>")
    parts.append(f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="26" fill="none" stroke="url(#edge)"/></svg>')
    return "".join(parts)


# -------------------------------------------------------------- voice pipeline

def pipeline():
    w, h = 1280, 680
    parts = [svg_open(w, h, "ELLIOT voice pipeline",
                      "Android capture feeds GTCRN. Wake detection normally uses filtered input. Eligible calibrated profiles can bypass "
                      "software GTCRN for wake while request audio remains filtered. Wake or tapping starts request capture, then on-device STT, routing and reply.")]
    parts.append("<defs>" + defs_common() + f"""
<marker id="arrow0" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M1 1L7 4L1 7" fill="none" stroke="{C['cyan']}" stroke-width="1.5"/></marker>
<marker id="arrow1" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M1 1L7 4L1 7" fill="none" stroke="{C['uv']}" stroke-width="1.5"/></marker>
<radialGradient id="pulse"><stop stop-color="#FFFFFF"/><stop offset=".35" stop-color="{C['cyan']}"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity="0"/></radialGradient>
<clipPath id="frame"><rect width="{w}" height="{h}" rx="26"/></clipPath></defs>""")
    parts.append('<g clip-path="url(#frame)">' + space(w, h) + stars(w, h, 90, 3))
    solid = [
        ("M264 344 H330", 1.2), ("M435 288 V270 H624", 2.4), ("M540 344 H564 Q580 344 580 360 V428 Q580 444 596 444 H624", 2.4),
        ("M754 308 V390", 1.4), ("M884 444 H968", 1.2), ("M1100 498 V542", 1.0),
    ]
    dashed = "M156 288 V198 Q156 180 174 180 H580 Q600 180 600 198 V220 H624"
    parts.append(f'<g font-family="{SANS}">')
    parts.append(f'<text x="48" y="52" font-family="{MONO}" font-size="13" fill="{C["teal"]}" letter-spacing="3">INSIDE THE VOICE PIPELINE</text>')
    parts.append(f'<text x="48" y="102" font-size="38" fill="{C["text"]}" font-weight="300">Follow the sound.</text>')
    parts.append(f'<text x="48" y="137" font-size="19" fill="{C["sub"]}">Wake detection and command recognition have separate jobs and filter settings.</text>')
    for d, _ in solid:
        parts.append(f'<path d="{d}" fill="none" stroke="{C["cyan"]}" stroke-opacity=".85" stroke-width="2" stroke-linejoin="round" marker-end="url(#arrow0)"/>')
    parts.append(
        f'<path d="{dashed}" fill="none" stroke="{C["uv"]}" stroke-width="2" stroke-linejoin="round" stroke-dasharray="7 7" marker-end="url(#arrow1)">'
        f'<animate attributeName="stroke-dashoffset" from="0" to="-28" dur="1.6s" repeatCount="indefinite"/></path>'
    )
    # A signal pulse runs the pipeline in order, one hop after another, on a shared loop.
    cycle = 6.0
    start = 0.0
    for d, hop in solid:
        a, b = start / cycle, (start + hop) / cycle
        times = f"0;{n(a, 3)};{n(b, 3)};1"
        parts.append(
            f'<circle r="7" fill="url(#pulse)">'
            f'<animateMotion path="{d}" dur="{n(cycle)}s" repeatCount="indefinite" calcMode="linear" keyPoints="0;0;1;1" keyTimes="{times}"/>'
            f'<animate attributeName="opacity" dur="{n(cycle)}s" repeatCount="indefinite" values="0;0;1;1;0;0" '
            f'keyTimes="0;{n(a, 3)};{n(a + .01, 3)};{n(b - .01, 3)};{n(b, 3)};1"/></circle>'
        )
        start += hop * 0.55
    parts.append(
        f'<circle r="6" fill="{C["uv"]}" opacity=".9"><animateMotion path="{dashed}" dur="3.2s" repeatCount="indefinite"/></circle>'
    )
    parts.append(f'<text x="210" y="166" font-size="16" fill="#C9B6FF">Eligible profile · wake before GTCRN</text>')
    parts.append(f'<text x="442" y="254" font-size="14" fill="{C["sub"]}">Default wake input</text>')
    parts.append(f'<text x="772" y="354" font-size="15" fill="{C["sub"]}">Opens listening</text>')

    def box(x, y, bw, bh, kicker, title, body, accent=C["cyan"], title_size=25, body_size=15):
        out = card(x, y, bw, bh, accent=accent)
        out += f'<text x="{x + 24}" y="{y + 33}" font-family="{MONO}" font-size="12" fill="{accent}" letter-spacing="1.8">{kicker}</text>'
        out += f'<text x="{x + 24}" y="{y + 65}" font-size="{title_size}" fill="{C["text"]}" font-weight="400">{title}</text>'
        out += f'<text x="{x + 24}" y="{y + 93}" font-size="{body_size}" fill="{C["sub"]}">{body}</text>'
        return out

    parts.append(box(48, 288, 216, 112, "01 / CAPTURE", "Android mic", "Direct input + Android NS"))
    parts.append(box(330, 288, 210, 112, "02 / FILTER", "GTCRN", "Enhanced noise reduction", body_size=14))
    parts.append(card(624, 190, 260, 118, accent=C["uv"]))
    parts.append(f'<text x="648" y="221" font-family="{MONO}" font-size="12" fill="{C["uv"]}" letter-spacing="1.8">WAKE PHRASE</text>')
    parts.append(f'<text x="648" y="254" font-size="28" fill="{C["text"]}">“Hey Elliot”</text>')
    parts.append(f'<text x="648" y="282" font-size="16" fill="{C["sub"]}">Saved pronunciation profile</text>')
    parts.append(box(624, 390, 260, 108, "03 / LISTEN", "Capture request", "Wake or tap to start", body_size=16))
    parts.append(box(968, 390, 264, 108, "04 / TRANSCRIBE", "On-device STT", "Zipformer or optional Whisper"))
    parts.append(card(48, 444, 492, 166, accent=C["teal"]))
    parts.append(f'<text x="72" y="480" font-family="{MONO}" font-size="12" fill="{C["teal"]}" letter-spacing="1.4">THE CALIBRATED VIVO CONFIGURATION</text>')
    parts.append(f'<text x="72" y="514" font-size="19" fill="{C["text"]}" font-weight="300">Wake can listen before software denoising.</text>')
    parts.append(f'<text x="72" y="545" font-size="19" fill="{C["text"]}" font-weight="300">Request audio still uses enhanced filtering.</text>')
    parts.append(f'<text x="72" y="582" font-size="15" fill="{C["sub"]}">Solid paths are standard. Dashed path is optional.</text>')
    parts.append(card(624, 542, 608, 68, accent=C["magenta"], brackets=False))
    parts.append(f'<text x="648" y="583" font-size="20" fill="{C["text"]}" font-weight="300">Offline tool or chosen conversation provider</text>')
    parts.append(f'<path d="M1078 576 H1112" fill="none" stroke="{C["magenta"]}" stroke-width="2" marker-end="url(#arrow0)"/>')
    parts.append(f'<text x="1124" y="583" font-size="20" fill="{C["text"]}" font-weight="400">reply</text>')
    parts.append(f'<text x="48" y="651" font-family="{MONO}" font-size="12" fill="{C["teal"]}" letter-spacing="1.8">SHERPA-ONNX</text>')
    parts.append(f'<text x="213" y="651" font-size="16" fill="{C["sub"]}">Native inference runs in the private :speech process.</text>')
    parts.append("</g></g>")
    parts.append(f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="26" fill="none" stroke="url(#edge)"/></svg>')
    return "".join(parts)


# --------------------------------------------------------------- speech modes

def speech_modes():
    w, h = 1280, 384
    parts = [svg_open(w, h, "Three optional Whisper language modes",
                      "Indian English, Hindi-English and Bengali-English are selectable for short live requests with optional Whisper packs. "
                      "Auto is also available. Real-speech accuracy remains under evaluation.")]
    parts.append("<defs>" + defs_common() + f'<clipPath id="frame"><rect width="{w}" height="{h}" rx="26"/></clipPath></defs>')
    parts.append('<g clip-path="url(#frame)">' + space(w, h) + stars(w, h, 60, 5))
    parts.append(f'<g font-family="{SANS}">')
    parts.append(f'<text x="32" y="48" font-size="28" fill="{C["text"]}" font-weight="300">Multilingual request settings</text>')
    rnd = random.Random(21)
    cards = [("EN", "Indian English", "English with regional accents", C["cyan"]),
             ("HI + EN", "Hindi-English", "Mixed-language requests", C["uv"]),
             ("BN + EN", "Bengali-English", "Mixed-language requests", C["magenta"])]
    for idx, (code, title, body, col) in enumerate(cards):
        x = 32 + idx * 412
        parts.append(card(x, 80, 392, 232, accent=col))
        parts.append(f'<rect x="{x + 22}" y="80" width="348" height="2" fill="{col}"/>')
        parts.append(f'<text x="{x + 26}" y="121" font-family="{MONO}" font-size="11" fill="{col}" letter-spacing="2">WHISPER LANGUAGE MODE</text>')
        parts.append(f'<text x="{x + 26}" y="188" font-size="42" fill="{col}" font-weight="300">{code}</text>')
        parts.append(f'<text x="{x + 26}" y="244" font-size="27" fill="{C["text"]}" font-weight="400">{title}</text>')
        parts.append(f'<text x="{x + 26}" y="279" font-size="17" fill="{C["sub"]}">{body}</text>')
        # Live equaliser in the card's corner.
        bars = 9
        for b in range(bars):
            bx = x + 262 + b * 12
            lows = [rnd.uniform(6, 14) for _ in range(3)]
            highs = [rnd.uniform(20, 46) for _ in range(3)]
            hs = [lows[0], highs[0], lows[1], highs[1], lows[2], highs[2], lows[0]]
            ys = [176 - v / 2 for v in hs]
            dur = rnd.uniform(1.6, 2.6)
            parts.append(
                f'<rect x="{n(bx)}" y="{n(ys[0])}" width="5" height="{n(hs[0])}" rx="2.5" fill="{col}" opacity=".85">'
                + anim("height", hs, dur) + anim("y", ys, dur) + "</rect>"
            )
    parts.append(f'<text x="32" y="352" font-size="17" fill="{C["sub"]}">Optional Whisper Small or Turbo packs, including Turbo with an unquantized decoder · Auto also available · Live accuracy remains under evaluation</text>')
    parts.append("</g></g>")
    parts.append(f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="26" fill="none" stroke="url(#edge)"/></svg>')
    return "".join(parts)


# ------------------------------------------------------------- signal divider

def signal():
    w, h = 1280, 86
    parts = [svg_open(w, h, "Decorative aurora signal", "Three luminous waveforms in cyan, violet and magenta drift across a dark band.")]
    parts.append(f"""<defs><linearGradient id="fade"><stop stop-color="#FFFFFF" stop-opacity="0"/><stop offset=".18" stop-color="#FFFFFF"/><stop offset=".82" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient>
<mask id="edges"><rect width="{w}" height="{h}" fill="url(#fade)"/></mask>
<radialGradient id="dot"><stop stop-color="#FFFFFF"/><stop offset=".3" stop-color="{C['cyan']}"/><stop offset="1" stop-color="{C['cyan']}" stop-opacity="0"/></radialGradient>
<filter id="blur" x="-5%" y="-50%" width="110%" height="200%"><feGaussianBlur stdDeviation="3"/></filter></defs>""")
    parts.append('<g mask="url(#edges)">')
    for col, wave, amp, dur, width, phase in [(C["cyan"], 220, 18, 7, 2.2, 0), (C["violet"], 320, 13, 11, 1.6, 90), (C["magenta"], 160, 9, 5, 1.2, 40)]:
        pts = []
        x = -wave
        while x <= w + wave:
            y = h / 2 + amp * math.sin(TAU * (x + phase) / wave)
            pts.append(f"{n(x)} {n(y)}")
            x += 8
        d = "M" + "L".join(pts)
        for blur in (True, False):
            parts.append(
                f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{width * (3 if blur else 1)}" stroke-opacity="{".35" if blur else ".9"}"'
                + (' filter="url(#blur)"' if blur else "")
                + f'><animateTransform attributeName="transform" type="translate" from="0 0" to="{wave} 0" dur="{dur}s" repeatCount="indefinite"/></path>'
            )
    parts.append("</g>")
    parts.append(f'<circle cx="{w / 2}" cy="{h / 2}" r="16" fill="url(#dot)"><animate attributeName="r" values="12;19;12" dur="3s" repeatCount="indefinite"/></circle></svg>')
    return "".join(parts)


def main():
    assets = {
        "singularity-hero.svg": hero(),
        "holo-core-states.svg": orb_states(),
        "voice-pipeline.svg": pipeline(),
        "speech-modes.svg": speech_modes(),
        "singularity-signal.svg": signal(),
    }
    for name, svg in assets.items():
        path = os.path.join(HERE, name)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write('<?xml version="1.0" encoding="UTF-8"?>\n' + svg + "\n")
        print(f"{name}: {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
