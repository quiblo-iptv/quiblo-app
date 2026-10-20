# FEAT-041 Manual Sweep

On the television, with the playback info overlay open.

1. Read the overlay's Refresh rates line. **Expect:** the modes the panel offers at its resolution.
2. Play a 23.976 or 24 fps film. **Expect:** the screen goes black for a moment; Display then reads
   24 (or 23.976) Hz, white, and pans move evenly.
3. Leave the player. **Expect:** Display back at 60 Hz in the catalogue.
4. Play the next episode from the end-of-episode offer. **Expect:** no switch between the two.
5. Play a 25 fps channel. **Expect:** 50 Hz, if the panel offers it.
6. Turn Settings → Match frame rate off and play the film again. **Expect:** no switch.
7. **Fail if** the resolution drops, or the panel is left at a film's rate after leaving the player.
