# BUG-064 Automated Tests

`PlayerReleasesLiveTest` in `:feature:player`:

| Test | Asserts |
| --- | --- |
| `leaving a film lets go of its engine and lets the same film load again` | `onLeft` on a film calls `discardEngine` once and `pause` never; loading the same film again prepares it |

The existing live-channel cases are unchanged and still assert `stop` on leaving.

## Run result

No Android SDK in this container: the test runs in CI. detekt 1.23.8 with the repo config and its
ktlint rules over `core/media` and `feature/player`: clean.
