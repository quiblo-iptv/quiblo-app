# Quiblo — a free, open source IPTV player.
# Copyright (C) 2026 The Quiblo Authors
# Licensed under the GNU General Public License v3.0 or later. See LICENSE.
"""Generate the launcher icons and the TV banner from the mark's geometry.

The mark is a filled outline here (ring, minus the cut either side of the tail, plus the tail)
because a vector drawable cannot mask one stroke with another. Compose draws the same shape
with strokes in `QuibloMark.kt`; change both together.

    pip install shapely fonttools
    python3 tools/brand_vectors.py . feature/designsystem/src/main/res/font/outfit_semibold.ttf /dev/null
"""
import sys
from shapely.geometry import box, LineString
from shapely.ops import unary_union
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

REPO = sys.argv[1]
FONT = sys.argv[2]
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

def wordmark(text, size, tracking_em):
    f = TTFont(FONT)
    gs = f.getGlyphSet(); cmap = f.getBestCmap(); upem = f["head"].unitsPerEm
    s = size / upem; x = 0.0; parts = []
    for ch in text:
        g = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        gs[g].draw(TransformPen(pen, (s, 0, 0, -s, x, 0)))
        parts.append(pen.getCommands())
        x += f["hmtx"][g][0] * s + tracking_em * size
    return " ".join(parts), x - tracking_em * size

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
    word, width = wordmark("quiblo", 50, -0.035)
    return HEADER.format(note="""  The Android TV launcher banner: 320x180, as leanback requires, its own artwork at its own
  aspect ratio rather than the square icon letterboxed. The mark with its light, and the name
  in Outfit SemiBold as outlines, so nothing depends on a font being installed.""") + f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
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

    <!-- The name, baseline at y=108. -->
    <group android:translateX="142" android:translateY="108">
        <path android:fillColor="#FFF3F1EC" android:pathData="{word}" />
    </group>
</vector>
"""

for app in ("app", "app-tv"):
    d = f"{REPO}/{app}/src/main/res/drawable"
    open(f"{d}/ic_launcher_foreground.xml", "w").write(foreground())
    open(f"{d}/ic_launcher_monochrome.xml", "w").write(monochrome())
    open(f"{d}/ic_launcher_background.xml", "w").write(background())
open(f"{REPO}/app-tv/src/main/res/drawable/tv_banner.xml", "w").write(banner())
open(sys.argv[3], "w").write(MARK)
w, width = wordmark("quiblo", 50, -0.035)
print("banner word width", round(width, 1), "ends at", round(138 + width, 1), file=sys.stderr)
