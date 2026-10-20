# BUG-063: a film stutters at a low frame rate after a seek

Reported by the owner on 2026-10-20: on some films and episodes, skipping forward or back leaves
the picture stuttering at a low frame rate. Reopening the film or restarting the app does not help;
clearing the cache "fixes" it, and later the same film plays fine. TiviMate shows the same thing on
the same provider and device.

## Problem & Motivation

The film keeps playing, so nothing in the engine reports it: no error, no retry, no diagnosis. The
viewer is left watching a slideshow with no way out but to leave the film.

## Environment

- Platform: both, mostly the television
- Source kind: Xtream films and episodes (progressive)
- Symptom: low frame rate after a seek, for as long as the film is watched

## Root cause

Not proven on a device. What is known:

- Quiblo keeps **no media cache**. `OkHttpClient` has no disk cache and the player no
  `CacheDataSource`; clearing the app's cache removes only artwork. So the cache is not what
  fixes it, and what changes in between (the device freeing its hardware decoder, or the
  provider moving the stream to another server) is outside the app.
- A low frame rate with no error is the decoder dropping frames it delivers too late. The
  likeliest cause on a television box is a seek that has the engine set the decoder up again and
  land on the software one (`setEnableDecoderFallback(true)`) when the hardware one is not free.
- TiviMate behaving the same points the same way: device or provider, not Quiblo's code.

## Scope

- While a film or an episode is playing, the decoder's rendered and dropped frame counts are
  compared every 2 s (`isStuttering`). A quarter or more of the frames dropped is stutter.
- Six seconds of it in a row, and the film is stopped and loaded again where it is
  (`reloadsForStutter`): a fresh decoder and a fresh connection. Up to twice per item.
- A pause, a buffer or a seek starts the count again, so the frames thrown away around a seek are
  never counted.

## Explicit Non-Scope

- Live channels: unchanged. Reloading one is a new connection to the panel.
- A device that cannot play the film smoothly at all: after two reloads it is left to play.
- Stalls with a spinner (the network not keeping up): that is `rebufferCount`, and the buffer
  setting, not this.

## Acceptance Criteria

- A film that stutters after a seek recovers within about ten seconds, at the same moment.
- A film that plays smoothly is never reloaded, before or after a seek.
- A film that stutters through two reloads is not reloaded a third time.
