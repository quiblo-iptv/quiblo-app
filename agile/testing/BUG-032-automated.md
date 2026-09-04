# BUG-032 Automated Tests

## Tests written

| Test | Layer | Asserts |
| --- | --- | --- |
| `TvPlayerBackKeysTest` — track menu first | unit | Open track menu → `CloseTrackMenu`, key consumed |
| `TvPlayerBackKeysTest` — controls hide | unit | Controls up → `HideControls`, not Exit (AC-TV-06) |
| `TvPlayerBackKeysTest` — next-episode dismiss | unit | Offer up, controls down → `DismissNextEpisode` |
| `TvPlayerBackKeysTest` — exit | unit | Nothing up → `Exit`, key **not** consumed |
| `TvControlsFocusRepairTest` (existing) | compose | Repair still recovers focus when a control disappears |

## Coverage

Touched decision logic is fully branched in `TvPlayerBackKeysTest`. Screen wiring is
the dual call sites of that function; no padding tests on Compose plumbing alone.

## Run result

`:app-tv:testDebugUnitTest` — `TvPlayerBackKeysTest` and `TvControlsFocusRepairTest` —
**PASSED** on branch `bugfix/BUG-032-player-back-hides-controls` (local, 2026-09-05).
