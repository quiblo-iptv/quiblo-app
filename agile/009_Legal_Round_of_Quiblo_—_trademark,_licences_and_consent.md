**Legal Round of Quiblo — trademark, licences and consent**

Four items about what this project promises, what it owes the code it stands on, and what it
asks the person installing it to understand. This is **Legal** from `docs/MASTER_PATH.md` §E.

**Created:** 2026-08-10. **Ships as:** E1, E2 and E3 **before** `1.0.0-beta.1`; E4 in the
`1.0.0` gate. See the reorder below — it is the only change of position this document
proposes, and no term is changed.

**This is not legal advice and nothing in it is drafted by a lawyer.** Everything below
produces a draft, a policy or an inventory that a lawyer should read before it is relied on.
Where an item genuinely needs a professional, it says so.

---

| Item | State | What exists already |
| :---- | :---- | :---- |
| Trademark and licence definition, and what a violation means | **Not started** | GPLv3 in `LICENSE`; nothing about the name |
| Licence and legal terms of everything we use, annotated in a legal file | **Partly built** | A hand-written list on the phone. **The television has none** |
| A policy and agreement that the user consents to using their own playlist | **Partly built** | `README.md` says it. Nothing in the app does |
| A first-launch dialog: agree, advise legal sources, link the wiki, next, start | **Not started** | — |

## The reorder

`MASTER_PATH` lists Legal as §E, after the shipment plan. **Three of the four items belong
before the first public beta**, for one reason: `1.0.0-beta.1` is the first artefact a
stranger installs, and the moment it is published the project stops being source code and
starts being software somebody is running.

- **E1, E2 and E3 are paper.** They cost days, not weeks, and each of them is cheaper to write
  before there is a user than after there is a dispute.
- **E4 is app work** — a screen, its strings, its state, and a wiki page for it to link to.
  That belongs in the `1.0.0` gate, alongside the sweep that has to cover it.

The position moves; the scope does not.

## E1 — Trademark, licence, and what happens when someone breaks the terms

`MASTER_PATH` §E1. The gap here is precise: **the code is licensed and the name is not.**

GPLv3 covers the source. It says nothing about who may call their build "Quiblo", and that
distinction is not pedantry on a project like this one. `FREEZE.md` §1 records that the name
was chosen deliberately to stay clear of the reseller namespace it identifies — the string
itself appears only in that document and in the CI guard that searches for it — and calls the
distance from that space **a legal and reputational asset**. An asset with no policy attached
is an asset anyone can spend.

The concrete risk is specific and likely: GPLv3 guarantees the right to fork and redistribute,
so somebody will eventually take this code, add a bundled playlist, and ship it under a name
close enough to ours that the difference does not survive a screenshot. That is legal as to
the code and it is the exact outcome the naming decision exists to avoid.

**What to write, in `TRADEMARK.md`:**

- **What the mark covers** — the name, and the icon once `008` §D3 produces the final one.
- **What is explicitly allowed without asking.** Say this first and generously, because the
  point is to be a good free-software citizen rather than to look like one: forks may say they
  are *derived from* Quiblo, may keep the name in commit history and attribution, and packagers
  may distribute unmodified builds under the name.
- **What is not.** A modified build under the same name and icon; a name or icon close enough
  to be mistaken for it; any implication of endorsement — **most of all, any build that bundles
  content**, which is the non-goal in `FREEZE.md` §2 that the project would be blamed for.
- **What happens on a violation**, in order of preference and stated plainly: ask, then ask the
  distribution channel, then the formal step. And say which of those the project will actually
  do, because a policy threatening what nobody will pursue is worse than none.
- **Whether the mark is registered.** Almost certainly not, and unregistered rights still
  exist. Say which it is rather than leaving a reader to infer registration from confidence.

Registration itself is a cost-and-jurisdiction decision that belongs to the project owner,
not to this document.

## E2 — The licences and legal terms of everything we use, annotated

`MASTER_PATH` §E2. Partly built, with two real gaps.

**What exists:** `feature/settings/ThirdPartyLicense.kt` holds a hand-written
`THIRD_PARTY_LICENSES` list — name, coordinates, licence, URL and a note on what each
component is for — rendered by `LicensesCard` behind a Show/Hide control in the phone's
Settings. `ACCEPTANCE-SWEEP.md` records `AC-LEGAL-03` as passing on that basis.

**Gap one: the television has no licences screen.** `:app-tv` has no licence string and no
licence UI. Since Amendment 1 the television is part of v1.0, and it links the same Apache-2.0
dependencies as the phone, so the attribution obligation is identical and currently unmet
there. `AC-LEGAL-03` therefore **passes on one of the two apps this release ships**, which is
not what the row says. This is the highest-priority item in the whole legal track, because it
is an obligation rather than a preference and it is unmet in shipping code.

**Built 2026-08-11.** TV Settings ends with an **About** section: the version, and the same
`THIRD_PARTY_LICENSES` data behind a Show control, expanded into the settings list itself
rather than onto a second scrolling surface — a scrollable inside a scrollable has to hand
focus back at its edges or the remote is trapped in it.

**Writing it changed one decision, and the test is what changed it.** The entries were not
focusable at first: there is nothing to press on a licence, so a focus stop looked like pure
cost. A test walking the list with a D-pad could not reach the twelfth entry. **On a
television, moving focus is how a list scrolls** — a row that never takes focus is a row the
remote cannot bring on screen, so twelve entries would have shipped as the two that fit and a
promise about the rest. Attribution nobody can read is not attribution, and that is the
unreachable-feature shape this project has deleted nine of.

`TvLicencesReachableTest` therefore drives the remote rather than clicking a node: down onto
the control, centre to open it, and one press per entry to the last one. **Owed the panel**
like everything else built on the JVM.

**Gap two: the list is written by hand.** It is accurate today because somebody kept it
accurate. It has no mechanical relationship to the dependency graph, so the failure mode is
silent and permanent: a dependency added in a pull request that nobody remembers to list is
attribution missing from every subsequent release, and no test fails.

**Closed 2026-08-11, and it was not accurate.** `./gradlew licenceCheck` resolves the release
runtime classpath of *both* applications — they ship different graphs — and fails when a module
that ships is claimed by no entry in `THIRD_PARTY_LICENSES`. It also regenerates
[`docs/LICENSES.md`](../docs/LICENSES.md) and fails when that has drifted. It runs in CI.

**The first run found 118 shipped modules attributed by nothing.** Not obscure ones: Okio, Tink
— which is the cryptography holding the viewer's password — Guava, Gson, Accompanist, Stately,
the whole Compose Multiplatform runtime, and `androidx.tv`, which is the television's entire
interface toolkit and was a *direct* dependency nobody had listed.

**One of them is not Apache 2.0.** SLF4J is MIT, in a file whose own comment said everything in
it was Apache 2.0. Both licences are compatible with GPLv3, so nothing was wrong with shipping
it — what was wrong is that the sentence asserting the compatibility question had been answered
was not true of the graph it was describing. That is the whole argument for this check in one
line: a hand-kept list does not fail loudly, it just stops being true.

The annotation stayed hand-written, as the action asks. Families are listed as families —
"the rest of AndroidX" is sixty libraries no part of Quiblo calls directly, and naming each on a
settings screen would bury the twelve that mean something.

**Action:**

1. **Give the television a licences screen** — the same `THIRD_PARTY_LICENSES` data behind a
   remote-navigable screen from TV Settings. The data already lives in `:feature:settings`,
   which `:app-tv` already depends on.
2. **Generate the inventory.** A Gradle licence report over the release runtime classpath of
   both application ids, checked in as `docs/LICENSES.md` and used as the source of the in-app
   list. The annotation — the `notes` field explaining what each component does and why it is
   here — stays hand-written, because that is the part with judgement in it.
3. **Make drift fail CI.** A step that regenerates the report and fails if it differs from the
   checked-in file. Attribution is exactly the kind of obligation that is met once and then
   quietly stops being met.
4. **Annotate what is not a code dependency**, which a licence report will never find: the
   TMDB API and its attribution requirements, the iptv-org channel list and its licence,
   fonts, and the icon assets once `008` produces them. **The TMDB terms are the one worth
   reading carefully** — it is the only third-party *service* the app talks to, it has
   attribution and use conditions of its own, and it is used with a key the user supplies.
5. **Confirm GPLv3 compatibility of the whole graph.** Apache-2.0 is fine — `FREEZE.md` §3
   records that as the reason for GPLv3 over GPLv2. Anything that turns up under a
   copyleft-incompatible or field-restricted licence is a build-breaking problem, and it is
   better found by a report than by a complaint.

## E3 — The policy that the user consents to using their own playlist

**Written 2026-08-11, as a wiki page rather than `docs/TERMS.md`.** The action below offered
either and noted that E4 needs a URL; one text serving both beats two that drift. It is
`/wiki/terms`, and it says what the app supplies (nothing), what the reader is responsible for,
where the data goes, what GPLv3 does not warrant, and why there is no age gate — that last one
stated deliberately rather than omitted, on the same reasoning that keeps the parental PIN
parked: a control that appears to restrict while verifying nothing is worse than none.

**That makes `006` gate 3's prerequisite for E4 met.** The first-launch screen now has a stable
URL to point at, and it remains true that the link cannot be the only way to read the terms —
the essential text goes on the screen, because a television has no browser worth using.

`MASTER_PATH` §E3. **The words exist; the agreement does not.**

`README.md` §"Quiblo supplies no content" already says it well: the app ships no playlists and
no way to find any, the user is solely responsible for the sources they configure and for the
legality of accessing them in their jurisdiction, and requests for sources are closed without
discussion. `AC-LEGAL-05` records that as passing.

What does not exist is any of it being **presented to the person installing the app**. A
README is read by contributors and by nobody who downloads an APK. So the position today is
that the project's protection lives in a document its users never see.

**Action:** write `docs/TERMS.md` — or a wiki page, since E4 needs a URL and one text should
serve both — covering:

- **The app supplies no content**, in the README's own words. This is not a new promise and
  should not be newly worded.
- **The user configures their own sources and is responsible for them**, including their
  legality where they live.
- **Where the data goes**, which is the most reassuring section and the most specific: local
  storage only, credentials encrypted and never in logs, exports or crash traces, no
  telemetry, no accounts, no server of ours, and the only outbound traffic is to hosts the
  user entered themselves. Every one of those is an invariant in `FREEZE.md` §4, so the terms
  are describing enforced behaviour rather than making a promise.
- **What is not warranted**, which GPLv3 §15–16 already say and which a user is entitled to
  see in language they can read.
- **No age gate and no jurisdiction filter**, stated deliberately rather than omitted. The
  project has no server, no accounts and no way to verify anything about a user, and a control
  that appears to restrict but does not is worse than none — the same reasoning that keeps the
  parental PIN parked in Amendment 6.

## E4 — The first-launch dialog

**Built 2026-08-11, on both apps.** Two screens before anything else on a fresh install: what
Quiblo is, then what is being agreed to. `FREEZE.md` **Amendment 9** was written first, as
`RELEASE-MANAGEMENT.md` §0 requires — a first-launch screen is user-facing scope the freeze did
not previously grant.

Every constraint below survived contact with the code, and two of them shaped it:

- **The consent gate stands in front of the profile gate**, not behind it. "Who is watching" is a
  question about a household, and it should not be the first thing an app says to somebody who
  has not been told what the app is.
- **Acceptance carries a version, not a boolean.** With a boolean, a materially changed policy
  leaves two options — ask nobody, so people are held to terms they never saw, or ask everybody,
  which trains them to dismiss the screen. The rule is "accepted something older", so a
  *downgrade* is left alone; the naive spelling gets that case wrong and it has its own test.
- **Nothing is drawn while the store is still answering.** Flashing the terms at somebody who
  accepted them a year ago is worse than a blank frame.

`TvConsentReachableTest` drives it with key events only, because the fault this project keeps
finding — a control that exists and cannot be reached — would not be a bug in a feature here. It
would be a television that cannot be used at all. What that harness cannot see is written into
it: Robolectric will not let the screen's own focus request land, so the tests prove everything
after the remote has arrived, and the screen is built not to depend on that request either way.

**`AC-LEGAL-06` to `AC-LEGAL-09` are written** and are unrun, like everything else awaiting the
sweep. `AC-LEGAL-09` — an upgrade must not ask again — cannot be run until there are two releases
carrying this screen, which makes it the first real test of the upgrade half of the DoD.

`MASTER_PATH` §E4, and the only item in this track that is code.

A next-next flow at first launch: agree to the terms, be advised to use legal sources, with a
title, artwork, a link to the agreements on the wiki, and a button that starts Quiblo.

**Two screens, not one.** The item describes a dialog with a next button, and the natural
split is: what this app is and is not, then what you are agreeing to. Three would be a
tutorial nobody reads.

**Design constraints, most of which this project has already learned:**

- **It runs on the television too, with a remote as the only input.** A dialog with a link and
  two buttons is exactly the shape that becomes unreachable by D-pad, which is the "hollow
  feature" failure this project has deleted nine of. The link cannot be the only way to read
  the terms, because a television has no browser worth using — so the essential text is *on
  the screen*, and the wiki link is for the full version.
- **It appears once and it is recorded.** Which means DataStore, a version number on the
  acceptance so a materially changed policy can ask again, and a decision about what happens
  if somebody declines. **Recommendation: there is no decline path that closes the app.** If a
  user will not accept the terms of a player they downloaded, the honest option is to let them
  read and leave; an app that force-quits on decline is theatre.
- **It is per install, not per profile.** Amendment 6's profiles own favourites and resume
  positions and nothing else; consent is not a viewing preference. The Guest profile must not
  be asked again.
- **Fresh install only.** An upgrade from an earlier version must not show it — and since the
  beta is the first release, there is nothing to upgrade from yet, which makes `1.0.0-beta.1`
  → `-beta.2` the first chance to check that and a reason to build this early in the beta
  cycle rather than at the end of it.
- **The artwork is `008` §D3's job**, and this is the first screen anybody sees, which makes
  it the strongest argument for doing the icon and type work earlier than v3.

**Two dependencies to note:** the wiki page must exist first (`006` gate 3 is a prerequisite,
not a follow-up), and the screen needs its own acceptance criteria — `AC-LEGAL-06` onward,
swept on a phone and on a television with the remote as the only input.

**A scope amendment is required.** A first-launch consent screen is user-facing behaviour that
`FREEZE.md` does not currently grant, and §1 requires scope changes to be dated amendments.
It should be written before the screen, per `RELEASE-MANAGEMENT.md` §0.

---

## Exit criteria for the track

- `TRADEMARK.md` exists, says what is allowed as loudly as what is not, and names what the
  project will actually do about a violation.
- `docs/LICENSES.md` is generated, CI fails when it drifts, and **both** apps show it in
  their own settings.
- The terms exist as a page, in the app's own words rather than boilerplate.
- The first-launch flow is swept on a phone and on a television, with the remote as the only
  input device, and has been seen not to appear on the second launch.
