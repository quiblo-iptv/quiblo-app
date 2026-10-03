# BUG-034 Manual Sweep

**Verify on real streams** (`docs/STOPPERS.md` S3). Which status a panel answers with, and how
long it takes to free a connection slot, is only knowable against a real panel.

## 1. A refused channel recovers on its own

1. Use an Xtream account with `max_connections = 1`.
2. Play a live channel, then zap to another one immediately.
3. **Expect:** if the panel refuses the second channel, "Reconnecting… (attempt 1)" appears and
   the channel starts within a few seconds, with no error screen.
4. **Fail if:** "no longer available" appears, or the error appears without any reconnect.

## 2. A real connection limit still surfaces in time

1. Hold the account's only connection on another device (or another app).
2. Play any channel in Quiblo and start a stopwatch.
3. **Expect:** two reconnect attempts, then an error before 15 s that talks about the provider
   refusing the stream, not about the stream being gone.
4. **Fail if:** the screen hangs past 15 s.

## 3. A dead channel is still reported at once

1. Play a channel whose URL answers 404 (check with
   `curl -s -o /dev/null -w '%{http_code}' URL` first).
2. **Expect:** "no longer available", immediately, no reconnect.

## 4. A changed password is not called a dead channel

1. Change the account's password at the provider, without editing the source.
2. Play any channel.
3. **Expect:** the account wording ("did not accept this account's username or password").
4. **Fail if:** "no longer available".

## 5. A host that does not exist

1. With the device online, play a channel from an M3U entry pointing at `http://example.invalid/`.
2. **Expect:** "Could not reach this stream."
