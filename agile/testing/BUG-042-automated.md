# BUG-042 Automated Tests

## Tests written

`SourceEditTest`, against a fake panel that accepts only one host and one password, a fake
credential store, and a `SourceDao` mock holding one row:

| Test | Layer | Asserts |
| --- | --- | --- |
| `a new address and password are saved under the same source and loaded with` | unit | Success for source 7; the trimmed URL and new credentials stored; the panel was asked with them |
| `an empty password keeps the stored one` | unit | Stored password unchanged |
| `a change the panel refuses puts everything back as it was` | unit | `Failure(Unauthorized)`; the row and the credentials equal what they were before |
| `the source is never deleted, so nothing keyed to it cascades away` | unit | `deleteById` never called |

The two edit screens are a form over `SourcesViewModel.editSource`, which only forwards to the
repository; they are in the sweep.

## Verified to fail without the fix

`editSource` reduced to a plain refresh — nothing saved, nothing restored — and `SourceEditTest`
re-run on 2026-10-03:

```
SourceEditTest > a new address and password are saved under the same source and loaded with() FAILED
SourceEditTest > a change the panel refuses puts everything back as it was() FAILED
4 tests completed, 2 failed
```

## Run result

`SourceEditTest` — **PASSED** (4) on branch `bugfix/BUG-042-edit-source` (local, 2026-10-03). Detekt
clean on `:core:data`, `:feature:sources`, `:app-tv`; both apps compile.
