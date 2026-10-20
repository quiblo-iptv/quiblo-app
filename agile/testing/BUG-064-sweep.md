# BUG-064 Manual Sweep

On the television the stutter was seen on, with the playback info overlay open.

1. From a fresh start, play an episode. **Expect:** dropped/s 0; Items on this engine 1.
2. Back out, press Back again to leave the app, reopen it, play the same series. **Expect:** the same
   — no steady drops, Items on this engine 1.
3. Back out and choose the same episode again. **Expect:** it resumes where it was left.
4. Play a live channel, back out, play it again. **Expect:** unchanged from before.
5. **Fail if** dropped/s sits above 0 on the second play when it did not on the first.
