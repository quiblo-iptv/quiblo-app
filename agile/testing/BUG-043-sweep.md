# BUG-043 Manual Sweep

**Verify on real streams.** Use the audit's manual check first to learn what the account allows:
`curl -s "http://HOST:PORT/player_api.php?username=USER&password=PASS" | jq '.user_info.allowed_output_formats'`.

## 1. An HLS-only account

1. With an account allowing only `m3u8`, refresh the source.
2. Play five live channels.
3. **Expect:** all play. (Before this, every one failed.)

## 2. Automatic on an account allowing both

1. Refresh; play the sweep channels on Automatic. Note start time and stalls over five minutes each
   (the player's `loadTimeMillis` and `rebufferCount`, or a stopwatch).
2. Edit the source to **TS**; repeat.
3. **Expect:** both work. Record which is better on this panel; if TS is clearly better on most
   panels tried, Automatic's preference needs revisiting before release.

## 3. A panel that says nothing

1. A panel whose `allowed_output_formats` is missing or not a list.
2. **Expect:** live plays as `.ts`, as before.

## 4. The setting

1. Phone and television: edit an Xtream source. **Expect:** Live channels: Automatic / HLS / TS,
   Automatic selected. An M3U source shows no such control.
2. Pick HLS, save. **Expect:** live plays, and reopening the editor shows HLS.

## 5. Films and episodes

1. Play a film and an episode on each setting. **Expect:** unaffected.
