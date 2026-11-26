# Quiblo — a free, open source IPTV player.
# Copyright (C) 2026 The Quiblo Authors
# Licensed under the GNU General Public License v3.0 or later. See LICENSE.
"""Generate the launcher icons and the TV banner from the mark's geometry.

The mark is a filled outline here (ring, minus the cut either side of the tail, plus the tail)
because a vector drawable cannot mask one stroke with another. Compose draws the same shape
with strokes in `QuibloMark.kt`; change both together.

    pip install shapely
    python3 tools/brand_vectors.py . /dev/null
"""
import sys
from shapely.geometry import box, LineString
from shapely.ops import unary_union

REPO = sys.argv[1]
SEG = 24

def rounded(x0, y0, x1, y1, r):
    return box(x0 + r, y0 + r, x1 - r, y1 - r).buffer(r, quad_segs=SEG)

ring = rounded(7.5, 7.5, 92.5, 92.5, 32.5).difference(rounded(20.5, 20.5, 79.5, 79.5, 19.5))
cut = LineString([(61, 61), (89, 89)]).buffer(10.5, quad_segs=SEG)
tail = LineString([(62, 62), (87, 87)]).buffer(6.5, quad_segs=SEG)
mark = unary_union([ring.difference(cut), tail])

def ring_path(coords):
    pts = list(coords)[:-1]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"

def path_of(geom):
    polys = getattr(geom, "geoms", [geom])
    out = []
    for p in polys:
        out.append(ring_path(p.exterior.coords))
        out.extend(ring_path(i.coords) for i in p.interiors)
    return " ".join(out)

MARK = path_of(mark)
COUNTER = "M40,20.5 H60 A19.5,19.5 0 0 1 79.5,40 V60 A19.5,19.5 0 0 1 60,79.5 H40 A19.5,19.5 0 0 1 20.5,60 V40 A19.5,19.5 0 0 1 40,20.5 Z"
print("mark bounds", mark.bounds, "path chars", len(MARK), file=sys.stderr)

# The name on the mark's grid, 297 by 103 with the baseline at 0: every bowl is a small screen.
# `QuibloWordmark.kt` draws the same strokes in Compose; change both together.
def bowl(left):
    right = left + 45.5
    return (f"M{left + 15},-47.75 H{right - 15} A15,15 0 0 1 {right},-32.75 V-21.25 "
            f"A15,15 0 0 1 {right - 15},-6.25 H{left + 15} A15,15 0 0 1 {left},-21.25 V-32.75 "
            f"A15,15 0 0 1 {left + 15},-47.75 Z")

WORD_STROKES = " ".join([
    bowl(6.25), "M51.75,-54 V25",
    "M72.25,-54 V-21.25 A15,15 0 0 0 87.25,-6.25 H102.75 A15,15 0 0 0 117.75,-21.25 V-54",
    "M117.75,-54 V0",
    "M138.25,-54 V0",
    "M158.75,-78 V0", bowl(158.75),
    "M224.75,-78 V0",
    bowl(245.25),
])
WORD_DOT = ("M135,-78 H141.5 A3,3 0 0 1 144.5,-75 V-68.5 A3,3 0 0 1 141.5,-65.5 H135 "
            "A3,3 0 0 1 132,-68.5 V-75 A3,3 0 0 1 135,-78 Z")

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.

{note}
  Generated from the mark's geometry: a 72-unit screen with corner radius 26 and a 13-unit
  stroke, its tail at 45 degrees with a 4-unit cut each side. See `QuibloMark.kt`, which
  draws the same thing in Compose. Change both together.
-->
"""
GLOW = """<aapt:attr name="android:fillColor">
                <gradient
                    android:type="radial"
                    android:centerX="50"
                    android:centerY="54"
                    android:gradientRadius="33">
                    <item android:offset="0" android:color="#FFFFD58A" />
                    <item android:offset="0.45" android:color="#FFFFB648" />
                    <item android:offset="0.8" android:color="#8CFF6A3D" />
                    <item android:offset="1" android:color="#00FF6A3D" />
                </gradient>
            </aapt:attr>"""

def foreground():
    return HEADER.format(note="""  The launcher mark: the screen-shaped Q with the warm light inside it. Drawn into the middle
  50 units of the 108-unit adaptive canvas, inside the 66-unit safe zone every launcher mask
  keeps.""") + f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group android:translateX="29" android:translateY="29" android:scaleX="0.5" android:scaleY="0.5">
        <!-- The light inside the screen. -->
        <path android:pathData="{COUNTER}">
            {GLOW}
        </path>
        <!-- The mark. -->
        <path
            android:fillColor="#FFF3F1EC"
            android:pathData="{MARK}" />
    </group>
</vector>
"""

def monochrome():
    return HEADER.format(note="""  The themed-icon layer: the mark alone, in one colour the launcher tints. Same placement as
  the foreground, so a themed icon and a normal one are the same size.""") + f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group android:translateX="29" android:translateY="29" android:scaleX="0.5" android:scaleY="0.5">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="{MARK}" />
    </group>
</vector>
"""

def background():
    return HEADER.format(note="""  The launcher background: ink, faintly warmed in the middle by the light the mark holds.""") + """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="54"
                android:gradientRadius="76">
                <item android:offset="0" android:color="#FF1D1A16" />
                <item android:offset="1" android:color="#FF0B0C10" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""

def banner():
    return HEADER.format(note="""  The Android TV launcher banner: 320x180, as leanback requires, its own artwork at its own
  aspect ratio rather than the square icon letterboxed. The mark with its light, and the name
  drawn on the mark's grid as strokes, so nothing depends on a font being installed.""") + f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="320dp"
    android:height="180dp"
    android:viewportWidth="320"
    android:viewportHeight="180">

    <!-- Full bleed, so there is no edge left for a launcher to letterbox. -->
    <path android:fillColor="#FF0B0C10" android:pathData="M0,0 H320 V180 H0 Z" />

    <!-- The light, spilling from the mark onto the tile. -->
    <path android:pathData="M0,0 H320 V180 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="90"
                android:centerY="90"
                android:gradientRadius="144">
                <item android:offset="0" android:color="#38FFB648" />
                <item android:offset="1" android:color="#00FFB648" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- The mark, 80 units across, centred on y=90. -->
    <group android:translateX="48" android:translateY="50" android:scaleX="0.8" android:scaleY="0.8">
        <path android:pathData="{COUNTER}">
            {GLOW}
        </path>
        <path android:fillColor="#FFF3F1EC" android:pathData="{MARK}" />
    </group>

    <!-- The name, 137 units across, centred on y=90. -->
    <group android:translateX="140" android:translateY="102" android:scaleX="0.46" android:scaleY="0.46">
        <path
            android:strokeColor="#FFF3F1EC"
            android:strokeWidth="12.5"
            android:pathData="{WORD_STROKES}" />
        <path android:fillColor="#FFF3F1EC" android:pathData="{WORD_DOT}" />
    </group>
</vector>
"""

for app in ("app", "app-tv"):
    d = f"{REPO}/{app}/src/main/res/drawable"
    open(f"{d}/ic_launcher_foreground.xml", "w", encoding="utf-8", newline="
").write(foreground())
    open(f"{d}/ic_launcher_monochrome.xml", "w", encoding="utf-8", newline="
").write(monochrome())
    open(f"{d}/ic_launcher_background.xml", "w", encoding="utf-8", newline="
").write(background())
open(f"{REPO}/app-tv/src/main/res/drawable/tv_banner.xml", "w", encoding="utf-8", newline="
").write(banner())
open(sys.argv[2], "w").write(MARK)
