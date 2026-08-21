**Splash Screen and Details Ambient of Quiblo**

A creative launch screen with version indicator, plus ambient lighting on movie and series detail screens.

**Created:** 2026-08-21, against commit on `main`.
**Ships as:** feature work across Mobile and Android TV.

---

## 1. Scope & Motivation

1. **Launch Experience (`QuibloSplashScreen`)**:
   - Deliver a sleek, cinematic splash screen with the signature animated Quiblo "Q" brand mark, title, tagline ("Free & Open Source IPTV"), and version indicator (`v$versionName`) cleanly positioned at the bottom right (`Alignment.BottomEnd`).
   - Wire into cold start for both Android Phone (`MainActivity.kt`) and Android TV (`TvApp.kt`) with smooth crossfade transitions into consent/profile gates.

2. **Ambient Lighting on Detail Screens**:
   - Extract dominant vibrant colors from movie and series poster artwork (`rememberAmbient(artworkUrl)`).
   - Render subtle radial ambient backdrop glow pools behind movie details (`TvMovieScreen.kt`, `MovieDetailScreen.kt`) and series details (`TvSeriesScreen.kt`, `SeriesDetailScreen.kt`).
   - Share the ambient backdrop engine via `feature:designsystem/Ambient.kt`.

---

## 2. Verification

- All unit tests pass across all modules (`./gradlew testDebugUnitTest`).
- Robolectric tests verify splash screen rendering, version tag display, and completion callback execution (`TvSplashScreenTest.kt`).
- Detekt, licensing checks, and full `check` suite pass cleanly with 0 warnings.
