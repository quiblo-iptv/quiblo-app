**First Beat of Quiblo — the road to 1.0.0**

Six gates between here and the first thing anybody installs. This is the **Version Shipment
plan — First Beat** from `docs/MASTER_PATH.md` §B, with an action plan and an exit criterion
per item, and the gates are in the order they can actually be worked rather than the order
they were listed.

**Created:** 2026-08-10, against commit `3184905` on `fix/live-tab-wiring-and-release-on-merge`.
**Ships as:** `1.0.0-beta.1`, then `1.0.0-rc.1`, then `1.0.0` — the classes are defined in
[`docs/RELEASE-MANAGEMENT.md`](../docs/RELEASE-MANAGEMENT.md).

---

| Gate | Item | State | What closes it |
| :---- | :---- | :---- | :---- |
| 0 | The local gate is red for a non-code reason | **Closed 2026-08-11** | The pin is gone, the file is ignored so it cannot come back tracked, and `build detekt lint` is green locally |
| 4 | Repo builds the release on protected main | **Closed 2026-08-10** | Four faults fixed and the lane driven: `v0.2.99-alpha.1` published as a pre-release, both signed APKs, build files matching the tag |
| 5 | All pending work done | **Open** | Seventeen acceptance criteria written and unrun; one open suggestion from `005` |
| 1 | Tests sweep on real devices, mobile and TV | **Open** | `ACCEPTANCE-SWEEP.md` §5 and §7 — the television has never been swept once |
| 6 | QA analysis for deprecated, old and vulnerable technology | **Not started** | A quality gate CI has to pass, not a one-off report |
| 2 | All docs updated | **Open** | `FREEZE.md` names the wrong repository; `AC-NFR-04` is an unresolved scope decision |
| 3 | Wiki updated | **Open** | No release page, and no destination for the consent link `009` needs |

**What on this page is not ours to finish** is listed in
[`docs/STOPPERS.md`](../docs/STOPPERS.md), with an owner and an action against each — the device
sweep, the blocked Xtream account, the branch-protection scope, the quality-gate account. Gates
here say what closes them; that page says who can.

## Why this order

The listed order is 1–6. The workable order is 0, 4, 5, 1, 6, 2, 3, and the reason is that
**four of the six gates produce evidence, and evidence has a shelf life.**

A device sweep is the most expensive thing on this list — three devices, a remote as the only
input on one of them, and an Xtream account that currently does not work. Running it against
a tree that is still growing features means running it twice. So the pending work closes
first, then the sweep, and the sweep is the last thing before the tag.

The release lane comes before all of that because it is currently broken in a way that
publishes under the wrong name, and because gate 1 wants to be swept on *the artefact*, not
on a local build. Documentation and the wiki come last because they record what the earlier
gates decided, and writing them earlier means writing them twice.

---

## Gate 0 — the local gate is red, and not because of the code

Not in `MASTER_PATH`, first because nothing else can be trusted until it is closed.

`gradle/gradle-daemon-jvm.properties` is untracked in the working tree and pins the Gradle
daemon to JDK 25. detekt does not run on 25, so **the local gate fails for a reason that has
nothing to do with the code being gated** — and CI, which sets up JDK 17 in `ci.yml`, passes
the same tree. A gate that disagrees with CI teaches people to ignore the gate.

`settings.gradle.kts` is also modified in the working tree and unaccounted for.

**Action.** Remove the daemon-JVM pin or set it to 17 to match CI; decide whether it should be
tracked at all, and if it should, track it with the value CI uses. Account for the
`settings.gradle.kts` change — merge it or discard it, but do not carry it into a release
branch unexplained.

**Exit criterion.** `./gradlew build detekt lint` is green locally on the same tree CI is
green on, from a clean checkout, with no untracked file required to make it work.

**Met 2026-08-11.** `./gradlew build detekt lint` exits 0 on a working tree with nothing
untracked, and `settings.gradle.kts` is accounted for — the modification was carried into a
merged pull request rather than left in the tree.

**The decision the action asked for: the file is not tracked, and is now ignored.** Pinning it
in the repository would export one machine's installed JDKs to every clone, and the toolchain
declarations in the build files are already where the version that matters is stated. Ignoring
it means the tool that writes it can go on writing it without the result reaching anybody else.

**It will be written again** — `updateDaemonJvm` and some IDE actions produce it — so the
symptom is recorded where it will be met: `.gitignore` carries the reason, and it names detekt
failing locally while CI is green as the sign to look there first. That is the whole of the
fault: **a gate that disagrees with CI teaches people to ignore the gate**, and the cheapest
version of this bug is the one where the next person recognises it in a minute.

## Gate 4 — the repo builds the release once pushed on a protected main branch

`MASTER_PATH` §B4. Second because gates 1 and 6 want to run against a published artefact.

**Two halves, and only one of them exists.** The lane is built: `release-on-main.yml` gates,
versions, tags and publishes, each step conditional on the one before, serialised so two
merges cannot bump from the same version. That part works and `docs/RELEASING.md` describes
it. What does not exist is the pre-release lane this shipment plan depends on:

1. **The bump cannot express a pre-release.** `release-on-main.yml` reads the version with
   `[0-9]+\.[0-9]+\.[0-9]+` and rewrites it with a `sed` anchored to the closing quote. Set
   `versionName = "1.0.0-beta.1"` in a pull request and it is read as `1.0.0`, the
   `versionName` `sed` matches nothing, the `versionCode` `sed` beside it still fires, the
   "changed no files" guard is satisfied by the code alone — and `main` is tagged `v1.0.1`
   against two build files that say `1.0.0-beta.1`. **Merging the beta today publishes it
   under a name nobody chose.**
2. **The bump must understand the suffix.** `-beta.1` → `-beta.2` on the next merge; a bare
   `X.Y.Z` keeps today's patch bump; and the steps between stages — beta to rc, rc to final —
   are set by hand in the pull request, as a minor or a major already is.
3. **`release.yml` publishes everything as a full release.** `draft: false` and no
   `prerelease`, so `1.0.0-beta.1` would appear as the latest stable download beside no
   stable download at all. A tag carrying `-alpha`/`-beta`/`-rc` publishes with
   `prerelease: true`.

**Branch protection is a release control here, not a code-review control.** Anything reaching
`main` is published, so: pull request required, `ci.yml` green required, no direct pushes,
**including for administrators** — the one account that can bypass the rule is the account
that will bypass it at midnight. The exception that must be kept is the workflow's own bump
commit, which is pushed with `GITHUB_TOKEN` and by design starts no further run.

**All three are fixed as of 2026-08-10**, and the arithmetic is verified against every shape
the lane can meet — a bare version bumping by class, each stage advancing its own counter,
`-beta.9` → `-beta.10`, and four malformed versions refused rather than truncated. The
`versionName` round-trip that was the first fault — write a pre-release, read it back — was
run against a real build file.

**Exit criterion, met 2026-08-10.** `v0.2.99-alpha.1` was merged through `main` and published:
marked **Pre-release** with `v0.2.5` still holding Latest, carrying `quiblo-v0.2.99-alpha.1.apk`,
`quiblo-tv-v0.2.99-alpha.1.apk` and a `.sha256` beside each, and with both build files reading
`0.2.99-alpha.1` — **the same string as the tag**, which is exactly what the first fault broke.
`main` was then reset off the throwaway version — and **that merge broke the lane**, which is
worth recording next to the success rather than tidied away. A shell line written with real
newlines instead of `
` broke the block scalar, so `release-on-main.yml` could not be parsed:
the run failed with **zero jobs** and no log to read, and nothing published. It looked exactly
like the forwards-only rule working, and it was the lane being dead.

**A pull request runs `ci.yml` and nothing else**, so a YAML error in either release workflow is
invisible to the gate and surfaces on the merge. The gate now parses every workflow file,
including the ones it is not running, and the check was proved against the actually-broken file
rather than a made-up one.

**Driving it found a fourth fault that reading it did not**, and that is the part worth keeping.
`RELEASE-MANAGEMENT.md` said to open a stage by setting the version in a pull request, and a
pull request that only edits two version numbers carries no `feat:` and no `fix:` — so the class
came out *none* and the publish was skipped. The instruction and the lane disagreed and the lane
was going to win, silently, on the merge meant to cut `1.0.0-beta.1`. None of the three faults
already fixed would have caught it: those are about how a version is read and written, and this
one is about whether the job runs at all. **Verified arithmetic is not a driven lane** —
`detektAll` was broken for its entire existence because nothing ever ran it.

## Gate 5 — all pending work is done

`MASTER_PATH` §B5. Third, because the sweep must run on the finished tree.

Everything below is written and none of it has been run. The list is from `005`, from
`ACCEPTANCE-SWEEP.md`, and — since 2026-08-10 — from
[`012`](012%20Bug%20Round%20of%20Quiblo%20—%20Round%203.md):

- **The twelve defects in `012` close before the sweep, not after it.** Five of them are
  failures of criteria this table records as never run (AC-TV-03, 10, 11, 12, 14) and one is a
  failure of AC-PLAY-04 on both apps, so a sweep run today would spend three devices'
  worth of effort rediscovering them. **#020 is blocked on a decision rather than on work** —
  it describes behaviour AC-TV-03 currently demands — and that decision belongs in gate 2's
  amendment pass, which makes it the one item here that must be started early rather than
  worked in order.
- **Nothing from `013` or `014` is in this gate.** The increment is 1.1.0 and later. It is
  listed here only so that "all pending work" cannot be read as including it.
- **`AC-PROF-05` first, before anything else on this page.** The 10 → 11 migration adopts
  existing favourites and resume points into a profile named "Default". It can only be judged
  on a device that already carries favourites, and a fault there presents as an empty
  catalogue rather than as a cosmetic bug — so it is both the highest-consequence unrun
  criterion and the one that needs a device prepared in advance.
- **`AC-PROF-01…06`** — profiles, on both apps.
- **`AC-META-01…06`** — the catalogue scan.
- **`AC-TV-14` and `AC-TV-15`** — television search, and Sources moving to Settings.
- **The version was never bumped for any of it.** Everything since `b4cc312` is still
  `0.2.0`/`versionCode` 2, so two builds carrying different features are indistinguishable on
  a device except by `lastUpdateTime`. Settle this in the same pull request that fixes gate 4.
- **The scan's pacing has never met the real service.** Eight requests a second and the
  handling of `Retry-After` are asserted on a fake clock against mocked responses. A stop
  reporting that the service asked us to slow down is a result, not a bug.
- **Open suggestion from `005`, to be decided rather than left open:** a rate limit currently
  ends a scan. Backing off for the interval the service names and carrying on would let the
  scan find its own safe speed. **Recommendation: not in 1.0.0.** Stopping is the
  conservative behaviour, this project has had a user's account blocked twice, and a beta is
  the wrong place to start pushing through a refusal. It is a good 1.1 item.

**Exit criterion.** No acceptance criterion exists that has never been run; every item above
is either swept in gate 1 or explicitly deferred with a reason, in writing.

## Gate 1 — all tests sweep on real devices for mobile and TV

`MASTER_PATH` §B1. The expensive one, and the one the beta exists to make possible.

The Definition of Done wants every criterion on a physical Android 11 device and a physical
Android 14 device, with an M3U source and an Xtream source, on a fresh install and on an
upgrade. Since Amendment 1 it also wants a television. Three targets:

| Target | State |
|---|---|
| Physical Android 11 | **Never run.** Only an API 30 emulator, which does not satisfy the DoD |
| Physical Android 14 | **Never run at all** |
| Haier MatrixTV EE, Google TV, Android 14 | `AC-TV-01…15` **never swept**, though the app is built |

`ACCEPTANCE-SWEEP.md` §5 lists what needs hardware; §7 is the television. Three practical
obstacles, each of which needs solving before a sweep day rather than during one:

- **The Xtream account is API-blocked** at the provider's end and returns 469 regardless of
  client behaviour, so `AC-PL-07` and `AC-XT-01…06` cannot be exercised against it. **A
  different account is needed.** This is a procurement problem, not an engineering one, and
  it is the single item on this page most likely to stall a sweep day.
- **Playback needs real streams.** The synthetic playlist points every entry at `.invalid`,
  which cannot exercise the player at all.
- **The television is swept with the remote as the only input device.** Unpair any mouse
  first — a mouse silently satisfies criteria a D-pad would fail, which is the exact defect
  `AC-TV-01` exists to catch.

**Stale results to re-run rather than trust**, all recorded before the changes that invalidate
them: `AC-NFR-01` and the scroll-jank baseline (measured before the browse query was indexed
and before its mapping left the main thread — schema 10 also adds a one-off index build on
first launch after upgrade, which the old cold-start figures do not include), `AC-NFR-02`
(both APKs have changed size, and the television has gained four screens), and
`AC-PLAY-*`/`AC-PL-05`.

**The upgrade half of the DoD cannot apply to the first release** — there is nothing to
upgrade from. It becomes live at the second, which makes `1.0.0-beta.1` → `-beta.2` the first
real test of it and another reason the beta lane is worth having.

**Exit criterion.** `ACCEPTANCE-SWEEP.md` §5 and §7 fully checked, on `1.0.0-beta.1` as
installed from the published release rather than on a local build.

## Gate 6 — QA analysis: deprecated, old, or vulnerable technology

`MASTER_PATH` §B6, marked **VIP**. Fifth because it will produce work, and work found after a
sweep invalidates the sweep.

The item asks for an open-source, licence-compatible tool run against the codebase, naming
SonarQube as an example. Two things need scanning and one tool does not do both:

- **Our code** — deprecated APIs, dead code, complexity, bug patterns. SonarQube.
- **Everything we depend on** — known vulnerabilities and versions long superseded. Sonar
  reads what we wrote, not what we pulled in, and the supply chain is where "vulnerable"
  usually lives.

**Recommendation: SonarQube Cloud.** It is free for public repositories and both repositories
are public, which makes it the cheapest route by a wide margin and puts the quality gate
where contributors can see it. A self-hosted Community instance is the fallback if the cloud
terms turn out not to suit. Licence compatibility is not in question either way: SonarQube is
an external analyser run against the source, not a dependency linked into a GPLv3 binary — the
same relationship detekt and Android Lint already have.

Paired with dependency scanning: OWASP `dependency-check`, or Gradle's own version-catalogue
report for "how far behind is this". Also worth a pass in the same gate: the `minSdk` 30 floor
and the Media3 version, both of which age quietly.

**Two things to decide before running it, not after:**

1. **What the quality gate is.** A first Sonar run on a codebase this size will report
   hundreds of items, most of them opinions. Agreeing the gate first — for example, no new
   issues above a chosen severity, and a named list of accepted existing ones — is the
   difference between a gate and a wall.
2. **That findings are triaged, not obeyed.** A static analyser does not know that
   `PanelRateLimiter` is deliberately conservative or that the television theme is
   deliberately fixed. Anything it flags that is a decision gets an accepted-issue entry with
   a reason, in the same spirit as a `FREEZE` amendment.

**Exit criterion.** A quality gate that runs in CI and can fail a pull request, with its
accepted-issue list written down and dated. Not a report someone read once.

## Gate 2 — all docs are updated

`MASTER_PATH` §B2. Late, because it records what the gates above decided.

Known faults and required updates:

- ~~**`FREEZE.md` §Repo/org says `quiblo-tv`.**~~ **Corrected 2026-08-11 as Amendment 8**, with
  `PLAN.md`'s first task in the same change — it now records *why* the org is `quiblo-iptv`
  rather than `quiblo`, so the next reader who notices the mismatch finds the answer rather
  than the discrepancy.
- ~~**`AC-NFR-04` reads as failing and is a scope decision, not a defect.**~~ **Closed as
  Amendment 7** on 2026-08-10, and it is a criterion rather than a clause that forgives
  anything: the merged manifests are read in CI and a third contributed permission fails the
  build.
- ~~**`RELEASING.md`** gains the pre-release lane and a cross-link to
  `RELEASE-MANAGEMENT.md`.~~ **Done** — both landed with gate 4.
- ~~**`README.md`** gains the release badge and the install instructions the first published
  APK makes true.~~ **Done** — the download badge, both APKs named, and the `sha256sum -c`
  line. That is also `013`'s INC-F5, which is therefore already shipped.
- **`ACCEPTANCE-SWEEP.md`** gets its results from gate 1, and its two "these results are
  stale" preambles removed once they are no longer true. **Blocked on the sweep** —
  [`docs/STOPPERS.md`](../docs/STOPPERS.md) §S1–S3.
- **`PLAN.md` §6** lists profiles as Phase 2 with a strike-through and a note. Fine now;
  re-read it after the sweep, because it also lists the desktop and web frontends that
  `007` plans.
- **Amendment 7 onward carries `Ships in` and `Release class`**, per
  `RELEASE-MANAGEMENT.md` §7. Amendment 8 does, including a release class of **none** — a
  correction that changes nothing a viewer runs is a legitimate class, and saying so is what
  stops the next amendment from inventing a version for a typo.

**What is left in this gate is the sweep's output and nothing else.** Every fault listed when it
was written is closed; the remaining two items cannot be written until gate 1 has run.

**Exit criterion.** No document in `docs/` describes a state that stopped being true; every
open decision in `ACCEPTANCE-SWEEP.md` §6 is closed by a dated amendment.

## Gate 3 — the wiki is updated

`MASTER_PATH` §B3. Last, because it is the public face of everything above.

- **A release page**: what the two APKs are, why the phone one does not appear in a
  television's launcher, and how to verify a checksum.
- **A destination for the consent link.** `009` §E4 puts a first-launch dialog in the app
  that links to our agreements on the wiki; that page has to exist before the dialog can
  point at it, which makes this gate a dependency of legal rather than a follow-up to it.
- **The stated capabilities re-read against what shipped** — search, the catalogue scan and
  profiles all arrived after most of the wiki was written.

Pages are added as entries in the `WIKI` array in `src/app/content/index.ts`; navigation, the
search index, the part indexes and previous/next all derive from that one array, so a page
cannot be added to one and missing from another.

**Exit criterion.** The wiki describes the app as `1.0.0-beta.1` ships it, and the consent
page exists at a stable URL.

---

## What this plan does not cover

- **The legal gate.** `009` recommends that the paper items — trademark, licence inventory,
  the bring-your-own-playlist consent policy — land **before** the first public beta, since
  the beta is the first artefact a stranger installs. The first-launch dialog belongs in the
  `1.0.0` gate. That is a reorder of position, not of scope.
- **Anything in `007` or `008`.** Version 2 and Version 3 are deliberately not gating this.
- **The keystore.** It is not on this checklist because it is not a step: it is the single
  irreplaceable artefact in the project, and if it does not exist and is not backed up
  somewhere that survives losing this machine, nothing on this page can happen. Confirm it
  before gate 4, since the lane's first job is to check the signing secrets are present.
