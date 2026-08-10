<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Release Management

This file answers one question: **what does a version number mean here?**

It is policy. [`RELEASING.md`](RELEASING.md) is mechanism — the keystore, the secrets, the
workflows, the checklist before a merge. When the two appear to disagree, this file says what
we intend and that file says what actually happens, and the gap between them is a defect in
one of them rather than a matter of taste.

Three documents govern a release and they answer different questions:

| Document | Question |
|---|---|
| [`FREEZE.md`](FREEZE.md) | What is this project allowed to contain? |
| [`ACCEPTANCE.md`](ACCEPTANCE.md) / [`ACCEPTANCE-SWEEP.md`](ACCEPTANCE-SWEEP.md) | Does it work? |
| **This file** | What do we call it, and when may it go out? |

## 0. Freeze, and how it relates to releases

`FREEZE.md` is the scope contract. It is the first document a new contributor or an AI agent
working on this codebase is handed, and it exists because the expensive failure on a project
like this is not a bug — it is building something nobody agreed to build.

**A freeze is a boundary, not a pause.** Work continues at full speed inside it. What the
freeze forbids is quietly widening what "Quiblo" means: a backend, an account, a bundled
playlist, a recorder. Those are listed as non-goals in `FREEZE.md` §2, and rejecting them is
a decision rather than an oversight.

Three rules connect the freeze to releases, and they are the whole relationship:

1. **A release may contain nothing outside the freeze plus its dated amendments.** If a
   change needs scope the freeze does not grant, the amendment is written *first* and the
   code follows. An amendment written afterwards to justify code already merged is a
   rationalisation, and it teaches the next person that the document is decorative.
2. **The amendments landed since the last release are the release notes.** Not a summary of
   them — the actual list. Amendment 5 (the catalogue scan) and Amendment 6 (local profiles)
   are what the next release *is*, and each already states its decision, its rationale, what
   it costs and what it does not change. A release body that says less than the amendment it
   ships is hiding something the amendment was honest about.
3. **The freeze names the version it is frozen for.** Today that is v1.0. When 1.0.0 ships,
   the freeze is re-cut for the next one rather than deleted, and the amendments that shipped
   are folded into the body they amended.

**What a freeze does not do:** it does not gate quality. A change can be perfectly within
scope and still not releasable, which is what the acceptance sweep is for. Scope and
correctness are separate gates and a release passes both.

## 1. What makes it a minor version

`X.Y.0` — the middle number moves.

A minor release **adds capability inside the frozen scope and breaks nothing a user relies
on.** All of these are true:

- Every source, playlist, favourite, resume position and setting on the device before the
  upgrade is present and correct after it.
- Any schema migration is additive and **adopts** existing rows rather than dropping them.
- The export file written by the previous version still imports.
- No screen, control or capability that existed has been removed.
- Nothing new is asked of the user's provider without them starting it.

The two worked examples are already in the tree. **Amendment 6 (local profiles)** rebuilt the
primary key of two tables — a change that sounds major and is not, because the 10 → 11
migration adopts everything already stored into a profile named "Default" and nobody upgrading
loses a favourite. **Amendment 5 (the catalogue scan)** added tens of thousands of potential
requests to the user's own metadata key, which also sounds major and is not, because it is off
unless started by hand.

The test is not how large the change is. It is **whether a user who upgrades without reading
anything notices something they had has gone.**

## 2. What makes it a major version

`X.0.0` — the first number moves.

A major release **breaks something a user relies on**, and it is major precisely so the break
is announced by the number rather than discovered on a device. Any one of these is enough:

- **The export/import format changes** in a way that makes an older file fail to import, or
  import incompletely.
- **A migration cannot adopt existing data** and something stored is lost or reset.
- **The application id changes.** Android identifies an app by it: a changed id is a new app,
  no existing install can upgrade, and every device starts empty. See Amendment 3 for why
  this was done once, before any release, and must never be done again after one.
- **`minSdk` rises.** Devices that ran the last version stop receiving this one.
- **A capability is withdrawn** — a screen, a source type, a format, a control.
- **An amendment retracts a promise** rather than extending scope. Amendments 1, 2, 4, 5 and
  6 all *added*; an amendment that takes something back is a major.
- **The signing key changes**, which is the same failure as a changed application id and
  is why the keystore is described in `RELEASING.md` as the single irreplaceable artefact
  in this project.

**A major is allowed to be boring.** It does not have to be a big release. It has to be an
honest one — 2.0.0 containing a single incompatible change to the export format is correct,
and 1.9.0 containing that change is a lie regardless of how small the diff is.

## 3. What makes it a release candidate

`X.Y.Z-rc.N`.

**A release candidate is a claim that this build is the release.** It is not "nearly ready"
and it is not a late beta. Cutting one asserts all of:

- Every acceptance criterion in `ACCEPTANCE.md` has been **swept**, on the devices the
  Definition of Done names — not observed working while it was being written, which is a
  different thing.
- `ACCEPTANCE-SWEEP.md` has no unchecked row in §5, §6 or §7.
- Every open scope decision is closed by a dated amendment, not deferred.
- Both signed APKs have been installed **over** the previous release and launched.

From the moment an RC exists, **only fixes for faults found in that RC may land.** Anything
else — a feature, a refactor, a tidy-up, a dependency bump that is not a fix — resets the
claim, and the next candidate is `-rc.(N+1)` rather than the final.

If `-rc.1` ships unchanged, the final release is the same tree tagged again without the
suffix. **A candidate that becomes the release without a rebuild is the point of the
exercise** — if the final is built from a different tree than the one that was swept, nothing
was swept.

## 4. What makes it an alpha

`X.Y.Z-alpha.N`.

**An alpha is a build we want someone to run, and nothing more is promised.** It may be
feature-incomplete, it may be unswept, and it **may break its own stored data** — an alpha
that cannot be upgraded is behaving as documented, and the remedy is to uninstall and start
again.

An alpha exists to answer a question hardware can answer and a runner cannot: does the app
start after R8, does the remote reach the new screen, does the migration survive a device
that has real favourites on it. Those are the questions this project keeps having to ask,
because a green gate is not a running app.

**Nobody should be asked to keep data in an alpha.** If a person is expected to live in a
build, it is a beta and the promises in §5 apply.

## 5. What makes it a beta

`X.Y.Z-beta.N`.

**A beta is feature-complete for the version it names and safe to keep data in.** Both halves
matter and the second is the one that is easy to get wrong:

- **Feature-complete.** Everything the version is going to contain is in it. A beta that is
  still growing features is an alpha.
- **Swept at least once.** Not every criterion on every device — that is the RC — but the
  sweep has been run, and what has *not* been run is written down.
- **The data format is stable.** A person who installs `1.0.0-beta.1`, adds their sources,
  builds up favourites and resume positions, and upgrades through every later beta to
  `1.0.0`, must arrive with all of it. **A beta a user cannot upgrade out of is an alpha
  wearing the wrong name.**
- **Known faults are listed in the release body.** A beta that reports nothing wrong is
  claiming to be a release candidate.

Betas are where the honest version of this project lives: it has real defects, they are
named, and the number says so.

## 6. How this is managed on GitHub and in CI

### Tags

| Tag | Published as | Means |
|---|---|---|
| `vX.Y.Z` | Release | §1 or §2 |
| `vX.Y.Z-rc.N` | **Pre-release** | §3 |
| `vX.Y.Z-beta.N` | **Pre-release** | §5 |
| `vX.Y.Z-alpha.N` | **Pre-release** | §4 |

The suffix is `-<stage>.<number>`, always with the number, always from 1. `-beta` on its own
sorts unpredictably against `-beta.2` and reads as though there will only ever be one.

### A merge to main is a release — when something released changed

`.github/workflows/release-on-main.yml` gates, versions, tags and publishes, in that order,
each step conditional on the one before. The version is decided by the workflow, not by a
person, and the numbers in the two build files are the record of what has already shipped.
`RELEASING.md` describes the machinery.

**But a merge that changes nothing a user runs publishes nothing.** `0.2.1` and `0.2.2` were
both cut before this rule existed, and `0.2.2` is — in every way a user can detect — the same
application as `0.2.1`. A README and a funding file. That is the cost of publishing on every
merge, and it is worth naming: a project that ships versions containing nothing teaches people
its release notes are not worth reading, and then the one release that *does* matter is read
by nobody.

So the workflow reads the **Conventional Commit types since the last tag** and decides:

| Since the last tag | Result |
|---|---|
| any `feat:` | **Minor.** `0.2.2` → `0.3.0` — the patch resets |
| any `fix:`, no `feat:` | **Patch.** `0.2.2` → `0.2.3` |
| only `docs:` `test:` `ci:` `chore:` `refactor:` `style:` `build:` | **No release.** Main moves, the run is green, nothing publishes |
| any `type!:` or a `BREAKING CHANGE:` footer | **Stops and asks a person** |

This needed no new tool and no new discipline: every commit here is already written to
Conventional Commits, so the information was there to be read and simply was not being read.

**A skipped release is a green run, not a red one.** The publish job does not fail when
nothing is releasable — it does not run at all. A red build for a corrected typo would be a
worse lie than a pointless release.

**Majors are never inferred.** A `!` is a claim, and §2 defines a major by consequences no
prefix can see: an export file that stops loading, a raised `minSdk`, a withdrawn feature. So
a breaking marker **stops the lane** and asks for the version to be set by hand — the same
escape hatch as below, used deliberately rather than automatically.

Which means **the branch protection on `main` is a release control, not a code-review
control.** A push that reaches `main` is published. Required: a pull request, the `ci.yml`
gate green, and no direct pushes — including for administrators, since the one account that
can bypass the rule is the account that will bypass it at midnight.

### Naming a version other than the next patch

The automatic bump moves the patch. To release a minor, a major, or any pre-release, **set the
version in both `app/build.gradle.kts` and `app-tv/build.gradle.kts` in the pull request
itself.** The workflow starts from what it finds and publishes **one step past it**, so what
you write is the version *before* the one you want: `0.9.0` to release `0.10.0`, `1.0.0-beta.0`
to release `1.0.0-beta.1`. The two application ids are asserted equal before anything is
written, because they are released together under one tag.

### The pre-release lane

Stated here because §3, §4 and §5 are unreachable without it. **It did not work until
2026-08-10**, and the three faults are recorded rather than deleted, because each of them was
the kind that publishes something wrong rather than failing loudly:

1. **The version was read as three numbers.** `versionName = "1.0.0-beta.1"` was read as
   `1.0.0`; the `versionName` `sed` then matched nothing while the `versionCode` `sed` beside
   it still fired, the "changed no files" guard was satisfied by the code alone, and `main`
   would have been tagged `v1.0.1` against build files saying `1.0.0-beta.1`. **A pre-release
   merged before that fix would have published under a name nobody chose.** The version is now
   read as whatever is between the quotes and its shape validated immediately, so a version
   the lane does not understand stops it with a message instead of being silently truncated.

2. **The bump now understands the suffix.** A pre-release advances its own counter and nothing
   else: `1.0.0-beta.1` → `1.0.0-beta.2`, whatever the commits since the last tag contain. The
   `X.Y.Z` in front of it was chosen by hand in the pull request that opened the stage, and a
   `feat:` landing during a beta is part of what that version will be rather than a reason to
   renumber it. A bare `X.Y.Z` keeps the class-driven bump above.

3. **`release.yml` published everything as a full release.** A tag carrying `-alpha`, `-beta`
   or `-rc` now publishes with `prerelease: true`. Without it `1.0.0-beta.1` would have stood
   on the releases page as the current version of Quiblo beside no stable release at all, and
   a stranger arriving there would have installed a beta believing it finished. A tag matching
   neither shape is **refused** rather than assumed stable, because the cost of guessing wrong
   is exactly that front page.

**Moving between stages is by hand**, in the pull request, as a minor or a major already is:
beta to rc, and rc to the final. Those are claims about the software — §3 and §5 — and no
commit prefix can make them.

**How to open a stage.** The bump always publishes one step past what it finds, so the build
files carry the version *before* the one being released: set `1.0.0-beta.0` to publish
`1.0.0-beta.1`. That is the same rule as for a minor or a major and it is easy to get backwards,
which is why it is written here rather than inferred.

**A version set by hand is a release request in its own right**, whatever the commits say. That
was a fourth fault and it was found by trying to prove the lane rather than by reading it: this
document said to open a stage by setting the version in a pull request, and a pull request that
only edits two version numbers carries no `feat:` and no `fix:`, so the class came out *none*
and the publish was skipped. **The instruction and the lane disagreed, and the lane was going to
win.** A changed `versionName` now publishes; the class only decides how a *bare* version moves,
since a pre-release advances its own counter regardless.

### `versionCode`

Always monotonic, always +1, and **never expresses the stage.** Android compares it as an
integer and refuses an install that lowers it, so `1.0.0-beta.2` → `1.0.0-rc.1` → `1.0.0`
must each raise it. The name carries the meaning; the code only has to keep increasing.

## 7. How amendments adopt this guide, incrementally

`FREEZE.md` carries six amendments written before this file existed. **They are not being
rewritten.** Retrofitting today's vocabulary onto dated decisions would make the record less
true, not more, and the dates are the reason anyone trusts it.

Instead the vocabulary is adopted going forward. From Amendment 7 onward, every amendment
adds two lines to the four it already carries:

```markdown
**Ships in.** 1.0.0-beta.1
**Release class.** Minor — additive, migrates existing rows, nothing removed (§1).
```

That is the whole mechanism, and it does three things worth having:

- **It forces the classification at the moment of the decision**, when the person writing
  knows whether the migration adopts or drops, rather than at release time when somebody is
  reading six amendments trying to work out what to call the tag.
- **It makes the release notes assemble themselves.** The amendments since the last tag,
  each already stating its class, are the notes and the number both.
- **It surfaces a major early.** An amendment that has to write "Major — the export format
  changes" is an amendment that gets argued about before the code is written, which is when
  arguing is cheap.

The six existing amendments are classified once, here, so the record is complete without
touching them:

| Amendment | Class |
|---|---|
| 1 — Android TV and Google TV enter v1 | Minor (pre-1.0 scope expansion) |
| 2 — DASH joins the supported formats | Minor |
| 3 — the project is renamed to Quiblo | **Major**, taken deliberately before any release existed to break |
| 4 — the television gets its missing screens | Minor |
| 5 — the catalogue can be described in one go | Minor |
| 6 — local profiles | Minor |

**Pre-1.0 exception, stated rather than assumed.** While the first number is 0, the guarantees
in §1 and §2 are not in force: `0.x` may break what `0.(x-1)` stored, and nothing published
under it is upgradable by promise. That exception ends at `1.0.0-beta.1`, because §5 requires
a beta to be safe to keep data in — so the first pre-release of 1.0.0 is the moment this
document starts binding.
