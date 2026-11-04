# BUG-065: a film dropped frames after a skip or a resume, and smooth from the start

Reported by the owner on 2026-11-04, after `BUG-063` and `BUG-064`: the same 25 fps episode plays
smoothly when started from the beginning, and drops frames heavily after a skip or when resumed
from the middle. The stutter reload from `BUG-063` does not help, because it also starts from the
middle.

## The owner's reading

Starting from second 100 lands at "frame 12 of that second" instead of frame 0, and the player
never recovers the gap.

A decoder keeps no count of frames per second, so not literally — every frame carries its own
time. But it is close to a real difference. An exact seek lands *between keyframes*: the decoder
starts at the keyframe before the target and has to race through every frame up to it while the
clock is already running, and only then shows anything. A film started from zero starts on a
keyframe and has nothing to catch up. That is the one structural difference between the smooth
case and the bad one, and it is cheap to remove.

## Root cause

Not proven on a device. This tests the hypothesis by removing the difference.

## Scope

- A skip in a film or an episode lands on a keyframe: the next one going forward, the previous one
  going back (`seekLandingFor`, `SeekParameters.NEXT_SYNC` / `PREVIOUS_SYNC`).
- A start at a saved place, a reload after a stall or a stutter, and a retry as HLS land on the
  keyframe before the place. The engine cannot snap a start position before it knows the
  keyframes, so the snap is made when the item's tracks are first known — before anything is
  shown, while that keyframe is still in the buffer.
- Live stays exact.

## Explicit Non-Scope

- Live channels.
- The stutter watch from `BUG-063` is kept as it is.

## Acceptance Criteria

- Resume the episode from the middle: the playback info overlay shows the same dropped-frame rate
  as playing it from the start.
- Skip forward and back several times: no sustained drops after any of them.
- A forward skip never lands behind where it started, and a backward one never ahead.

## If it is still there

Then the gap is not where the picture starts, and the next suspect is the audio clock after a seek
(video frames judged late against it by a constant offset). The overlay should then show frames
dropped one at a time at a steady rate, with nothing to a keyframe.
