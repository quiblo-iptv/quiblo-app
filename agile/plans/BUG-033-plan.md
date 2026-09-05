# BUG-033 Implementation Plan

## Approach

1. Replace `XtreamSource.categories()` with `grouping(streams, result)`, which returns the
   `ApiResult` through rather than flattening a failure to an empty list. It still records a
   backoff on `ProviderBlocked`, and it still answers `Ok(emptyList())` when the stream list is
   empty — an account with no films needs no film categories.
2. Introduce `BatchOutcome` (`Loaded` / `Refused`) so a refusal can carry the panel's own error.
   `optionalBatch` returns it instead of a nullable `Batch`, which had forced every refusal to be
   reported as `ProviderBlocked`.
3. `collect()` stops evaluating `mapLive(...)` as an argument, so the grouping is checked before
   anything is mapped.
4. `withOptionalCatalogues()` returns the carried error for VOD and for series.
5. Nothing in `SourceRepository` changes: `store()` already leaves the stored catalogue alone on a
   failure, which is the behaviour wanted — the household keeps the categories it had.

## Files Touched

- `source/xtream/src/main/kotlin/dev/quiblo/source/xtream/XtreamSource.kt`
- `source/xtream/src/test/kotlin/dev/quiblo/source/xtream/XtreamSourceTest.kt`
- `agile/items|plans|testing/BUG-033-*`
- `CHANGELOG.md`

## Risks & Rollback

- Risk: a panel that always 404s its category endpoints now fails to load at all where it
  previously loaded ungrouped. Mitigation: the empty-stream-list carve-out, which covers the
  common shape of that — a live-only account. A panel that returns films and refuses to say what
  they are is genuinely broken, and a failed refresh that keeps the old catalogue is the better
  outcome than a correct catalogue overwritten with one heap.
- Rollback: revert the branch commit. Behaviour returns to swallowing the failure.
