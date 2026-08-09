**Wiki Story of Quiblo — how this was actually built**

A new part of the wiki about the method rather than the product: agents, skills, and what
they did and did not do. This is **Wiki Story** from `docs/MASTER_PATH.md` §G.

**Created:** 2026-08-10. **Ships as:** not tied to a version. It is best written after `010`
produces its numbers, and it should not wait for `010` to be finished.

---

| Item | State | Where it lands |
| :---- | :---- | :---- |
| A wiki part on using Claude Code, and how agents make things possible with real skills | **Not started** | A fifth `WikiPart` in `quiblo-wiki` |
| Tell the story of how this app was built with Claude Code | **Not started** | The same part — one narrative, not two |

## The thing this must not become

`MASTER_PATH` §G1 already names the risk in its own words: **"not just 100% vibe coding"**.
The genre this page sits in is full of posts claiming an app was built in a weekend by
describing it to a model, and every one of them is read by an engineer looking for the part
that is not true. If this page reads like those, it is worth less than nothing, because the
project's own `FREEZE.md` tagline is "a vibe-coded IPTV player" and a reader who suspects a
sales pitch will start there.

**So the page is written from the record rather than from memory.** The record is unusually
good — `001` through `010` in this folder, six dated `FREEZE` amendments, an acceptance sweep
that says what has *not* been verified, and after `010` a token count per session. Almost
everything worth writing is already written down; this is an edit, not an invention.

The test for every claim on the page: **could a reader check it in this repository?** If not,
it comes out.

## What the story actually is

Not "an AI built an app". The interesting version is narrower and true:

**The architecture was decided first, and it paid.** `FREEZE.md` §4 forbade UI code in
`:core:*` before there was a television, an amendment, or a reason. When Amendment 1 admitted
Android TV into v1.0, the argument was not that it would be cheap — it was that the engine had
already been confirmed running on the target television, so the frontend was a presentation
layer and nothing more. That invariant is why `007` can even ask the question about a desktop
and a browser.

**Scope was frozen, and the freeze was amended in public.** Six amendments, each dated, each
saying what it decided, why, what it cost and what it did not change. Amendment 4 is the one
to quote: it admits that Amendment 1's scope was never delivered — on a television a viewer
could not open a film, could not see episodes, could not reach any setting, and could not pick
a category from 11,923 channels — and it dates the admission rather than quietly widening the
plan. **That is the page's strongest paragraph and it is already written.**

**The failures are the content.** A short list, all of them in the record:

- **A cache that held failures.** Every `TmdbClient` error returned `null`, and `null` was
  written down as "this title matches nothing" for a fortnight. Invisible one poster at a time;
  across a catalogue scan it would have recorded tens of thousands of false misses. *A cache
  may hold answers. It may never hold failures.*
- **A rate limiter running at exactly twice its documented rate**, for weeks, with a passing
  test. The bucket let its balance stop at zero, so a throttled caller's wait accrued a token
  the next caller spent for free. It survived because the test measured one request's wait —
  **a pacing test that measures a single request cannot see this class of bug.** It exists
  because the project got a user's account blocked twice.
- **A Koin module that compiled, passed detekt, passed lint, passed every unit test, and took
  the app down on the screen that needed it.** A positional `single { }` list is not
  type-checked. That is how `0.2.0` shipped a Live tab that crashed on being opened.
- **Nine features nobody could reach**, deleted rather than kept — the "hollow feature" rule
  the project now applies to anything that appears to work.
- **The shake that took four wrong answers.** A focusable inside an animating scale on the
  television. Four plausible fixes, none of which worked, and the clue that cracked it came
  from the person holding the remote rather than from anything on screen.

**What that adds up to, and it is the honest thesis of the page:** the model wrote most of the
code, and every one of those defects was caught by something else — a test written to pin a
rate rather than a wait, a device with a remote, a person watching a screen. **The leverage
was real and the verification was not optional.**

## Structure

A fifth `WikiPart`, added as one more entry to the `WIKI` array in
`src/app/content/index.ts` — navigation, the search index, the part indexes and previous/next
all derive from that one array, so a page cannot be added to one and missing from another.
Content is data rather than markup in components, which is what makes the search index
possible; follow `engineering.ts` for the shape.

Placed **last**, after Engineering practice. A reader arriving to find out what Quiblo is
should not meet a page about how it was made before they meet the app.

Proposed pages:

1. **How this was built** — the narrative above. The freeze, the invariants, the amendments,
   and the five failures.
2. **Working with agents on this codebase** — the practical part, and the one a reader can
   actually use: what is written down and why (`FREEZE.md` first, always), what a skill is for
   versus what a test is for, and the rule that anything which can fail in CI belongs in CI.
   This is `010` §F2's argument written for someone else's project.
3. **What it cost** — `010` §F3's aggregates: tokens per session, cache read against fresh
   input, and sessions lined up against what shipped in them. Aggregates only, for the reason
   `010` gives: transcripts contain real provider hostnames, and `AC-LEGAL-04` forbids those
   anywhere in this project.

## Two constraints

**Voice.** The wiki is written as one team, in the present tense, without project
archaeology — and this part is about the past, which is exactly the place that convention
breaks down. The resolution: the *story* pages may be retrospective, because a history written
in the present tense is a lie; the *practice* page stays in the wiki's normal voice. Nothing
anywhere is written as "the AI did X and then I did Y" — it is one team, including on this
page.

**No numbers without a source.** Every figure comes from `010`'s generated aggregates or from
a dated document in this repository. The page states which of its numbers are exact and which
are floors, for the reason `010` gives: local transcripts are a record, not a billing
statement.

---

## Exit criterion

A reader who is sceptical of AI-built software finishes the part knowing three specific things
that went wrong, how each was caught, and where to check it — and can tell from the page which
parts of the process they could reuse and which were particular to this project.
