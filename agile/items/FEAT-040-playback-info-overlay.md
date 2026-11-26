# FEAT-040: a playback info overlay to screenshot

Asked for by the owner on 2026-10-20, after 0.27.3 (`BUG-063`) shipped and films still stuttered
after a seek on their television: "a full debug window I can open and close, an overlay,
transparent, with all the information, that I can screenshot for you."

## Problem & Motivation

`BUG-063` was fixed by reasoning alone. A low frame rate after a seek could be the decoder (a
software one, or a hardware one falling behind), the network (a server slow to answer a range
request), or the screen (a film at 23.976 fps on a 60 Hz panel judders however smoothly it
decodes) — and nothing on screen told those apart. Every next fix would be another guess.

## Scope

- **One overlay, phone and television**, drawn over the video, semi-transparent, monospace. It
  takes no touch and no focus: the player under it works as if it were not there.
- **Opened and closed by one button**: the top bar on the phone, the end of the options row on the
  television. It stays open across rotation and across the next item.
- **Sampled once a second, and only while open** (`PlayerController.setDiagnosticsEnabled`).
- **What it shows:**
  - Stream: engine state, live or not, container, position, buffer ahead, buffer setting and cap.
  - Video: codec and profile, size, frame rate, bitrate, dynamic range, the decoder in use and
    whether it is hardware or software, whether software is currently allowed (`BUG-063`), and the
    last six decoders started with their times — a fallback after a seek shows up here.
  - Audio: codec, channels, sample rate, bitrate, decoder.
  - Frames: rendered and dropped per second, totals, the worst run of drops, drops to keyframe.
  - Network: the last second's throughput, the engine's estimate, bytes received, time since the
    last byte.
  - Recovery: retries, rebuffers, load time, stall and stutter reloads, decoder retries, live-edge
    rejoins, the last engine error.
  - Device: maker and model, Android version, the display's resolution and refresh rate, app version.
- **Amber** marks what would explain a stutter: frames dropping, a software decoder, under two
  seconds of buffer, any automatic reload, and a refresh rate that is not a whole multiple of the
  film's frame rate.
- A wall-clock time with seconds in the title, so screenshots sort themselves.

## Explicit Non-Scope

- No URL, host, username or password, anywhere in it (AC-XT-04): a screenshot is meant to be shared.
- No logging to a file, no sharing button. A screenshot is the whole export.
- No translation of the values: they are the engine's own words, for whoever fixes the bug.

## Acceptance Criteria

- The button opens the overlay over a playing film and closes it again, on both apps.
- With the overlay open, play, pause, seek and the remote's keys all work as before.
- A film decoding in software shows the decoder in amber with `SOFTWARE`.
- A 23.976 fps film on a 60 Hz display shows the display line in amber.
- Nothing is sampled while the overlay is shut.
