# BUG-045 Manual Sweep

**Verify on real streams**, with an Xtream film whose `get_vod_info` lists subtitles (the owner's
manual check, step 5, shows them and tests each URL).

1. Play a film whose listed subtitle URL answers 404 or times out. Open Subtitles. **Expect:** the
   panel's subtitle is listed; choosing it shows *(loading…)*, then *(unavailable)* and *That
   subtitle could not be loaded*. The film never stops.
2. Play a film whose listed subtitle works. Choose it. **Expect:** a second of buffering, then the
   film continues from the same moment with the subtitle showing; Subtitles now lists it as a
   track with a tick.
3. Turn on captions in Android accessibility settings and play the film from step 1. **Expect:** it
   starts and plays.
4. Attach a subtitle file of your own while a fetched provider subtitle is showing. **Expect:** the
   provider subtitle is still showing after the restart; yours is listed to choose.
5. Both apps: phone (sheet) and television (D-pad menu).
6. **Fail if** any film stops with an error because of a subtitle.
