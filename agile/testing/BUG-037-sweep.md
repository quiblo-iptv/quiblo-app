# BUG-037 Manual Sweep

**Verify on real streams** with an account whose `max_connections` is **1**. Keep the audit's
`player_api.php` call to hand: `active_cons` is the number that matters here.

## 1. Zapping

1. Play a live channel. Change channel five times, quickly.
2. **Expect:** every channel starts; no "in use on another screen"; `active_cons` never above 1.

## 2. Background on the phone

1. Play a live channel. Press Home. Wait two minutes.
2. Check `active_cons`. **Expect:** 0.
3. Return to Quiblo. **Expect:** the channel plays again, at live, within a few seconds.

## 3. Another input on the television

1. Play a live channel on the television. Switch the television to another HDMI input for two
   minutes. Check `active_cons`: **expect** 0.
2. Switch back. **Expect:** the channel plays again, at live.

## 4. Backing out on the television

1. Play a live channel, press Back to the catalogue. **Expect:** `active_cons` drops to 0.
2. Choose the same channel again. **Expect:** it plays.
3. Meanwhile start the same account on a second device. **Expect:** it is not refused.

## 5. A film keeps its place

1. Play a film, go to the home screen for a minute, return.
2. **Expect:** paused at the same moment, as before.
