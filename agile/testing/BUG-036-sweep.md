# BUG-036 Manual Sweep

**Verify on real streams** (`docs/STOPPERS.md` S3). A live window's length is the provider's
choice, and only a real HLS channel has one.

## 1. Pause past the window

1. Play a live HLS channel (a `.m3u8` URL).
2. Pause for two minutes — longer than most panels' window.
3. Press play.
4. **Expect:** a short buffer, then live playback. No "Reconnecting", no error.

## 2. Background and return

1. Play a live HLS channel, press Home, wait two minutes, return.
2. **Expect:** playback resumes at live within a few seconds.

## 3. Retry lands at live

1. Make a live channel fail (pull the network until the error shows), restore the network.
2. Press Try again.
3. **Expect:** the channel plays from live.

## 4. A film still resumes where it was

1. Pause a film for two minutes and press play.
2. **Expect:** it continues from the same moment, not from anywhere else.
