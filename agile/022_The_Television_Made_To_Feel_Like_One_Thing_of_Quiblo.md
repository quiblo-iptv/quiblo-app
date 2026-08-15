**The Television Made To Feel Like One Thing of Quiblo**

`021` fixed what was broken. This round is about what the television *feels* like — three pieces
of light and a set of faces — plus one defect reported several times and never pinned down, one
piece of behaviour that never worked the way anybody assumed, and a question about reach.

**Created:** 2026-08-16, against `8ec3055` on `main` (`v0.18.0`), with `021`'s four branches still
unmerged.
**Ships as:** `1.0.0` work. Five `feat:` items and one `fix:`.

| # | Item | Branch | Cut from |
| :--- | :--- | :--- | :--- |
| 1 | `FEAT-025` — the generated avatars are shapes, not faces | `feature/FEAT-025-beam-avatars` | `main` |
| 2 | `FEAT-026` — the browse ambient is slow now the player's is not | `feature/FEAT-026-ambient-everywhere` | `FEAT-024` |
| 3 | `FEAT-027` — the ambient does not clear on Search, and Search has no light of its own | `feature/FEAT-027-search-glow` | `FEAT-026` |
| 4 | `BUG-028` — the gear and the face are sometimes not highlighted at all | `bugfix/BUG-028-bar-focus-race` | `main` |
| 5 | `FEAT-029` — backing out on Search does not close the app | `feature/FEAT-029-back-to-exit` | `BUG-028` |
| 6 | `FEAT-030` — touch, and phones | `feature/FEAT-030-touch-and-phones` | `FEAT-029` |

**The wheel stayed with the author** (Amendment 4), so nothing here was reproduced on a device.
Item 4 is a race, and the diagnosis below says how it is caught headlessly instead.

---

## 1. `FEAT-025` — beam, not bauhaus

**Reported:** *"change icon generation from this ugly types to faces (same library idk what it was
called)."*

The library is **boring-avatars**, ported rather than depended on — it is a React component
library, so there is no artefact to declare and the MIT obligation lands on the source file. What
was ported was its **bauhaus** variant: four coloured shapes arranged by a hash. The face variant
is **beam**: a tile, a shape on it, two eyes and a mouth, all placed by the same hash.

**Replaced for everyone, and the prefix did not change.** A profile stores `boring:<seed>`, so
switching what that prefix draws turns every existing generated avatar into a face on next launch
— which is the point, since the shapes are what "ugly" refers to. A second prefix would have kept
the old shapes alive forever in the one place they were least wanted: on the profiles that already
existed. The seed is untouched, so the same profile still gets the same face on every device and
after a restore.

**Four helpers were already right and are shared verbatim.** `boringHash`, `digitAt`, `booleanAt`
and `unitOf` are the same four functions beam uses, including the `Long` widening before `abs`
that stops one name in four billion indexing the palette backwards. What is new is `beamFace()`
and the drawing.

**Three details in the port are each one test**, because each of them fails as a face that is
slightly wrong rather than as an error:

- **The nudge.** A translation under 5 is pushed out by `36 / 9`; one at 5 or above is left alone.
- **The follow, and it is strictly greater.** A face follows its tile at half only when the tile
  has moved *past* `36 / 6`. `Sara#0` lands on exactly 6, so it does not follow — a port reading
  `>=` is a unit out on every seed that lands on the boundary.
- **The contrast rule.** Black or white by YIQ luma against a threshold of 128, read off the
  eight-bit channels via `toArgb` rather than Compose's floats. One of the five palette entries
  rounds the other way if it is read as floats, and the face disappears into its own tile.

**Every expected value came out of their JavaScript**, run here rather than reasoned about — which
is the standard the existing test file already set, and the only kind of assertion worth making
about a port. Thirteen cases; the seven that describe a shape are red against bauhaus by
construction.

Two SVG rules had to be transcribed rather than approximated:

- `scale(n)` scales from the origin, not the centre — pivoting on the middle instead would grow
  the tile symmetrically and lose the off-centre look the nudge exists to produce.
- `a1,0.75 0 0,0 10,0` is an arc whose radii are far too small to reach its end point, and SVG
  scales such radii up until they exactly do. The closed mouth is therefore a half-ellipse of
  `5,3.75`, not of `1,0.75`.

Files: `feature/designsystem/BoringAvatar.kt` and its test. Nothing else — `ProfileAvatar`,
`generatedAvatarKey` and both chooser screens go through the same two functions.

---

## 2. `FEAT-026` — the same quickness everywhere

**Reported:** *"make ambiant on outer screens same quickness."*

The player came down to 300ms in `021`; the browse grid stayed at 700.

**The 700 was paying for a problem something else already solves.** It was set longer than a D-pad
key repeat so that holding right along a row produced one slow drift rather than twelve flashes —
but nothing is fetched at all until focus has rested for `SETTLE_MILLIS`, so a row flown through
never produces twelve colours to flash between. What the long crossfade bought instead was light
arriving after the tile it belonged to.

`SETTLE_MILLIS` is **untouched**, and its note now says why: it is the restraint rather than the
slowness, it is what makes walking a row cost one fetch at the end rather than one per tile, and
it is also what makes a short crossfade safe.

The two constants stay two constants although they now hold the same number — one follows focus
and the other follows the picture, and a single value would make the next person to tune either of
them tune both without noticing.

## 3. `FEAT-027` — Search gets its own light, and clears the last one

**Reported:** *"ambiant not cleared on search screen … make it fade, and the glowing on the search
itself makes its own moving glowing blured ambiant."*

**Two faults, one cause.** Only the poster rows ever fed `LocalAmbientSink`, and nothing ever fed
it back — so arriving at Search or Live left the colours of a film focused two tabs ago lighting a
screen with no film on it.

- **Clearing.** Search and Live push `null` on composition. `rememberAmbient` already reads that
  as no light, and the backdrop's own crossfade turns it into the fade the report asks for rather
  than a snap.
- **Search's own light**, and this is the half worth reading twice. The search field has had a
  travelling highlight since `013` — one bright arc going round its border every six seconds.
  `driftingGlow` puts two pools of light in the room on **that same circuit**, at opposite ends of
  one orbit, so the room turns with the arc. Sharing the period is deliberate: two nearly-equal
  periods would drift apart into two things that never quite agree, which is the sort of wrongness
  nobody can name and everybody sees.

  The hue turns too, over two minutes — twenty circuits — so the colour is never seen to change,
  only seen to have. And the pools are built through the artwork path's own fixed lightness,
  saturation floor and `BACKDROP_ALPHA`, so the glow is exactly as bright as a poster's. A glow
  that read brighter would make Search the loudest screen in the app, which is the opposite of
  what a screen you arrive at to type is for.

The two-pool drawing is now one shared private function, so the backdrop and the glow cannot come
to disagree about the shape of light in this app.

**Tested for the contract, not the look.** `TvAmbientClearedTest` pins that a screen with no
artwork of its own says so — a one-line rule, in exactly the place a later refactor of either
screen loses it — and that a screen *with* artwork still lights the room, which is what a careless
fix (clear on every tab change) would break. The glow itself is two radial gradients on an
infinite transition; a screenshot is the only honest assertion about how light looks, and `A15`
sends that to the panel.

## 4. `BUG-028` — the bar with nothing highlighted on it

**Reported:** *"a bug that happened many times that settings button and profile button are not
highlighted."*

**The report is a symptom two steps downstream of the cause, and the screenshot says which.** The
gear and the face are highlighted when the bar holds focus *and* the remote is resting on them.
The tabs keep their underline regardless, because that is **selection**, not focus. So a picture
of a bar with an underlined tab and no highlighted icons is a picture of a shell where *nothing at
all* has focus — and in that state the bar's key handler never runs either, which is why the
remote also appears dead until something else takes focus.

**The cause is narrower than it looked, and the probe changed the diagnosis.** The plan for this
round said a `FocusRequester` whose node is not placed *throws* and that `tryRequestFocus`
swallows the exception. Half right. `requestFocus()` **returns a boolean** in this version of
Compose: it does not throw when the node exists but has not been placed yet, it returns `false` —
and `runCatching` has nothing to catch. The failure was a discarded return value, not a swallowed
exception.

`TvShell` asked from a `LaunchedEffect`, which runs after composition and before layout has
necessarily placed anything. And the shell leaves composition entirely whenever an overlay opens,
so the ask ran again on every return from Settings, a detail screen or the player — and lost often
enough to be reported as happening "many times".

`insistOnFocus` asks again on the next frame while the answer is `false`, bounded at ten frames.
A frame between attempts rather than a busy loop, because a frame is precisely what is being
waited for. `tryRequestFocus` stays exactly as it is for the callers it was written for — a
content area that may legitimately hold nothing focusable — and its own note now says which of the
two failures it is swallowing.

**A race cannot be asserted by running it and hoping**, so `TvFocusRaceTest` forces the losing
side: the focusable is not in the tree on the frame the request is made. It also asserts the
*old* call still fails in that arrangement, because otherwise "the bar ends up focused" would be a
sentence that is equally true of a test which never forced the race — which is how this survived a
whole round of bar tests already.

## 5. `FEAT-029` — two backs on Search close the app

**Reported:** *"make two backs on search screen & stack is empty close the app so I can pick
profile again."*

**Backing out never closed anything.** `BackHandler` was enabled only away from Search, so on
Search the press fell through to the system — which *backgrounds* an activity rather than
finishing it. The process survived, the chosen profile survived with it, and the next launch
resumed straight into somebody else's favourites. That is the "so I can pick profile again" half,
and it is the half that would still have been broken by simply finishing.

**Finishing alone is not enough**, and this is the part worth reading twice. The chooser reappears
because `Application.onCreate` clears the chosen profile — and that does not run again while the
process is cached. So the shell signs out *and then* closes, awaited in that order, because a
write racing an activity that is going away is a write that sometimes happens. And the activity
uses `finishAndRemoveTask` rather than `finish`, so the television's recents list stops offering a
card that resumes a session the viewer just asked to leave.

**Two presses, and a line rather than a dialog.** Back is also how a viewer walks out of
everything else, and a stray press that closes the app is the one mistake a television app cannot
let somebody make. The notice is a `Text` along the bottom, taking no focus and no space, so
arming the exit cannot move anything on screen. Not a `Toast`: on a television that is a
phone-sized rectangle in the corner of a three-metre screen, in the system's own type at the
system's own size. Not a dialog: this app has none, and the way out is a poor place to grow the
first one. The window is three seconds and resets.

The rule is `tvBackAction`, a function over state rather than branches inside the handler — the
same shape as `tvBarAction` and for the same reason. Its third case is the one a careless version
drops: **walking off Search disarms**, because a viewer who has gone somewhere else has stopped
leaving. Without it, arming on Search and then walking to Movies leaves the next back on Search
closing the app with no notice on screen at all. Four tests, one per case.

## 6. `FEAT-030` — touch, and phones

**Asked:** *"how costy is it to make tv app works with touch screen — if not too much make it work
in touch screen and workable on phones."*

**Cheaper than it looks, because the tiles were already built for it.** `TvPosterRows`,
`TvLiveScreen` and `TvPlayerControls` all use `clickable`/`combinedClickable`, so a tap on a
poster, a channel row or a player button has always worked. Three things did not.

**The manifest was television-only in three separate ways**, and each one fails differently:

- `leanback` was `required="true"`, which is the line that made this a television app *and nothing
  else* — the store filters such an app off every device that is not a television. Optional now;
  the feature is still declared and TV home screens read the `LEANBACK_LAUNCHER` filter rather
  than this.
- The activity declared only `LEANBACK_LAUNCHER`, so it would install on a phone and then not
  appear in that phone's launcher. `LAUNCHER` sits beside it now.
- `screenOrientation="landscape"` is right about a television and wrong about a handset, where a
  locked orientation is an app that refuses to turn. Removed; `configChanges` already declares
  that this activity handles a rotation itself.

**The keyboard is the one place a single value could not serve both.** `adjustNothing` is
*measured*, not preferred — `TvSettingsFieldStabilityTest` holds the trace of a settings list
chasing a shrinking viewport four items down, and of the same trace flat once the window is held
still. It is right on a television, where the leanback keyboard is a full-screen overlay. It is
wrong on a phone, where the keyboard covers the field being typed into. So the window stays fixed
on both and the *content* insets itself on a handset, decided from `UiModeManager` rather than
from a screen size — a ten-inch tablet in landscape is not a television and a small television is.

**The top bar is the one control a finger could not reach**, and it is deliberately *one* focus
target rather than five. `Modifier.clickable` would have fixed the reach in one word and
reintroduced the defect that shape exists to prevent: `clickable` makes what it touches focusable,
and with a focusable per tab, any event destroying the focused element in the content below left
Compose falling back to the first focusable in the tree — a tab — which selected itself and threw
the viewer onto another screen. `Modifier.onTap` is a raw tap detector with no focus node, so the
bar's own invariant holds unchanged: *nothing but a press moves the selection*, with "press" now
meaning a key or a finger. `TvTapAddsNoFocusTest` asserts both halves, and the second half —
that a focus search walks straight past a tap target — is the one that matters.

### What this does not do, said plainly

- **The ambient light does not light on a touchscreen.** It is fed by whichever tile holds focus,
  and on a phone nothing does; the screen falls back to black behind the grid, which is how it
  looked before `014`. Degraded, not broken, and left rather than rewired because the alternative
  touches the one screen this project has burned four wrong analyses on.
- **Sizing is untouched.** 10-foot padding and type on a phone is large but readable. Re-laying-out
  every screen is different work from making it reachable, and doing half of it would be worse
  than doing none.
- **There is now a second phone-capable app beside `:app`.** Two UIs for one platform is a
  maintenance obligation, taken on because it was asked for, and recorded here as a decision
  rather than left to be discovered.


---

## What a device still has to answer

Eleven tickets, `A15.1` to `A15.11`, in
[`docs/TESTING-REQUIRED.md`](../docs/TESTING-REQUIRED.md). Six of this round's six items are about
how something *looks* or *feels*, which is the one class of claim a build cannot settle. Three are
worth naming:

- **`A15.6` asks for ten repetitions and means it.** `BUG-028` is a race. A single open-and-return
  proves nothing about a fix for something that failed intermittently, and "it seems better" is
  what a race sounds like when it has not been fixed.
- **`A15.8` is the reported reason rather than the reported behaviour.** Closing the app is easy
  to see; the chooser coming back afterwards is the thing that was actually asked for, and it is
  the half that survives a fix which only finishes the activity.
- **`A15.11` is the regression watch.** The manifest edits and the keyboard inset in `FEAT-030`
  are the two changes that could bring `#021`'s shaking settings list back, and it shakes on a
  panel while staying flat everywhere else.

## What was not measured

Coverage is not reported for this round, for the same reason as `021`: `coverageAll` covers
`:source:m3u` and `:source:xtream` only — the parsers, where a covered line is genuinely an
exercised line — and nothing here touched a parser. Recorded as a divergence from Amendment 10
rather than acted on alone.

`licenceCheck` is unchanged and green. Beam is the same port as bauhaus was, not a dependency, so
`docs/LICENSES.md` has nothing to add; the MIT notice at the top of `BoringAvatar.kt` names the
variant and moved with it.

What this round has instead is **19 tests written or rewritten** — nine on the avatar port, two on
the ambient contract, two on the focus race, four on what a back press means, two on taps — and
every one of them is on the half of a change that can be wrong without looking wrong: a face a
unit out of place, a `false` return nobody read, a back press that disarms when it should not, a
tap target that is quietly also a focus target.

The other half of each of those changes is how it looks, and none of these say anything about
that. `A15` is where that goes.
