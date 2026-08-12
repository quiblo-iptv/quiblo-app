<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Sweep results — 2026-08-12

**The first sweep anyone has run against a real device in one sitting.** Raw results below,
unedited, exactly as they came back. The reading of them is above it, because the raw table
undercounts the useful findings and overcounts the failures.

**Frozen on purpose.** This page is the record of one run. It is not updated as things get
fixed — the fixes are tracked where the defects are tracked, and a results page that gets
edited afterwards stops being evidence of what was seen on the day.

Run by Mahmoud against `v0.5.0` on a **Haier television** and a **Lenovo Tab One**.

---

## What the numbers say, and what they hide

`Pass 26 · Fail 8 · Blocked 37 · Not run 12`

**Both large numbers are misleading:**

- **37 blocked is mostly one missing thing.** Sessions 4, 5 and 6 are blocked almost entirely on
  having no working provider account and no real streams — `STOPPERS.md` S2 and S3, which have
  been the top of that page since it was written. **Session 5 is worse than blocked: it is
  impossible.** Device row B is a second phone, and there is only one.
- **8 failures are not 8 defects.** Six are real. One is a design decision being reversed. One is
  a row that could not be read.

**Five rows were lost to instructions the tester could not act on** — 1.4, 3.4, 3.8, 4.3 and 7.6.
That is a documentation failure and it cost more of this sitting than any bug did. `SWEEP-PLAN.md`
is written for somebody who has not seen the codebase, and on those five rows it was not.

## The six real defects

| From | What was seen | Standing |
| :---- | :---- | :---- |
| **1.5** | The chooser is skipped when the app resumes. It only asks after a force-stop | **New — #025.** `beginSession()` runs in `Application.onCreate`, so a process that was never killed never re-asks. `012` #016 is not closed |
| **2.1** | #015 again: the screen opens correct, then clips when focus moves to Play/Resume | **Third rejection**, and a *different* mechanism from the first two. Opening is fixed; a later focus move is not |
| **2.10** | #019: series titles still cropped, about half a line | Rejected. Was measured on the JVM and passed there |
| **2.11** | #018: the genre chips take **8 to 10 seconds** | Passes as written — the wait is explained — but the number is the one `012` deferred the SQL fix waiting for. It now exists |
| **3.1** | The settings button is hard to focus with the remote | **New — #026.** `AC-TV-01` fails on it |
| **7.5** | INC-F3 works on the phone and is absent on the television | Was `STOPPERS.md` **S12**. **Overruled on 2026-08-12: build it.** The stopper asked for a decision and this is the decision |

## The two that are not defects

- **7.4, INC-F8.** Marked fail, and it is a **reversal rather than a fault**: the request is a
  *fixed-height container with the list scrolling inside it*, on both apps. `015` specified the
  opposite — one scroller, no inner box — and built that. The tester's call stands; `015`'s
  reasoning about a scroller inside a scroller now has to be answered rather than repeated.
- **7.6, INC-E4.** Marked fail because the row could not be read. **The corner radius is
  untested**, not rejected.

## What was asked for rather than reported

- **7.3** — avatars pass, and the arrangement is to be replaced with one the tester supplies.
- **2.3** — #021's video: a wireless debugging session will be opened rather than the tester
  filming it.
- **Sessions 4 and 6** — to be driven over `adb` against the tester's tablet rather than by hand.

## Two prerequisites that were never actually blocked

Both were listed as not confirmed, and both already existed:

- **The 20,000-entry M3U** — `tools/gen_playlist.py` generates it, no account needed.
- **The `0.2.0` release-key build** — staged since 2026-08-11 at
  `~/Dev/mywrok/quiblo/sweep-artefacts/`, with checksums. See `STOPPERS.md` S9.

Neither was hard to get and both stopped a row. **A prerequisite nobody can find is a
prerequisite nobody has**, which is a plan failure rather than a preparation failure.

---

# Quiblo acceptance sweep — results

- **Tester:** Mahmoud
- **Build tested:** _not recorded_
- **Date:** _not recorded_
- **Devices:** Hiar Tv/Lenovo Tab One

**Pass 26 · Fail 8 · Blocked 37 · Not run 12**

## Prerequisites not confirmed

- A 20,000-entry M3U
- A file with two audio tracks
- The 0.2.0 release-key build
- Every mouse unpaired from the television

## Session 1 — Television, fresh install

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 1.1 | AC-TV-04 | **Pass** | — |
| 1.2 | AC-LEGAL-06 | **Pass** | — |
| 1.3 | AC-LEGAL-07 | **Pass** | — |
| 1.4 | AC-LEGAL-06 | **Blocked** | I don't understand this test |
| 1.5 | AC-PROF-01 | **Fail** | sometimes it appears and some other times the app is not closed so it opens my profile directly then i have to force close it to get who's watching screen again |
| 1.6 | AC-LEGAL-08 | **Not run** | — |
| 1.7 | AC-LEGAL-08 | **Not run** | — |
| 1.8 | AC-TV-08 | **Not run** | — |
| 1.9 | AC-LEGAL-03 | **Pass** | — |
| 1.10 | — | **Pass** | — |
| 1.11 | AC-LEGAL-03 | **Pass** | — |

## Session 2 — Television, the twelve round-3 defects

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 2.1 | AC-TV-11/12 | **Fail** | at first poster and titles shows normally but once focuses the play or resume button it's clipped again with no way to show title |
| 2.2 | AC-TV-11 | **Pass** | — |
| 2.3 | AC-TV-10 | **Blocked** | cannot record by myself ill open wireless debug session for u to record |
| 2.4 | AC-TV-03 | **Pass** | — |
| 2.5 | AC-PROF-01 | **Pass** | but it must be force stopped ! back don't stop it |
| 2.6 | AC-TV-02 | **Pass** | — |
| 2.7 | AC-PLAY-01 | **Pass** | — |
| 2.8 | AC-TV-11/12 | **Pass** | — |
| 2.9 | AC-NFR-08 | **Pass** | — |
| 2.10 | AC-TV-14 | **Fail** | still cropped half line of series is shown , and half line of focused series is shown |
| 2.11 | — | **Pass** | but loading is soooooooo slow 8 to 10 seconds slow |
| 2.12 | AC-TV-09 | **Pass** | — |
| 2.13 | AC-PLAY-04 | **Not run** | — |

## Session 3 — Television, AC-TV-01…15

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 3.1 | AC-TV-01 | **Fail** | all work but with focus issue on settings button it's hard to select |
| 3.2 | AC-TV-02 | **Pass** | — |
| 3.3 | AC-TV-03 | **Pass** | — |
| 3.4 | AC-TV-05 | **Fail** | wtf is this i cannot even understand what's this write human English please |
| 3.5 | AC-TV-06 | **Pass** | — |
| 3.6 | AC-TV-07 | **Not run** | — |
| 3.7 | AC-TV-09 | **Pass** | — |
| 3.8 | AC-TV-10 | **Blocked** | not clear instructions |
| 3.9 | AC-TV-11 | **Pass** | — |
| 3.10 | AC-TV-12 | **Pass** | — |
| 3.11 | AC-TV-13 | **Pass** | — |
| 3.12 | AC-TV-14 | **Blocked** | i will let you automate that |
| 3.13 | AC-TV-15 | **Pass** | — |

## Session 4 — Phone, Android 11

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 4.1 | AC-LEGAL-06/07 | **Pass** | — |
| 4.2 | AC-PL-02 | **Pass** | — |
| 4.3 | AC-PL-03 | **Blocked** | cannot understand |
| 4.4 | AC-PL-06 | **Blocked** | no source |
| 4.5 | AC-PL-07 | **Blocked** | no source u can test on my tab |
| 4.6 | AC-XT-01…06 | **Blocked** | OYOMT : on you on my tab |
| 4.7 | AC-EPG-01…05 | **Blocked** | OYOMT |
| 4.8 | AC-PLAY-01…13 | **Blocked** | OYOMT |
| 4.9 | AC-FAV-01…05 | **Not run** | OYOMT |
| 4.10 | AC-DATA-01…05 | **Not run** | OYOMT |
| 4.11 | AC-NFR-01 | **Not run** | OYOMT |
| 4.12 | AC-NFR-03 | **Not run** | OYOMT |
| 4.13 | AC-NFR-08 | **Not run** | OYOMT |
| 4.14 | AC-LEGAL-09 | **Not run** | OYOMT |

## Session 5 — Phone, Android 14

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 5.1 | AC-LEGAL-06/07 | **Blocked** | OYOMT |
| 5.2 | AC-PL-02 | **Blocked** | — |
| 5.3 | AC-PL-03 | **Blocked** | — |
| 5.4 | AC-PL-06 | **Blocked** | — |
| 5.5 | AC-PL-07 | **Blocked** | — |
| 5.6 | AC-XT-01…06 | **Blocked** | — |
| 5.7 | AC-EPG-01…05 | **Blocked** | — |
| 5.8 | AC-PLAY-01…13 | **Blocked** | — |
| 5.9 | AC-FAV-01…05 | **Blocked** | — |
| 5.10 | AC-DATA-01…05 | **Blocked** | — |
| 5.11 | AC-NFR-01 | **Blocked** | — |
| 5.12 | AC-NFR-03 | **Blocked** | — |
| 5.13 | AC-NFR-08 | **Blocked** | — |
| 5.14 | AC-LEGAL-09 | **Blocked** | — |

## Session 6 — Profiles and the catalogue scan

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 6.1 | AC-PROF-05 | **Blocked** | — |
| 6.2 | AC-PROF-02 | **Blocked** | — |
| 6.3 | AC-PROF-03 | **Blocked** | — |
| 6.4 | AC-PROF-04 | **Blocked** | — |
| 6.5 | AC-PROF-06 | **Blocked** | — |
| 6.6 | AC-META-01 | **Blocked** | — |
| 6.7 | AC-META-02 | **Blocked** | — |
| 6.8 | AC-META-03 | **Blocked** | — |
| 6.9 | AC-META-04 | **Blocked** | — |
| 6.10 | AC-META-05 | **Blocked** | — |
| 6.11 | AC-META-06 | **Blocked** | — |

## Session 7 — What v0.5.0 added, and no device has seen

| # | Criterion | Result | What was seen |
| :---- | :---- | :---- | :---- |
| 7.1 | AC-META · #024 | **Blocked** | — |
| 7.2 | #024 upgrade | **Not run** | — |
| 7.3 | INC-F0 | **Pass** | ugly design I'll bring you better arrangement |
| 7.4 | INC-F8 | **Fail** | still same extend as long as list it should be fixed length and the items inside is scrollable same on tv |
| 7.5 | INC-F3 | **Fail** | works on mobile but not on tv it should be on tv also |
| 7.6 | INC-E4 | **Fail** | wtf are you saying here???? |
| 7.7 | #024 cost | **Blocked** | — |

---

Results belong in `docs/ACCEPTANCE-SWEEP.md` — §5 for the phone rows, §7 for the television.
A row that says Pass with no evidence is a row nobody can trust six weeks later.
