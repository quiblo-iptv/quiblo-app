# Stoppers

**What is blocked, who can unblock it, and what carries on without it.**

Everything on this page is work that cannot be finished from the codebase alone: it needs a
device in front of a person, an account somebody owns, or a decision that is not an engineering
decision. It exists so that a stopper is a line in a document rather than a session that stalls.

Two rules for this page:

- **A stopper names an owner and an action**, not a mood. "Needs hardware" is not a stopper;
  "open a series on the Haier and say whether the poster is whole" is.
- **Nothing waits on it that does not have to.** Each entry ends with what is being built around
  it, because the point of writing a blocker down is to stop it blocking anything else.

Last reviewed: **2026-08-11**.

---

## S1 — The round 3 fixes need the television

**Blocks:** `agile/012` closing, and therefore `agile/006` gate 5, and therefore gate 1.
**Owner:** whoever has the remote.

Twelve defects are built. Two have been swept: **#015 and #021 were both rejected** on the panel
on 2026-08-11. #015 has since been rebuilt and published in `v0.2.7`; #021 is unfixed and its
next step is a video capture, not another argued mechanism. **Ten have never been seen on a
device at all.**

**The action, in the order that gets the most out of one sitting with the remote:**

1. Install `v0.2.7` — it upgrades in place over `v0.2.6`, both real-key.
2. **#015** — open a multi-season series *and* a film. Both should show the title and the whole
   poster with no input. The film is the control, not a formality: it says the shared open
   sequence did not break the screen that was already right.
3. **#021** — capture video of the settings screen with the API-key field opened by the remote.
   See `docs/` on video method in the project's debugging notes: a fixed camera, and something
   in frame that is known to be still.
4. Sweep the remaining ten against the table in `agile/012`.
5. **New since the last sweep, and both are first-launch:** `AC-LEGAL-06`…`09` (the terms
   screens, driven by the remote alone) and the About section under TV Settings — the version,
   the licences, and the two service sentences. A fresh install is the only way to see the first
   of those, so it goes first or not at all in that sitting.

**Carrying on regardless:** everything on this page below S3, plus any `012` item whose fix is
still being written.

## S2 — The Xtream test account is API-blocked

**Blocks:** `AC-PL-07`, `AC-XT-01…06`, and any sweep day that expects a real panel.
**Owner:** the project owner. **This is procurement, not engineering.**

The account used for testing returns **469** to every API call regardless of client behaviour.
Nothing in this codebase can route around a provider's block, and attempting to is how an
account gets blocked in the first place — it has happened twice here.

**The action:** obtain a second Xtream account before a sweep day is scheduled. A sweep day that
starts by discovering the account is still blocked has spent three devices for nothing.

**This is the single item most likely to stall gate 1**, and it has been so since the gate was
written.

**Carrying on regardless:** M3U sources exercise most of the catalogue path, and the criteria
that need a panel are marked in `ACCEPTANCE-SWEEP.md` rather than attempted and failed.

## S3 — Playback criteria need real streams

**Blocks:** `AC-PLAY-*`, `AC-PL-05`.
**Owner:** whoever prepares the sweep.

The synthetic playlist points every entry at `.invalid`, which cannot exercise the player at
all. That is deliberate — it makes the catalogue testable without a provider — but it means the
player criteria are unrun rather than passing.

**The action:** a small playlist of streams that are legal to play and stable enough to re-run
against. It does not need to be large; it needs to be the same one every time, or a regression
cannot be told from a dead stream.

## S4 — Branch protection does not require a pull request yet

**Blocks:** the last part of `agile/006` gate 4's control story.
**Owner:** a developer. **The scope this needed is granted — 2026-08-11.**

The `main` ruleset is active and blocks **deletion** and **non-fast-forward** pushes. What is
missing is *pull request required*, and it is missing for a reason rather than an oversight: the
release lane pushes its own bump commit to `main` with `GITHUB_TOKEN`, so the rule needs a
bypass actor for that app.

**The scope is no longer the obstacle.** `gh auth refresh -h github.com -s admin:org,workflow`
was run on 2026-08-11; the token now carries both. The same refresh unblocked merging anything
under `.github/workflows/` through the API — without `workflow` the merge is refused with
*"refusing to allow an OAuth App to create or update workflow … without `workflow` scope"*, even
though pushing the branch over SSH works fine.

**What is left is one change, and it is one decision wide:** turn *pull request required* on and
name the release app as a bypass actor in the same edit. Turning the rule on without the bypass
stops the release lane from pushing its own bump commit, which is a broken release rather than a
tighter control.

**Until then the control is a convention:** everything lands by pull request because that is how
this project works, not because the repository refuses anything else.

## S5 — The sponsors listing does not exist

**Blocks:** the README's support link and the `FUNDING.yml` button resolving to anything.
**Owner:** the project owner, in GitHub's settings.

The organisation has sponsorship enabled but **no listing published**, so both links currently
point at a page that does not resolve. A support button that 404s is worse than no button.

**The action:** publish the org's sponsors listing, or remove both links until it exists. Either
is fine; the current state is the one that is not.

## S6 — The keystore has one copy

**Blocks:** nothing today. **Ends the project's ability to update its own app if it is lost.**
**Owner:** the project owner. **This is the most consequential line on this page.**

`quiblo-release.jks` sits in the repo root, gitignored, on one machine. Every published APK is
signed with it. Losing it means a new application id and every existing install having to be
uninstalled and replaced by hand — there is no recovery, no reset, and no support ticket.

**The action:** back it up somewhere that survives losing that machine, along with its two
passwords and the alias. Then confirm here that it is done, with the date.

## S7 — The quality gate needs an account decision

**Blocks:** `agile/006` gate 6 in its full form.
**Owner:** the project owner.

Gate 6 wants a quality gate that can fail a pull request. The recommendation on record is
**SonarQube Cloud**, free for public repositories — but connecting it means authorising a GitHub
app against the organisation and adding a `SONAR_TOKEN` secret, neither of which is ours to do.

**The action:** authorise SonarQube Cloud for `quiblo-iptv`, or say no and the self-hosted
Community fallback becomes the plan.

**Carrying on regardless — and this is most of the gate.** Dependency and deprecation scanning
need no account at all, and they are where "vulnerable" usually lives. **They do not run yet.**
They are written and unmerged in draft pull request #15 — `main` has no `dependabot.yml` and no
dependency-review workflow — and that pull request's own CI run is red. `agile/006` gate 6 says
"held", which is the accurate word. See it for what the scans will cover and what is still owed
to Sonar.

## S9 — `AC-PROF-05` has no build to upgrade from

**Blocks:** the highest-consequence unrun criterion in the project.
**Owner:** a developer, before a sweep day — it is twenty minutes of work and it cannot be done
on the day.

`AC-PROF-05` is *"upgrading from a build without profiles keeps every favourite and resume
point"*. **No published release qualifies.** Profiles landed on 2026-08-09 and `v0.2.1` — the
earliest release that exists — already contains them. A debug-signed older build cannot be
upgraded over by a signed release either, so there is no artefact anywhere that can start this
test.

**The action:** build an APK from a commit before 2026-08-09, sign it with the release key, and
hand it to whoever is sweeping. They install it, add favourites, and only then upgrade.

**Why it matters more than its position suggests:** a fault here presents as an **empty
catalogue** rather than as a cosmetic bug, and it lands on people who already had the app.

## S8 — The paper items are decisions, not documents

**Blocks:** `agile/009` closing.
**Owner:** the project owner.

The drafting is ours; two questions inside it are not:

- **Whether the mark is registered**, and in which jurisdiction. Unregistered rights exist and
  are worth stating plainly — but which of the two it is has to be *known* rather than implied
  by confidence.
- **Which enforcement step the project would actually take** on a violation. A policy that
  threatens what nobody will pursue is worse than no policy.

**The action:** answer those two, and `TRADEMARK.md` can be finished around them.

---

## Not stoppers, recorded so they stop being re-asked

- **The 1200x630 social preview image.** Ship without one; it is a preview card, not a gate.
- **A custom domain and a Search Console token.** Optional, and the Pages URL is stable.
- **The `013` and `014` increments.** Not blocked — deliberately *not started*, because they are
  `1.1.0` and the tree they land on is the tree gate 1 sweeps.
