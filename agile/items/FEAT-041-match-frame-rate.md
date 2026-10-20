# FEAT-041: the television matches its refresh rate to the film

Asked for by the owner on 2026-10-20, once `BUG-064` had stopped the dropped frames and a judder
was still visible: "then we do the same, change the screen's rate to what is playing".

## Problem & Motivation

The playback info overlay showed it plainly: a 23.976 fps episode on a 60 Hz panel, 24 frames
rendered a second, none dropped — and still uneven. 60 is not a whole multiple of 23.976, so each
frame is held for two refreshes and then three, and every pan judders however well the film decodes.
Kodi, TiviMate, Plex, Netflix and Android TV itself all switch the panel's refresh rate for this.

## Scope

- While a film, an episode or a channel plays on the television, the panel is asked for a mode at
  its current resolution whose refresh rate is a whole multiple of the stream's frame rate
  (`matchingDisplayMode`): 23.976 → 23.976 Hz (or 24 Hz), 25 → 50 Hz. Nothing changes when the panel
  already shows the rate evenly (30 on 60, 25 on 50), when no mode fits, or when the only fit would
  lower the resolution.
- Asked through the window's preferred display mode — the one way to request a switch that is not
  seamless — and given back (no preference) when the player goes. The last rate is held between
  items, so moving to the next episode does not switch the panel back and forth.
- **On by default**, with a switch in the television's settings (`Match frame rate`), per profile.
- The overlay lists the refresh rates the panel offers at its resolution, so a television that has
  no 24 Hz says so; and the total of dropped frames is no longer amber — the per-second line is the
  one that says whether frames are being dropped now.
- **On screen** (overlay): the gaps between the times the engine scheduled the last second's frames
  for — average, shortest, longest, spread, and the share well off the average. Every frame shown is
  not the same as every frame shown evenly: 23.976 fps on 60 Hz alternates 33 and 50 ms, on 24 Hz
  every gap is 41.7, and gaps that wander with no pattern are a timing fault the counts cannot show.
  Collected on the playback thread only while the overlay is open.
- **Why each decoder started** (overlay): `first`, a codec error, or the format change the decoder
  before could not take, by the engine's own discard reasons. The reporter's screenshot showed a
  second decoder four seconds into an episode and nothing to say why.

## Explicit Non-Scope

- A stream that declares no frame rate (some Matroska files): left as it is, not matched on a guess.
- The phone: a phone panel runs at its own rates and is not asked to switch.
- A different resolution to get an even cadence.

## Acceptance Criteria

- A 23.976 fps film on a 60 Hz panel that offers 24 Hz: the overlay's display line reads 24 (or
  23.976) Hz, white, while it plays, and 60 Hz again after leaving the player.
- A 25 fps channel on a panel that offers 50 Hz: 50 Hz.
- With the setting off: nothing switches.
