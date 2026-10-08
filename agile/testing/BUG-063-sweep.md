# BUG-063 Manual Sweep

On an Xtream account with one screen.

1. Play a film, go back, and open an episode at once. **Expect:** it plays, perhaps after
   *Reconnecting*; never *a format Quiblo cannot play*.
2. Hold the screen on another device and open an episode. **Expect:** *Your account is already in
   use on 1 of 1 allowed screens*, with `1 retry` or `2 retries` in the details line when the panel
   answered empty.
3. **Fail if** a film or an episode is described as a channel not broadcasting while the account
   shows every screen in use.
