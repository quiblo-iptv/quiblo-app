# BUG-062 Manual Sweep

**Verify on real streams**, on an Xtream account with one screen and films that are slow to start.

1. Play a film that used to fail with `no engine error (load timed out) · data received`.
   **Expect:** it buffers past 12 s and plays; nothing is reported.
2. Play a film from a panel that sends the start and then stalls (or pull the network for 6 s
   after the first bytes). **Expect:** *Reconnecting*, then it plays; or, if it stalls again, the
   error with `1 retry` in the details line.
3. After any timeout, press *Try again*. **Expect:** the film loads again, and the
   panel's account info shows no connection held while the error is on screen.
4. Play a live channel whose server is down, and a film URL that answers nothing. **Expect:** the
   error within 15 s, as before.
5. Both apps: phone and television.
6. **Fail if** a film that plays in another player on the same device times out in Quiblo, or if
   anything takes longer than 40 s to start or to fail.
