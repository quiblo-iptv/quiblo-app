**Version 3 of Quiblo — sponsorship, reach and everyone**

Four items that share a shape: none of them add a feature, and all of them change who can
find, fund or use the thing that already exists. This is **Version 3 Planning** from
`docs/MASTER_PATH.md` §D.

**Created:** 2026-08-10. **Ships as:** `3.x`, except accessibility — see the reorder below,
which is the only one this document proposes.

---

| Item | State | Shape |
| :---- | :---- | :---- |
| Sponsorship for the project as open source | **Not started** | Paperwork and a policy. No code |
| SEO for the project | **Partly built** | `SeoService` exists in the wiki; the rest is additions |
| UI elements enhanced — icon, font and the rest | **Not started** | Design work, then a pass |
| Accessibility for any group of people | **Partly specified** | Two criteria exist; the sweep never ran |

## The reorder

**Accessibility should not wait for version 3.** Not because the item is wrong, but because
of what "wait" costs in this specific case: an accessibility pass done on four platforms is
four passes, and `007` adds three platforms. Every focus rule, label and contrast decision
made now is inherited by the desktop, the browser and the two web televisions; every one made
in v3 is made three more times.

There is also a fault in the current state that a v3 date would leave standing for two major
versions. `AC-PLAY-13` already requires that TalkBack speaks a buffering or failed stream —
"a silent spinner is a failure of this criterion" — and `AC-NFR-08` already requires full RTL
and no hardcoded strings. **Both are written, neither has been swept.** So the app has
accessibility criteria it has never been checked against, and moving the work to v3 turns
that from a gap into a policy.

**Proposed:** the accessibility *sweep* (D4) joins `006` gate 1 as criteria that get run;
accessibility *improvements* found by it are triaged into 1.x; the wider audit — the parts
that are genuinely new work rather than unverified claims — stays in v3 with the other three.
Terms unchanged, position moved.

## D1 — Sponsorship, donation, and support for the FOSS community

`MASTER_PATH` §D1. Paperwork, and one decision that has to be made before the first pound
arrives rather than after.

**The decision: what money does and does not buy.** GPLv3 already guarantees the code stays
free, but a licence answers a legal question and people are asking a different one. Write the
policy down and put it where the donate button is:

- **No feature is ever paywalled**, ever, including on any future platform. If it exists it
  is in the free build.
- **No sponsor influences what is built.** Priorities come from the freeze and the issues.
- **No sponsor logo in the app.** The app makes no network call to a host the user did not
  configure (`FREEZE.md` §4.5) and that includes a logo fetch — a sponsor image loaded at
  runtime would break the invariant that is the project's best claim.
- **What the money is actually for**, named honestly. On this project that is a second
  television, an Android 14 phone, an Xtream account for the sweep — `006` gate 1 is
  currently blocked on exactly that — and the developer accounts `007` needs for Tizen and
  webOS submission. That list is a better fundraising page than any appeal, because it is
  true and it is specific.

**Mechanics.** `.github/FUNDING.yml` is the whole integration and it does not exist yet;
GitHub Sponsors, Open Collective and Liberapay are the three that suit a GPLv3 project, and
Liberapay is itself free software if that consistency matters. Whether donations go to a
person or to a collective is a tax question and the answer differs by country — **that one
belongs to the project owner, not to this document.**

**Also in this item, and cheaper than it looks:** being a good FOSS citizen in the other
direction. `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md` exist. What does not is F-Droid
submission, which `PLAN.md` §6.8 parks behind a reproducible build and fully FOSS
dependencies — that is the single highest-leverage distribution item available and it belongs
in this track rather than nowhere.

**Exit criterion.** A funding path that works, with a published policy saying what it does not
buy.

## D2 — SEO

`MASTER_PATH` §D2. The wiki has the foundation; this is completion, not construction.

`quiblo-wiki`'s `SeoService` already does per-page titles, descriptions and canonical URLs,
and its own comment explains the non-obvious part — the deployment serves `index.html` for
unknown paths, so without a canonical every typo'd URL is an indexable duplicate. What is
missing around it:

- **A sitemap**, generated from the `WIKI` array rather than written by hand. That array is
  already the single source for navigation, search and previous/next, so a sitemap derived
  from it cannot drift.
- **`robots.txt`**, and a check that the deployment is not serving `index.html` for it.
- **Structured data** — `SoftwareApplication` for the app, `TechArticle` for the wiki pages.
  This is what produces a rich result rather than a blue link.
- **Open Graph and Twitter cards.** Every link shared to a chat or a forum renders from these,
  and a project with no card looks abandoned regardless of its commit history.
- **A real landing page.** The wiki home currently serves readers who already know what
  Quiblo is. The search query to win is "free IPTV player android tv", and the page that wins
  it says what the app is and is honest that it ships no content — which is also `AC-LEGAL-05`.

**The constraint that shapes all of it:** this project has no analytics and never will, on the
app or the wiki. So SEO here is done by building the pages correctly and reading GitHub's own
traffic panel, not by measuring visitors. That is a real limitation and it is the right trade.

**Exit criterion.** Every wiki page is indexable, has a canonical, and renders a card when
shared; the sitemap is generated rather than maintained.

## D3 — UI elements that need enhancing: icon, font, and the rest

`MASTER_PATH` §D3. The item most improved by being specific, so:

- **The launcher icon.** It appears in three places with different rules — the phone
  launcher, the television's leanback launcher banner, and the wiki. The television banner is
  the one most often forgotten and the most visible when it is wrong: a home row of polished
  banners with one stretched square in it.
- **Typography.** Two type systems, deliberately. A phone is read at 30 cm and a television at
  three metres, and a font stack chosen for one is a bad choice for the other. What matters
  on the television is weight and tracking at distance, not personality.
- **The empty and failure states.** This project has a documented history of building the
  path where everything works and leaving the others to fate — `002` and `004` both landed on
  it, and the observation in the TV notes still stands: failure states are the ones that go
  unbuilt. Every screen has a state for "nothing here yet", "this failed", and "your provider
  refused us", and those three are where an app looks unfinished.
- **Motion.** One rule, learned expensively: **a focusable must never be inside the thing
  that animates.** That is the fix for the shake that took four wrong answers, and any new
  motion in this pass reintroduces it unless the rule is written into the design notes rather
  than remembered.

**Do the design work before the pass.** An icon set, a type scale and a state vocabulary
agreed first make this one commit; done screen by screen it is a permanent activity.

**Exit criterion.** One icon set across app, TV banner and wiki; a type scale per platform; no
screen without an empty state and a failure state.

## D4 — Accessibility, so the app is usable by any group of people

`MASTER_PATH` §D4. See the reorder above — the sweep moves forward, the audit stays here.

**What already exists and has never been checked:** `AC-PLAY-13` (TalkBack speaks buffering
and failure) and `AC-NFR-08` (full RTL, no hardcoded strings). Neither has been swept. That
is the first action and it belongs in `006` gate 1.

**What is genuinely new work, and belongs in v3:**

- **Screen reader across every screen**, not just the player. Every control labelled, every
  poster meaningful, and decorative images marked as decorative — an unlabelled grid of 200
  posters is worse for a screen reader than an empty screen, because it takes longer to
  discover there is nothing to read.
- **Contrast**, measured rather than eyeballed, in both themes. The television is always dark
  by design, which makes it the one where low-contrast text is easiest to ship.
- **Touch targets and focus order**, and on the television the focus order *is* the
  navigation — a D-pad has four directions and no way around a trap.
- **Text scaling.** A phone set to the largest font size is the case that breaks fixed-height
  rows, and this app is made of rows.
- **Captions and subtitles.** Media3 renders them; whether they can be turned on, sized and
  positioned from the app's own UI is the accessibility question, and on the television it is
  the only way to reach them at all.
- **Reduced motion**, which after the shake is a setting this project has particular reason to
  respect.

**Two things worth stating plainly.** The first is that the D-pad work already done for the
television is accessibility work — a focus model that survives a remote is most of a focus
model that survives a switch device, and the project has already paid for it. The second is
that WCAG is the reference to measure against, but it was written for the web; a television
ten feet away is not a web page, and where the two disagree the criterion should be written
for the viewer rather than copied from the standard.

**Exit criterion.** `AC-PLAY-13` and `AC-NFR-08` swept; a new `AC-A11Y-*` block in
`ACCEPTANCE.md` covering the list above, swept on a phone and a television with the assistive
technology actually turned on.

---

## What this track is not

It is not a redesign. Every item here makes the existing app findable, fundable and usable by
more people, and none of them changes what Quiblo is. `FREEZE.md` §2's non-goals stand
throughout — in particular, no analytics arrives to measure the SEO, and no sponsor changes
what gets built.
