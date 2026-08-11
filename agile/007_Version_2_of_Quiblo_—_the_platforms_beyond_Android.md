**Version 2 of Quiblo — the platforms beyond Android**

Four platforms, one decision that governs all of them. This is **Version 2 Planning [HUGE]**
from `docs/MASTER_PATH.md` §C, and the word in the brackets is correct: it is larger than
everything built so far, because it is the first work that cannot inherit `:core`.

**Created:** 2026-08-10. **Ships as:** `2.0.0` and the releases before it — no part of this
is in the 1.0.0 gate, and `FREEZE.md` §2 lists all four as out of scope, so v2 opens with an
amendment rather than with code.

---

| Item | Platform | State | The honest position |
| :---- | :---- | :---- | :---- |
| Support for Samsung TV | Tizen | **Planned** | Web platform. Cannot run `:core` in any form |
| Support for LG TV | webOS | **Planned** | Web platform. Same constraint, and largely the same frontend |
| PC desktop app | JVM | **Planned** | The near one — Compose Multiplatform reuses `:core` almost as it stands |
| Web version, running locally in a browser as a PWA | Web | **Planned** | Cheapest per-platform *if* the web core exists; the reason to build it once |

## The one decision

Everything the TV app got cheaply, it got from one invariant: **no UI code in `:core:*`**.
`:core:model`, `:core:common`, `:core:database`, `:core:datastore`, `:core:network`,
`:core:media`, `:core:data` and every `:source:*` module are free of Compose, the ViewModels
hold no UI types, and so `:app-tv` is a presentation layer and nothing more. Amendment 1 was
argued on that basis and the argument held.

**It does not carry to Tizen or webOS.** Both are web platforms: the app is HTML, CSS and
JavaScript in a browser engine. `:core` is Kotlin against Room and DataStore, both of which
are Android libraries with Android implementations. The invariant that made the television
free makes the television *the last platform that is free*.

So there are two answers and the whole track follows from which one is taken:

**A — Kotlin Multiplatform.** `:core:model`, `:core:common`, `:source:*` and the parsers move
to common Kotlin; Room's KMP support or SQLDelight replaces `:core:database`; DataStore has a
multiplatform artefact. Every platform then compiles the same definition of what an Xtream
panel is, and `:source:m3u`'s parser — the one with a coverage gate on it in `ci.yml` — is
tested once and correct everywhere.

**B — A separate TypeScript codebase against a documented data contract.** This is the
incumbent: `docs/PLAN.md` §6.7 already says it, in as many words. The web platforms get their
own implementation of the source layer, and the contract between them is a document rather
than a compiler.

**The recommendation is A, and the recommendation is weakly held.** The reason to prefer it is
specific rather than architectural taste: the parsers are where this project's bugs live.
Every defect worth its own section in `001`–`005` was in parsing, pacing or caching — the
rate limiter running at twice its documented rate, `null` cached as a miss, the panel backoff
that covered one call path out of four. Option B duplicates exactly that code into a language
with no shared tests, and the second copy would be written by whoever is building a
television frontend, at the moment they are thinking least about token buckets. **A parser
that exists twice is a parser that is correct once.**

The reason the recommendation is weak is that option A's cost is unknown in this codebase
specifically: Room's KMP support, the JS target's HTTP story, and how much of `:core:data`
turns out to be Android-shaped in ways nobody noticed because it never had to leave. That is
not a question to answer by reading.

**So the first item in this track is a spike, not a platform.**

## Item 0 — the spike that decides the shape

Two weeks, timeboxed, producing a decision and a written argument rather than a product.

Move exactly two things to common Kotlin and try to build them for JVM and JS: **the M3U
parser** (`:source:m3u`, the module with a coverage gate, so the tests come with it) and
**`:core:model`**. That is the smallest slice that touches the real questions — parsing,
data classes, coroutines, serialisation — without touching the database, which is the
expensive unknown and can be answered second.

**What the spike must report:**

- Does `:source:m3u` build and pass its existing tests on JS? On JVM?
- What does `:core:database` cost — Room KMP, SQLDelight, or a per-platform storage interface
  with the query layer above it kept common?
- Does the HTTP client survive? `:core:network` is the layer that carries `PanelRateLimiter`,
  and that guard exists because this project got a user's account blocked twice.
- How much of `:core:data` is actually Android-shaped?

**Exit criterion.** A written decision, dated, naming A or B and what it costs. If it is B,
the data contract is written down as part of the same decision — because "a documented data
contract" that is not documented is two codebases drifting.

## Desktop — the near one

`MASTER_PATH` §C3, first half. Independent of the spike, and worth doing first for that
reason.

Compose Multiplatform on the JVM consumes `:core` close to as it stands and reuses the
Compose feature modules more than either TV platform will. It is the platform that proves the
frontend layering a second time, on a target that costs weeks rather than quarters, and it
produces something usable while the spike is still arguing.

What it needs that Android gives free: a window rather than an activity, a real file picker
for M3U files and for export/import, a keyboard and mouse input model, and a decision about
distribution — a jpackage installer per OS, or a jar.

**Media3 does not exist on the desktop.** `PlayerController` is the interface that was put
there for exactly this (`FREEZE.md` §4.4 names it as the seam where DRM slots in later, and
it is equally the seam where a different player does), so the work is one implementation
behind an existing interface — VLCJ or an mpv binding. That is the largest single item in
desktop and it is contained.

## Tizen and webOS — one frontend, two shells

`MASTER_PATH` §C1 and §C2. Both are gated on item 0.

They are listed as two platforms and they are close to one job: both run a web app, both
drive it with a D-pad remote, and both differ mainly in packaging, store submission and the
system APIs around the app rather than in the app. **Building them as two products would be
the single most expensive mistake available in this track.** One web frontend, two shells.

Everything already learned about the television carries over and none of it is about Android:
the focus model is the product, a mouse silently satisfies criteria a D-pad would fail, and
`006`'s sweep rule — unpair the mouse first — applies unchanged. The shake that took four
wrong answers to solve on Android TV was a focusable inside an animating scale; the web
equivalent is the same mistake with different syntax, and it will be made again unless
somebody who remembers it writes the rule down for CSS transforms too.

Both platforms have real submission requirements — developer accounts, app review, device
certification. Those are lead-time items, not engineering items, and they should be started
long before the code is ready.

## The PWA — cheap, and the reason to build the core once

`MASTER_PATH` §C3, second half. "Run locally on browser as PWA".

If option A is taken and there is a web build of the core, the PWA is close to free: it is
the Tizen/webOS frontend with a mouse-and-keyboard input model and a service worker. If
option B is taken, it is the same TypeScript codebase, also close to free. **Either way the
PWA is the cheapest platform in this track**, and that is the strongest argument for doing
whatever the spike recommends properly the first time.

One honest constraint to record before anybody is disappointed by it: **a browser cannot do
everything the Android app does.** CORS decides whether a given panel answers a page at all,
and many will not — a player that works for one provider and silently fails for the next is
a bad experience regardless of whose fault it is. Raw MPEG-TS has no native browser
playback and needs `mpegts.js` or equivalent. Storage is origin-scoped and can be cleared by
the browser without warning, which makes export/import more important there than anywhere
else. **These belong in the PWA's own documentation as stated limits, not discovered ones.**

## What has to happen before any of it

- **`FREEZE.md` §2 lists LG webOS, Linux desktop and Xbox as phase 2 or later**, and §3 fixes
  the platform at Android phones. v2 opens with an amendment that says which platforms enter,
  what they cost, and what does not change — the non-goals in §2 stand on every platform. No
  backend, no accounts, no bundled content, and no phoning home, on four platforms rather
  than one.
- **`RELEASE-MANAGEMENT.md` §2 makes this a major** by definition: new platforms are additive,
  but the version that introduces them is where a `:core` restructure lands, and that is the
  release to spend a major number on.
- **The release lane grows.** Today a release is two signed APKs under one tag. It becomes
  APKs, an installer per desktop OS, and a web bundle, and `release.yml`'s 25 MB budget does
  not mean anything for the new ones.

## Sequence

1. **Item 0, the spike.** Decides everything else. Two weeks, timeboxed.
2. **Desktop.** Independent of the spike, proves the layering, produces something usable.
3. **The web frontend, once.** Built against whichever core the spike chose.
4. **PWA shell**, first, because it is the one that can be tested without a television.
5. **webOS and Tizen shells**, with store accounts and certification started early.

**Exit criterion for the track.** A viewer's playlist, favourites and resume positions behave
the same on a phone, a television, a desktop and a browser, and the source layer has one
definition — whether that is one codebase or one document is what item 0 decides.
