# BUG-063 Implementation Plan

## Approach

1. `EngineFailure.receivedData` (default true); `onPlayerError` sets it from `bytesReceived`.
2. `classify`: `PARSING_CONTAINER_UNSUPPORTED` and `PARSING_CONTAINER_MALFORMED` with no data →
   `PROVIDER_REFUSED`; with data → `UNSUPPORTED_FORMAT` as before.
3. `healthyAccountVerdict`: at the connection limit, no status, no data, fault `TIMEOUT` or
   `OTHER` → `CONNECTION_LIMIT`.

## Files Touched

- `core/media/.../PlaybackDecisions.kt`, `core/media/.../Media3PlayerController.kt`
- `core/data/.../diagnostics/PlaybackVerdict.kt`
- Tests: `PlaybackDecisionsTest`, `PlaybackVerdictTest`
- `agile/items|plans|testing/BUG-063-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a panel that answers every request with an empty body is retried twice before the error.
  Inside the 12 s budget.
- Rollback: revert the branch commit.
