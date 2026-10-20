# BUG-063 Manual Sweep

**Verify on real streams**, on the television box the stutter was seen on.

1. Play a film that used to stutter after a seek, and skip forward or back until it does.
   **Expect:** within about ten seconds a short buffer, then it plays smoothly from the same moment.
2. Play a film that does not stutter; skip around it a dozen times. **Expect:** no reload, ever
   (the picture never blanks to a buffer except for the seek itself).
3. Play a 4K HEVC film on a box that cannot decode it smoothly. **Expect:** two reloads at most,
   then it plays on as it is.
4. Pause a film for a minute and resume. **Expect:** no reload.
5. Play a live channel. **Expect:** unchanged.
6. **Fail if** a smooth film is reloaded, or a reload lands anywhere but where the film was.
