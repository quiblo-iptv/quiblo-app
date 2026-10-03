# BUG-043 Automated Tests

## Tests written

| Test | Module | Asserts |
| --- | --- | --- |
| `automatic asks for hls when the account may use it` | `:source:xtream` | `{m3u8, ts}` and `{m3u8}` → `m3u8` |
| `automatic asks for ts when hls is not allowed, or nobody said` | `:source:xtream` | `{ts, rtmp}`, null, empty → `ts` |
| `a choice made by hand is kept whatever the panel says` | `:source:xtream` | TS and HLS override the panel |
| `the choice reaches the url, and only for live` | `:source:xtream` | Live gets `.m3u8`; a film keeps `.mkv` |
| `allowed formats are read whatever case they arrive in` | `:source:xtream` | `["M3U8"," ts ","rtmp"]` → lower-cased, trimmed |
| `a panel that sends something other than a list has said nothing` | `:source:xtream` | A string or a missing field → null |
| `a load reports the formats the account may use, for the source to keep` | `:source:xtream` | `SourceResult.Success.allowedLiveFormats` |
| `the formats a refresh reports are kept with the source (BUG-043)` | `:core:data` | `setAllowedLiveFormats(7, "m3u8,ts")` |
| `a live format chosen while editing is saved with the source (BUG-043)` | `:core:data` | Row's `liveFormat` becomes `HLS` |
| `an existing source becomes automatic, with no formats known yet` | `:core:database` | Over the exported v25 schema: `AUTO`, null, other columns intact |
| `a source added after the upgrade gets the default without naming it` | `:core:database` | Column default is `AUTO` |

`ExportedSchema.kt` is the helper behind the migration test: an in-memory database built from an
exported schema JSON, usable on Windows where `MigrationTestHelper` is not.

## Verified to fail without the fix

`XtreamUrl.liveExtension` reduced to always `ts` — the old behaviour — and `:source:xtream:test`
re-run on 2026-10-03:

```
XtreamLiveFormatTest > the choice reaches the url, and only for live() FAILED
XtreamLiveFormatTest > automatic asks for hls when the account may use it() FAILED
XtreamLiveFormatTest > a choice made by hand is kept whatever the panel says() FAILED
128 tests completed, 3 failed
```

## Run result

`:source:xtream:test`, `:core:data:testDebugUnitTest`, `LiveFormatMigrationTest` — **PASSED** on
branch `bugfix/BUG-043-live-format` (local, 2026-10-03). `detektAll` clean; both apps compile.
`MigrationTest`'s end-to-end run to 26 is CI's (Windows path issue, see `BUG-041`).
