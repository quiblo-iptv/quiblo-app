**First Launch, the Timeline, and Updating Itself of Quiblo — the three things a television could not do**

A fresh install ended at the terms and dropped somebody into an empty app whose next step was
four presses deep in Settings. A film could only be crossed one seek button press at a time, one
re-buffer per press. And an app distributed as an APK, on a device with no store, had no way of
telling anybody that three releases had gone by. This round does those three, and reads the
project against three competitors.

**Created:** 2026-08-17, against `fb87109`.
**Ships as:** `1.0.0` work, `feat:`. A merge to `main` cuts a release; it is not merged until that
is asked for.

**Branch:** `feat/026-first-launch-timeline-updates`, on top of `feat/025-recommender`.

---

## What was asked for, and what was built instead

**"The dialog on first launch" is a page, not a dialog.** The television app has no modals and
that is a standing decision, not an oversight: a floating box over a screen a remote cannot reach
around is the one interaction pattern this project has ruled out. The consent flow was already
two pages; it is three now, and the third is where a playlist is asked for.

**"Make the text inputs not activate until I press OK" was already true.** `TvTextField` has had
two stages since `019` — a resting box that is focusable and inert, and an editor composed only
after a centre press — and the playlist form uses it. The author confirmed the request was made
untested, and the clause was dropped rather than built twice.

**"Rewind that stacks each click on the other" is a scrub, not a faster seek.** Each press moves a
pending mark, the bar draws it, and the player is told once when the pressing stops. That is what
"stacking" means and it is also the only version that does not re-buffer eight times in two
seconds.

**`DownloadManager` was planned and Ktor was used.** The plan named `DownloadManager` for the APK
download. Ktor is already in `:core:network` with a shared connection pool, and `:core:network` is
where the architecture keeps HTTP; a second HTTP mechanism, a broadcast receiver and a cursor
poll would have been three moving parts to avoid one dependency the app already has. The download
streams to disk rather than buffering, so the memory argument for `DownloadManager` does not hold
either.

## Decisions, and what they rule out

**Consent is accepted on the way out of page three, by whichever button is pressed.** It used to
be accepted at the end of the terms. Moving it means a viewer can never save a source while the
app still believes nobody has agreed to anything — a state nothing else here expects. The cost is
that the flow cannot be left half way; there is no way to accept the terms and then abandon the
app before page three, which is the same as before, since there was never a way to leave.

**The add-source form moved out of `TvSourcesScreen` and is shared.** Two screens now show it.
A copy each is how a fix lands in one and is forgotten in the other, which is the failure
`TvTextField` was written to end and which this project has already paid for once.

**The timeline commits after 500ms of quiet, and the mark survives the commit.** Clearing the mark
on the commit is the version that snaps the bar backwards for as long as the position poll takes
to notice the seek. It is cleared instead by the player's own position arriving within three
seconds of the target — three because a seek lands on a keyframe rather than on a millisecond, and
anything tighter never settles at all.

**The first four presses of a run are worth exactly the interval chosen in Settings.** A
correction — "back a bit, I missed that line" — is the case that interval exists for, and
accelerating it would take the setting away. Acceleration starts at the fifth press.

**A release with no checksum is refused rather than installed with a warning.** This is the one
place Quiblo downloads an executable and hands it to the package installer. Every release the lane
has published carries a `.sha256`; a release that does not is a release something has gone wrong
with, and "are you sure?" on a television is a button people press. A file that fails its checksum
is deleted rather than left where a file manager can find it.

**Nothing checks for updates on its own.** No launch check, no schedule, no worker. The row is a
button. `tv_consent_terms_body` promises that nothing leaves the device except to servers the
viewer named themselves, and this project's own releases page joins that list only while somebody
is standing in front of the screen asking.

**`REQUEST_INSTALL_PACKAGES` is declared, and it is a store-restricted permission.** Quiblo is
distributed as an APK from its releases page and a television has no store to update it from, so
an app that cannot say "you are three versions behind" never gets updated at all. The permission
only lets Quiblo *ask*; the system's own "allow installs from this source?" prompt is the consent
that matters and cannot be skipped. **If Quiblo is ever submitted to a store, this declaration
needs a policy answer** — it is flagged here rather than discovered at submission.

## What was built

**First launch — `TvConsentScreen`.** Two pages became three, and the boolean became a
`ConsentPage` enum. Page three offers **Add a playlist** and **Skip for later**; adding composes
the shared form inline, waits for the source to load, and says how many channels arrived or that
it failed — with **Try again** and **Skip for later** on the failure.

**The playlist screen — `TvSourcesScreen`, `TvAddSourceForm`.** The form and the pressable row
moved to files of their own (`TvAddSourceForm.kt`, `ui/common/TvFocusRow.kt`). Everything is
centred on a fixed 720dp column instead of sixty per cent of an unknown panel. Save carries the
travelling light at 0.6 intensity — dimmer than Search's, because Search is the only thing on its
screen and Save sits beside a form somebody is still reading.

**The timeline — `TvScrubState`, `Progress` in `TvPlayerControls`.** The bar takes focus, sits
between the transport row and the options row in the D-pad order, consumes left and right, passes
up and down, and is not focusable on a live stream. It draws the pending mark ahead of the real
position and its measured height does not change while scrubbing. The arithmetic is a class with
no Compose and no player in it.

**Check for updates — `ReleaseChecker`, `ReleaseDownloader`, `TvUpdateViewModel`, `TvUpdateRow`.**
Reads `releases/latest`, compares by semantic version, picks the `quiblo-tv-` asset out of a
release holding both apps, downloads it, verifies the published SHA-256, and opens the system
installer through a `FileProvider`. Seven visible states and seven distinct failures, each with
its own line.

**`docs/COMPETITORS.md`** — Extreme-InfiniTV, IPTVnator and ZUI IPTV Player read against Quiblo.

## One thing found on the way

**`:core:network` had the `kotlinx.serialization` dependency and not the plugin.** `@Serializable`
compiled, generated nothing, and every decode failed at runtime — reported by the checker as a
malformed release, which is exactly what a genuinely malformed payload looks like. Five tests
caught it; nothing else would have, because the failure path is indistinguishable from a real one.
The plugin is now applied.

## Acceptance criteria

| | Criterion |
| :---- | :---- |
| **AC-026-01** | A fresh install reaches a third consent page offering a playlist and a way past it |
| **AC-026-02** | Both controls on that page accept the terms; neither can be reached without the terms page first |
| **AC-026-03** | A playlist added on that page is loaded before the app opens, and its outcome is stated |
| **AC-026-04** | The playlist screen's column is centred on the panel and narrower than it |
| **AC-026-05** | Save on the add form carries the travelling light while focused, dimmer than Search's |
| **AC-026-06** | The player's timeline takes focus, one press below play/pause, and can be left with up or down |
| **AC-026-07** | Left and right on the focused timeline move a pending mark and do not move focus |
| **AC-026-08** | A run of presses produces exactly one seek, to the sum of them |
| **AC-026-09** | A run of more than four presses accelerates; four or fewer do not |
| **AC-026-10** | A live stream has no timeline and down behaves as it did |
| **AC-026-11** | Check for updates names a newer release, and says so when there is none |
| **AC-026-12** | The television APK is chosen from a release containing both applications |
| **AC-026-13** | A download whose checksum does not match is deleted and is not offered for install |
| **AC-026-14** | A release with no published checksum is not downloaded |
| **AC-026-15** | Being offline is reported as offline, never as up to date |
| **AC-026-16** | Nothing contacts `api.github.com` except the button |

## Testing

**Automated**, all on the JVM:

| Test | Proves |
| :---- | :---- |
| `TvScrubStateTest` (11) | Stacking, acceleration and its absence, both clamps, commit-once, settle, cancel |
| `TvTimelineFocusTest` (5) | Reachable, leavable, left/right consumed, one seek per run, none on live |
| `TvConsentReachableTest` (5) | Three pages by centre alone, both controls on page three, terms before acceptance, no decline |
| `TvSourcesCentredTest` (2) | Centred against the **root**, and narrower than the panel |
| `AppVersionTest` (6) | 0.10.0 > 0.9.0, the `v` prefix, pre-release order, unreadable tags refused |
| `ReleaseCheckerTest` (9) | The TV asset out of a four-asset release, up-to-date, ahead, no asset, offline, unreachable, error status, malformed body, unreadable tag |
| `ReleaseDownloaderTest` (7) | Match kept, `sha256sum` column ignored, mismatch deleted, no checksum refused, both failure paths leave nothing behind |

`./gradlew detekt test` is green across every module. `:app-tv:assembleDebug` builds.

**Manual:** seventeen tickets, `A19` in
[`docs/TESTING-REQUIRED.md`](../docs/TESTING-REQUIRED.md). Three need a fresh install; `A19.8`
is the one that decides whether the timeline was worth doing.

## Open, and owned by the author

- **Nothing here has been on a panel.** Everything above is JVM-measured. `A19.5`, `A19.6`,
  `A19.8` and `A19.10` are judgements a test cannot make — whether the column looks centred,
  whether the light reads as a light, whether a run of presses feels like one movement, whether
  the bar visibly snaps.
- **The 500ms commit delay and the four-press acceleration threshold are guesses tuned by
  reasoning, not by a remote.** They are two constants in `TvScrubState` and `TvPlayerControls`
  and are meant to be changed after `A19.8` and `A19.9`.
- **`REQUEST_INSTALL_PACKAGES` and any future store submission** — see above.
- **The update check is television only.** The phone has the same problem where it is sideloaded
  and does not have the answer. `TvUpdateViewModel` is the only TV-owned ViewModel in the graph;
  when the phone wants this, it moves into a shared module rather than being copied.
- **`docs/COMPETITORS.md` argues against competing on features**, which is a strategy call and is
  the author's to accept or reject.
