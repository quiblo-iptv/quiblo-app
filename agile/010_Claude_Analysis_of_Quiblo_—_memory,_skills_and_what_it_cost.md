**Claude Analysis of Quiblo — memory, skills and what it cost**

Three items about the tooling that built this, rather than about the app. This is **Claude
Analysis** from `docs/MASTER_PATH.md` §F.

**Created:** 2026-08-10. **Ships as:** not tied to a version — F1 pays for itself immediately
and should be done first; F2 follows it; F3 is a wiki page and can land whenever.

---

| Item | State | The position today |
| :---- | :---- | :---- |
| Enhance memory management — can we track it in the repo? | **Not started** | 22 memory files exist. **None of them are in the repo.** One rule file is, and it is stale |
| Look for skills to use on this project | **Not started** | Every skill in use is generic. None knows anything about Quiblo |
| Track usage, and analyse it on the wiki | **Feasible** | The data exists in the local transcripts — 15 sessions, ~95 MB, with per-request token counts |

## F1 — Memory, and whether it can live in the repo

**Yes, and most of it should.**

Today there are 22 memory files under `~/.claude/projects/C--Users-Maxmya-DEV-quiblo/memory/`
with a `MEMORY.md` index. They hold the things that were expensive to learn and are invisible
in the code: that the TV shake was a focusable inside an animating scale and took four wrong
answers to find; that a `single { }` list in Koin is positional and not type-checked, so a
mis-wired module compiles, passes detekt, passes lint, passes every unit test and then takes
down the screen that needed it; that a concurrency cap is not a rate limit, which is how a
user's account got blocked twice; that an untracked daemon-JVM file pins Gradle to JDK 25 and
makes the local gate red for no code reason.

**Every one of those is a fact about this project, and none of them survives a fresh
checkout.** A new contributor gets none of it. A second machine gets none of it. An agent
started in the wrong directory gets none of it — which is itself one of the memories.

**What is already in the repo, and is worse than nothing.** `.agents/rules/development-workflow.md`
is tracked, twenty lines, and out of date in three ways that matter:

- It calls the project "Quiblo TV". Amendment 3 renamed it, and `FREEZE.md` is specific about
  the name.
- It requires updating `walkthrough.md`, which does not exist.
- It instructs: commit on the branch, switch to `main`, merge. **Under `006` gate 4 that
  becomes a forbidden operation** — `main` is protected, a merge to it publishes a release,
  and everything goes through a pull request. The single tracked instruction file tells a new
  agent to do the one thing the release lane must refuse.

So the repo's agent guidance is not missing; it is wrong, and being tracked makes it
authoritative.

**Action:**

1. **`CLAUDE.md` at the root**, which does not exist. It is the file loaded first, so it is
   the natural home for the pointers: read `FREEZE.md` before proposing scope, `RELEASE-MANAGEMENT.md`
   before touching a version, the architectural invariants, and the branching rule as it
   actually is now.
2. **Move the project-shaped memories in**, under `.claude/` or as a `docs/` appendix, split
   by what they are:

   | Keep in the repo | Keep personal |
   |---|---|
   | The Koin wiring trap; the shake and its cause; measuring UI movement on the JVM; measuring movement from video; the request budget and why a concurrency cap is not a rate limit; what an API-blocked panel looks like; the hollow-feature test; the local JDK trap; the release-on-merge traps; what the first CI runs found | Which folder to start a session in; the git workflow authorisation; the house voice; the deploy-means-deploy rule; anything about this machine or these devices |

   The rule is simple: **if it would still be true for somebody else on a different machine, it
   belongs in the repo.** Authorisations and machine layout do not.
3. **Rewrite `.agents/rules/development-workflow.md`** or delete it. Do not leave it.
4. **Then keep them honest.** A memory in a repo is documentation and rots like documentation.
   The ones above are durable — they describe traps, not state — which is why they are the
   ones proposed for moving; anything that describes current status stays out, because that is
   what the `agile/` documents are for.

**Why this is first.** Everything else in this track, and a good deal of `006`, is cheaper
once an agent starting cold knows about the JDK trap, the Koin trap and the protected branch.

## F2 — Skills worth having for this project

None of the skills currently available know anything about Quiblo. They are generic, and the
work that consumes the most time here is not.

Candidates, in order of how much time each would return:

- **Drive the devices.** The adb loop is the most repeated and most fiddly thing in this
  project: pairing from the television's Wireless debugging screen, the install/reconnect
  dance, capturing a screen recording, and the trap in `screenrecord` that makes an app look
  like it is oscillating when the hand holding the phone is. All of it is already known and
  none of it is written where a tool can use it. **Highest value of the three.**
- **Run an acceptance sweep.** `ACCEPTANCE.md` and `ACCEPTANCE-SWEEP.md` are structured
  enough to drive: take a section, walk the criteria, record results in the file's own format.
  `006` gate 1 is the largest single block of work on the roadmap and most of it is procedure.
- **Cut a release.** The checklist in `RELEASING.md` plus the classification in
  `RELEASE-MANAGEMENT.md` §1–§5: work out the class from the amendments since the last tag,
  set both build files, check the two application ids agree, and open the pull request.
- **Verify the Koin graph.** Narrow, and it exists because the failure is silent: resolve
  every module against the real graph and report what a screen would fail to build. This is
  arguably a test rather than a skill, and if it is written as a test it should be, because a
  test runs in CI and a skill runs when somebody remembers.
- **Check the wiki's content array.** Pages are entries in the `WIKI` array in
  `src/app/content/index.ts`, from which navigation, search, part indexes and previous/next
  are all derived. Low value while the wiki is small.

**Two things to decide before writing any of them.** Whether skills live in this repo — they
should, by the same argument as F1 — and **whether each candidate is really a skill or really
a test.** Anything that can fail in CI belongs in CI. Skills are for the things that need a
device, a judgement, or a human in the room.

## F3 — Track usage, and analyse it on the wiki

`MASTER_PATH` §F3 asks for a wiki page on how much token is used, with which skills and which
contexts, allowing that old sessions may have to be assumed.

**They do not have to be assumed. The data is there.** Local transcripts exist in three
directories, because the project has moved and been renamed:

| Directory | Sessions | Size |
|---|---|---|
| `C--Users-Maxmya-DEV-vibrato-tv` | 6 | 78 MB — **the pre-rename history**, Amendment 3 |
| `C--Users-Maxmya-DEV-quiblo` | 8 | 16 MB |
| `C--Users-Maxmya-DEV-quiblo-quiblo-iptv` | 1 | 920 KB |

Each line carries the model and, on assistant messages, `input_tokens`, `output_tokens` and
the cache-read counts. So per-session totals, cache hit rates and a model breakdown are
arithmetic rather than estimation, and the pre-rename directory means the record starts at the
beginning rather than at the rename.

**Built 2026-08-11.** `tools/usage_aggregates.py` reads the three directories and writes
`docs/usage-aggregates.json`; `/wiki/what-it-cost` renders it. Nineteen sessions, 2–11 August,
9,176 assistant messages, **8,085,931 output tokens** — and the figure the item was worth doing
for: **38,307 fresh input tokens against 2,711,252,834 cache reads**, which is 99.999%. An agent
does not cost what it writes, it costs what it re-reads, at roughly 335 to 1.

**The constraint below is met structurally rather than carefully.** The script never reads a text
field at all — only `timestamp`, `message.model` and `message.usage` — so there is no filter that
could be written too loosely, because no content is ever loaded to be filtered. It then refuses
to write its own output if any string in it is not a date, a session id or a model name; that
guard was tested against a hostname and a prompt before it was trusted, and it rejects both.

**What to build:**

1. **A script that reads the transcripts and emits aggregates only** — a small JSON file,
   checked in, regenerated deliberately. It never emits transcript text.
2. **A wiki page that renders it**: total tokens, per-session, cache read versus fresh input
   (which is the number that explains the cost, and the one nobody expects), model
   distribution, and tokens against what shipped in that period.
3. **Correlation with the work**, which is the part worth reading. The `agile/` documents are
   dated and so are the sessions, so a session can be lined up against what it produced — the
   TV frontend, the profiles round, the four wrong answers before the shake was solved. That
   is the honest version of the story `011` tells, with numbers under it.

**One hard constraint, and it governs the whole item.** `AC-LEGAL-04` forbids any playlist
URL, provider hostname or credential anywhere in this project — including in documentation.
**Transcripts are full of exactly that**: real panel hostnames, real responses, and the debug
sessions that got the account blocked. So:

- **Publish aggregates, never content.** No excerpts, no prompts, no tool output.
- **The raw transcripts are never committed**, and the script that reads them stays pointed at
  a local path rather than at anything in the repo.
- **The generated file is reviewed before it is committed**, once, by a person — a hostname
  reaching the wiki through a usage page would be the most avoidable possible breach of a
  criterion this project already enforces in CI.

**A caveat to state on the page itself:** these are local transcript records, not a billing
statement, and old sessions may be missing or truncated. A page claiming precision it does not
have is worse than one that says which figure is exact and which is a floor.

---

## Exit criteria

- A fresh checkout tells a new contributor — human or agent — about the traps that cost this
  project the most time, and the tracked instruction file no longer contradicts the release
  lane.
- At least one skill exists that knows something specific about Quiblo, and each candidate has
  been judged against "should this be a test instead".
- The wiki carries a usage page built from real data, aggregates only, with its own limits
  stated on it.
