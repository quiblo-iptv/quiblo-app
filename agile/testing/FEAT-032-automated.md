# FEAT-032 Automated Tests

## Tests written: none, and that is the honest answer

There is no new automated test for this item. What it changes is one expression on each player —
keep the window awake unless playback is paused and the viewer has left the setting on — and
neither half of it is reachable by a test this project runs.

**The window flag is not testable here.** `FLAG_KEEP_SCREEN_ON` is a property of the `Activity`'s
window, set from a `DisposableEffect`. Asserting on it means driving a real player screen with a
real controller attached, on a device. Amendment 4 keeps verification headless, and this project
runs no instrumented suite. A Robolectric shadow would assert that Compose ran an effect, which
is not the same claim as "the television stays on".

**The preference is not testable here either, yet.** `PlayerSettingsStore` is backed by
`DataStore` and needs a `Context`; `:core:datastore` has plain JVM tests and no Robolectric.
Adding it to reach a two-line getter is a build change made to raise a number rather than to
learn anything — the same shape Amendment 10 calls padding. `checkUpdatesOnLaunch`, which this
follows exactly, has no unit test for the same reason.

Writing a test that would pass whatever the players did would be worse than saying this plainly.

## How it is verified instead

`FEAT-032-sweep.md`, on a physical screen, on both apps. The first check there is the one that
matters most: **playing must still hold the screen on**, because that is the fault the flag
exists to prevent and this change is the one that could reintroduce it.

## Where the decision does live

`PlayerViewModel.keepScreenAwake` combines the playback status with the setting, so the rule is in
one place and the two apps cannot drift on what counts as watching. That was done for clarity
rather than for a test — it is a `StateFlow` over a controller's state, not a pure function, so
holding it would mean standing up a fake controller to assert one boolean.

If this ever earns a test, the seam is a pure function beside the view model taking the status and
the setting, the way `tvPlayerBackAction` was extracted for `BUG-032`. That extraction was earned
by the rule being got wrong twice; this one has no such history yet.
