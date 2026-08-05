**Bug Reporting of Quiblo — Round 2**

Found during the 0.2.0 audit pass ([`003`](003%20Playback%20and%20Audit%20Phase%20of%20Quiblo.md)),
recorded rather than fixed in it.

**Created:** 2026-08-05, against commit `0eba343`.

---

| Bug | Platform | Status | Description |
| :---- | :---- | :---- | :---- |
| #011 A failed stream shows nothing at all | TV | **Closed** | When playback fails on television the screen goes black and stays black. No message, no retry, no way forward except Back — which a viewer has no reason to believe will help. The phone build shows the reason and a Try again button. |

## #011 — A failed stream shows nothing at all

**Severity: release blocker.** This is not a missing nicety; it is an acceptance criterion the
television frontend does not meet.

**Closed 2026-08-05.** All five points under "What 'fixed' means" are built, and the screen
was exercised on the Haier against a stream that genuinely fails — deliberately, because
normal use never reaches this path, which is why it went unbuilt in the first place.

Quiblo 0.2.0 was installed over 0.1.0-alpha01 on the television for that run.

### What happens

`TvPlayerScreen` renders exactly two playback states. Line 119 reacts to
`PlaybackStatus.PLAYING`, line 219 draws a spinner for `PlaybackStatus.BUFFERING`. There is
no branch for `PlaybackStatus.ERROR`, and `state.error` is never read anywhere in the
television app.

So when a stream dies the spinner disappears — because the status is no longer BUFFERING —
and nothing takes its place. The viewer is left with the last decoded frame or a black
screen, indefinitely.

Two things make it worse than a blank screen:

- **The television stays awake.** `FLAG_KEEP_SCREEN_ON` is held for the whole life of the
  player screen. A dead stream therefore holds the display on, at full brightness, on
  nothing, until somebody walks over to the remote.
- **Reconnection is invisible.** The controller does retry — three attempts with backoff —
  and those surface as BUFFERING, so the spinner does reappear. But the
  "Reconnecting… (attempt N of 3)" text lives in the phone's `BufferingIndicator` and was
  never built for television. A viewer cannot tell a stream that is coming back from one
  that is loading slowly from one that is already gone.

### What the phone does instead

`PlayerScreen.PlaybackErrorMessage` renders the typed reason — "This stream is no longer
available at that address", "This stream is copy-protected", and so on — with a **Try again**
button and a **Back** button. Every one of those strings already exists in
`feature/player/src/main/res/values/strings.xml`, and `PlaybackError` is already a shared
type in `:core:media`. Nothing needs designing from scratch; the television frontend simply
never consumed any of it.

### Acceptance criteria this breaks

| ID | Requirement | TV today |
|---|---|---|
| AC-PLAY-05 | "Given a dead or unreachable stream URL … a clear error appears within 15s. The app does not hang indefinitely." | **Fails.** No error appears at any point, and the screen hangs until the viewer intervenes |
| AC-PLAY-06 | "… retries automatically at least 3 times with backoff **before surfacing an error**." | **Half.** The retrying happens; the surfacing does not |
| AC-PLAY-13 | Buffering and failure are spoken under TalkBack | **Partial.** 0.2.0 announces buffering on TV; failure has no UI to announce |

### Why it was missed

The same reason as most of [`001`](001%20Bug%20Reporting%20of%20Quiblo.md): parity between
the two frontends was assumed rather than checked. `FREEZE.md` Amendment 1 admitted the
television into v1.0 on the argument that it is "the same player with a different frontend,
not a different product" — and for a second time, that claim does not hold where nobody
looked. The failure path is invisible in normal use because it only appears when a stream is
already broken, which is exactly the moment a viewer most needs to be told something.

Worth generalising: **the states that only appear when something is wrong are the states
that go unbuilt.** A screen is finished when its failure looks right, not when its success
does.

### What "fixed" means

- `PlaybackStatus.ERROR` renders the typed message from `state.error`, in ten-foot type.
- A focusable **Try again** that calls the same `retry()` the phone does, and a **Back**.
  Focus lands on Try again, so the common case is one press of OK.
- Reconnection is legible while it happens — the attempt count on screen, as on the phone.
- `FLAG_KEEP_SCREEN_ON` is released once playback has failed and stopped retrying. There is
  no reason to hold a television awake on an error.
- A screen-reader announcement on entering the error state, closing AC-PLAY-13 for TV.

### Not in scope here

Automatic fallback to another stream URL for the same channel. That is a feature, and a
different conversation; this bug is only about telling the viewer the truth.
