# BUG-044 Automated Tests

## Tests written

`HlsWithoutExtensionTest` in `:core:media`:

| Test | Layer | Asserts |
| --- | --- | --- |
| `a playlist named in the query is hls` | unit | `?file=index.m3u8`, `&output=m3u8`, `?type=m3u8&…` → HLS |
| `a playlist at the end of the path is hls too, whatever its case` | unit | `INDEX.M3U8?token` → HLS |
| `nothing else is called hls` | unit | `play.php?id=12`, `.mkv`, `.ts`, a directory called `m3u8s`, `notm3u8` → null |
| `an extension-less stream with no container is tried once as hls` | unit | `play.php?id=12`, `/hls/12` → retry |
| `but only once` | unit | Already tried → no |
| `not when the stream said what it was` | unit | A declared type → no |
| `not when the path names a container the engine already knows` | unit | `.mkv`, `.mp4`, `.ts`, `.m3u8` → no |
| `not for any other failure` | unit | Malformed container, bad status, decoder → no |

Setting the type on the media item and re-preparing need an engine; they are in the sweep.

## Verified to fail without the fix

Path-only detection (what the engine does) and no fallback, `:core:media:testDebugUnitTest`
re-run on 2026-10-03:

```
HlsWithoutExtensionTest > an extension-less stream with no container is tried once as hls() FAILED
HlsWithoutExtensionTest > a playlist named in the query is hls() FAILED
31 tests completed, 2 failed
```

## Run result

`:core:media:testDebugUnitTest` (31) — **PASSED** on branch `bugfix/BUG-044-hls-without-extension`
(local, 2026-10-03). `:core:media:detekt` clean.
