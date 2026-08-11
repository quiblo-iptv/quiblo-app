# Execution Plan — Quiblo v1.0

**Repo:** `quiblo-iptv/quiblo-app` · **Application ID:** `dev.quiblo.player` · **Namespace:** `dev.quiblo.*`

---

## 1. Stack

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin 2.x | |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) | Convention plugins in `build-logic/` to stop per-module drift |
| UI | Jetpack Compose + Material 3 | |
| Navigation | Navigation Compose, type-safe routes | |
| DI | **Koin** | Lighter and faster to iterate than Hilt; no KAPT/KSP tax. Hilt is the alternative if you prefer compile-time safety. |
| Async | Coroutines + Flow | |
| HTTP | Ktor Client (OkHttp engine) | Same coroutine idiom throughout; Retrofit is equally valid. One `OkHttpClient` for the whole app — the player shares its connection pool, or it pays a handshake per segment |
| JSON | kotlinx.serialization | Configure `isLenient` + `coerceInputValues` — panel APIs are inconsistent (AC-XT-06) |
| DB | Room | |
| Prefs | DataStore (Proto or Preferences) | |
| Secrets | EncryptedSharedPreferences / Jetpack Security | AC-XT-04 |
| Images | Coil 3 | |
| Player | Media3 / ExoPlayer | |
| Test | JUnit5, Turbine, MockK, Compose UI test, Robolectric | |
| Static analysis | detekt + ktlint + Android Lint | |
| CI | GitHub Actions | |

**Coming from Spring:** Koin modules map cleanly onto `@Configuration` classes, repositories onto `@Repository`, and Room DAOs onto Spring Data interfaces. The unfamiliar parts are the Android lifecycle and Compose's recomposition model — budget real time for both.

## 2. Module structure

```
:app                      — assembly, navigation graph, DI wiring, theme

:core:model               — pure Kotlin. Channel, VodItem, Series, Programme, Category, Source
:core:common              — Result types, dispatchers, extensions
:core:database            — Room entities, DAOs, migrations
:core:datastore           — settings, encrypted credentials
:core:network             — Ktor client, the shared OkHttpClient, error mapping
:core:media               — PlayerController interface + Media3 implementation
:core:data                — repositories; the only layer features talk to

:source:api               — MediaSource interface + shared DTO contract
:source:m3u               — M3U/M3U8 parser + tests
:source:xtream            — Xtream API client + tests

:feature:sources          — add/edit/delete playlists
:feature:live             — live channel browse + guide
:feature:vod              — movies
:feature:series           — series/seasons/episodes
:feature:player           — playback UI
:feature:favorites        — favourites
:feature:settings         — settings, export/import, licenses
```

**Rule:** `:core:*` and `:source:*` never depend on `:feature:*` and never import Compose (AC-NFR-06). This is what makes the phase-2 TV frontend cheap.

`:core:model` and `:source:*` should be plain Kotlin (JVM) modules, not Android modules — it makes their tests fast and keeps the door open for Kotlin Multiplatform later.

## 3. Milestones

Estimates assume solo part-time work. Adjust to your actual hours.

### M0 — Foundation (week 1)
- Repo, GPLv3 `LICENSE`, license headers, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
- Gradle version catalog, convention plugins, module skeleton
- CI: build + test + detekt on every push
- App shell: theme, navigation, empty screens
- **Exit:** `./gradlew build` green in CI; app installs and opens

### M1 — M3U ingestion (weeks 2–3)
- `:source:api` contract
- M3U parser: attributes, groups, malformed input, BOM, CRLF, streaming parse for large files
- Room schema + repository
- Add source by URL and by file (SAF)
- **Exit:** AC-PL-01 → 07 pass. Parser tests at 80%+, driven by a fixture corpus of deliberately broken playlists.

**Write the parser first and write it against nasty fixtures.** Real-world playlists are far dirtier than the spec suggests. This module is where a weekend disappears if you rush it.

### M2 — Playback (weeks 4–5)
- `PlayerController` abstraction over Media3
- Full-screen player, controls, auto-hide, rotation handling
- Audio focus, error handling, retry with backoff
- Track selection (audio, subtitles)
- **Exit:** AC-PLAY-01 → 10 pass

### M3 — Browse and favourites (week 6)
- Category filter, search, lazy grid/list at 20k entries
- Favourites, stable across refresh
- Logo loading
- **Exit:** AC-FAV-01 → 05, AC-PL-05 pass

### M4 — Xtream (weeks 7–8)
- API client, URL normalisation, auth, lenient deserialisation
- Live + VOD + Series category trees
- Encrypted credential storage
- EPG ingestion, storage, now/next rendering
- **Exit:** AC-XT-*, AC-EPG-* pass

### M5 — Settings, export/import, polish (week 9)
- Versioned export format, SAF write/read, import validation
- Licenses screen, README, screenshots
- Dark/light, RTL, string extraction
- **Exit:** AC-DATA-*, AC-LEGAL-*, AC-NFR-08/09 pass

### M6 — Release (week 10)
- ProGuard/R8 rules, size check, cold-start profiling
- Signed release build; keystore documented and backed up
- GitHub Actions release workflow: tag → build → attach APK + checksums
- Full AC sweep on two physical devices
- **Exit:** v1.0.0 tagged and published

## 4. Repository conventions

- Trunk-based, short-lived branches, squash merge
- Conventional Commits (`feat:`, `fix:`, `refactor:`) — enables changelog generation
- Semantic versioning
- Issue templates that forbid posting playlist URLs or credentials (AC-LEGAL-04); pin this in the README and enforce it in moderation
- `SECURITY.md` with a disclosure address

## 5. Risks

| Risk | Mitigation |
|---|---|
| Real-world M3U files break the parser | Fixture corpus built from deliberately malformed samples before writing the parser |
| Xtream panels disagree on response shapes | Lenient deserialisation, defensive defaults, never trust a field's declared type |
| 20k-entry lists stutter | Paged Room queries, stable keys in `LazyColumn`, no work in composition |
| The repo attracts piracy-adjacent issues and PRs | Explicit CONTRIBUTING policy, README posture statement, firm moderation from day one |
| Scope creep toward TV/DRM/recording mid-v1 | The freeze prompt. Amendments require an explicit dated entry. |
| Solo burnout across 10 weeks | Ship M1 and M2 as an internal alpha you actually use daily — early utility sustains motivation better than a distant v1 |

## 6. Phase 2 (post-v1, not in scope now)

Listed only so the architecture stays ready for it, in rough priority order:

1. Android TV / Google TV frontend — new `:feature-tv:*` modules over the same `:core`
2. XMLTV EPG, starting with the `url-tvg` attribute already present in many M3U headers
3. Catch-up / timeshift
4. ~~Multiple profiles~~, parental PIN — **profiles landed 2026-08-09** (FREEZE Amendment 6): favourites and resume positions per person, plus a guest that keeps nothing. The PIN half stays parked, deliberately
5. Picture-in-picture, background audio
6. Desktop (Compose Multiplatform JVM)
7. Web frontend for LG webOS / Tizen / Xbox — a separate TypeScript codebase against the same documented data contract
8. F-Droid submission (requires a reproducible build and fully FOSS dependencies)

---

## First three tasks

0. Claim the name before writing anything: a GitHub org — this is `quiblo-iptv`, because `quiblo` itself was taken — and check `quiblo.dev` / `quiblo.tv`. Confirm `dev.quiblo.player` is unused on Play and in F-Droid's package list — cheap now, painful to change after release.
1. `git init`, drop in the GPLv3 `LICENSE`, push the empty module skeleton with CI green.
2. Build the malformed-M3U fixture corpus — before writing a single line of parser code.
3. Write the M3U parser against it as a plain JVM module with no Android dependency at all.
