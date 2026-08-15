**The Television Made To Feel Like One Thing of Quiblo**

`021` fixed what was broken. This round is about what the television *feels* like — three pieces
of light and a set of faces — plus one defect reported several times and never pinned down, one
piece of behaviour that never worked the way anybody assumed, and a question about reach.

**Created:** 2026-08-16, against `8ec3055` on `main` (`v0.18.0`), with `021`'s four branches still
unmerged.
**Ships as:** `1.0.0` work. Five `feat:` items and one `fix:`.

| # | Item | Branch | Cut from |
| :--- | :--- | :--- | :--- |
| 1 | `FEAT-025` — the generated avatars are shapes, not faces | `feature/FEAT-025-beam-avatars` | `main` |
| 2 | `FEAT-026` — the browse ambient is slow now the player's is not | `feature/FEAT-026-ambient-everywhere` | `FEAT-024` |
| 3 | `FEAT-027` — the ambient does not clear on Search, and Search has no light of its own | `feature/FEAT-027-search-glow` | `FEAT-026` |
| 4 | `BUG-028` — the gear and the face are sometimes not highlighted at all | `bugfix/BUG-028-bar-focus-race` | `main` |
| 5 | `FEAT-029` — backing out on Search does not close the app | `feature/FEAT-029-back-to-exit` | `BUG-028` |
| 6 | `FEAT-030` — touch, and phones | `feature/FEAT-030-touch-and-phones` | `FEAT-029` |

**The wheel stayed with the author** (Amendment 4), so nothing here was reproduced on a device.
Item 4 is a race, and the diagnosis below says how it is caught headlessly instead.

---

## 1. `FEAT-025` — beam, not bauhaus

**Reported:** *"change icon generation from this ugly types to faces (same library idk what it was
called)."*

The library is **boring-avatars**, ported rather than depended on — it is a React component
library, so there is no artefact to declare and the MIT obligation lands on the source file. What
was ported was its **bauhaus** variant: four coloured shapes arranged by a hash. The face variant
is **beam**: a tile, a shape on it, two eyes and a mouth, all placed by the same hash.

**Replaced for everyone, and the prefix did not change.** A profile stores `boring:<seed>`, so
switching what that prefix draws turns every existing generated avatar into a face on next launch
— which is the point, since the shapes are what "ugly" refers to. A second prefix would have kept
the old shapes alive forever in the one place they were least wanted: on the profiles that already
existed. The seed is untouched, so the same profile still gets the same face on every device and
after a restore.

**Four helpers were already right and are shared verbatim.** `boringHash`, `digitAt`, `booleanAt`
and `unitOf` are the same four functions beam uses, including the `Long` widening before `abs`
that stops one name in four billion indexing the palette backwards. What is new is `beamFace()`
and the drawing.

**Three details in the port are each one test**, because each of them fails as a face that is
slightly wrong rather than as an error:

- **The nudge.** A translation under 5 is pushed out by `36 / 9`; one at 5 or above is left alone.
- **The follow, and it is strictly greater.** A face follows its tile at half only when the tile
  has moved *past* `36 / 6`. `Sara#0` lands on exactly 6, so it does not follow — a port reading
  `>=` is a unit out on every seed that lands on the boundary.
- **The contrast rule.** Black or white by YIQ luma against a threshold of 128, read off the
  eight-bit channels via `toArgb` rather than Compose's floats. One of the five palette entries
  rounds the other way if it is read as floats, and the face disappears into its own tile.

**Every expected value came out of their JavaScript**, run here rather than reasoned about — which
is the standard the existing test file already set, and the only kind of assertion worth making
about a port. Thirteen cases; the seven that describe a shape are red against bauhaus by
construction.

Two SVG rules had to be transcribed rather than approximated:

- `scale(n)` scales from the origin, not the centre — pivoting on the middle instead would grow
  the tile symmetrically and lose the off-centre look the nudge exists to produce.
- `a1,0.75 0 0,0 10,0` is an arc whose radii are far too small to reach its end point, and SVG
  scales such radii up until they exactly do. The closed mouth is therefore a half-ellipse of
  `5,3.75`, not of `1,0.75`.

Files: `feature/designsystem/BoringAvatar.kt` and its test. Nothing else — `ProfileAvatar`,
`generatedAvatarKey` and both chooser screens go through the same two functions.

---

## 2 to 6

*Not yet implemented. See the round's plan; each lands on its own branch and is written up here as
it does.*
