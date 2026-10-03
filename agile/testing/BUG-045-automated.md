# BUG-045 Automated Tests

## Tests written

`ProviderSubtitleFetchTest` in `:core:data`:

| Test | Asserts |
| --- | --- |
| `a subtitle that arrives is played from a local copy, not from the panel` | `file://` URI, content written, label and language kept |
| `its format is read from what arrived, not from its name` | WebVTT served as `.srt` → `text/vtt` |
| `a dead link is unavailable, and nothing more` | 404 → `Unavailable` |
| `a panel's error page is not subtitles` | HTML containing a cue line → `Unavailable` |
| `a link that turns out to be a film is refused at the cap` | 8 MiB + 1 → `Unavailable` |
| `a server that never answers is given up on` | no answer → `Unavailable` after the 5 s timeout (virtual time) |

`PlayerOffersPanelSubtitlesTest` in `:feature:player`:

| Test | Asserts |
| --- | --- |
| `a film starts without the panel's subtitle, which is offered instead` | prepared item has no subtitles; the panel's is offered |
| `choosing it fetches it and restarts the film where it was, showing it` | one prepare, local copy with `selectOnStart`, same position, offer gone |
| `one that does not arrive leaves the film alone and says so` | no prepare, `UNAVAILABLE`, `SUBTITLE_FAILED` |
| `a subtitle the engine dropped is explained` | `subtitleDropped` → `SUBTITLE_FAILED` |

`TrackMenuTest`: offers appear with no tracks loaded (no "off"), and after the engine's tracks.

The controller's drop-and-re-prepare and the default flag need an engine; they are in the sweep.

## Verified to fail without the fix

Panel subtitles put on the item as before, the offer path short-circuited, and the fetch handing
back the remote URL unchanged, re-run on 2026-10-03:

```
:core:data       ProviderSubtitleFetchTest        6 tests completed, 6 failed
:feature:player  PlayerOffersPanelSubtitlesTest   4 tests completed, 3 failed
```

The fourth covers `subtitleDropped`, which did not exist before.

## Run result

On branch `bugfix/BUG-045-dead-subtitle-stops-film` (local, 2026-10-03), all **PASSED**:
`:core:data:testDebugUnitTest` (233), `:feature:player:testDebugUnitTest` (46),
`:core:media:testDebugUnitTest` (31), `:source:api:test`, `:source:xtream:test`. `:app` and
`:app-tv` compile; detekt clean on every touched module; `:feature:player:lintDebug` clean.
