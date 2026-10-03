# FEAT-035 Manual Sweep

**Verify on real streams** (`docs/STOPPERS.md` S3). A verdict is only as good as what a real panel
answers, and every panel answers a little differently. Run each check on the phone **and** the
television. Before each, note the time: the error must still appear within 15 s.

## 1. An expired account

1. Use an Xtream test account whose `exp_date` has passed (check with the `player_api.php` call
   in the audit's manual check; note whether `status` still says `Active`).
2. Play any live channel.
3. **Expect:** the error appears with "Checking why…", then *Your subscription* — "Your
   subscription ended on {date}." with the panel's date.
4. **Fail if:** "no longer available", or "Not sure".

## 2. A wrong password

1. Change the account's password at the provider, without editing the source in Quiblo.
2. Play any channel.
3. **Expect:** *Your subscription* — "Your provider no longer accepts this username or password."

## 3. The panel is down

1. Block the panel's host at the router, or point a test source at an address nothing answers on,
   with the device otherwise online.
2. Play any channel.
3. **Expect:** *Your provider* — "Your provider's server is not responding…"

## 4. Airplane mode

1. Start a channel, then switch on airplane mode (on the television, unplug the network).
2. Wait for the retries to run out.
3. **Expect:** *Your connection* — "This device is not connected to the internet."

## 5. Connection limit

1. On an account with `max_connections = 1`, hold the slot on another device.
2. Play a channel in Quiblo.
3. **Expect:** *Your subscription* — "Your account is already in use on 1 of 1 allowed screens."

## 6. A format Quiblo cannot play, on an account that is fine

1. Add an M3U entry, inside an Xtream account's catalogue if possible, pointing at a reachable file
   that is not media (any small text file served over HTTP), or a codec the device cannot decode.
2. **Expect:** on an Xtream source, *Quiblo* — "The stream is reachable, but Quiblo cannot play its
   format on this device." On a plain M3U source, *Not sure* — there was no account to vouch for.

## 7. Nothing to go on

1. With an Xtream source, block only `player_api.php` (or let the panel time out on it) while the
   stream URL itself refuses.
2. **Expect:** *Not sure* — "Could not tell whether the problem is your account, the provider or
   Quiblo." Never a guess.

## 8. The details and the log

1. After any of the above, read the small line under the buttons.
2. **Expect:** a status if there was one, the engine's error name, data or no data, the account's
   state, and `host …`. **Fail if** it contains a username, a password, `/live/`, `/movie/`,
   `/series/` or any part of a path.
3. Phone: press **Copy details** and paste somewhere. Same rule.
4. Open Settings → App → Playback log on both apps. **Expect:** each failure above, newest first,
   at most twenty. Force-stop the app and reopen: **expect** the log to be empty.
