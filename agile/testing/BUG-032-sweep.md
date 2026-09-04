# BUG-032 Manual Sweep

## SWEEP-BUG-032-01 — Back hides controls on a real remote

- **Priority:** P0
- **Preconditions:** Television build installed; play any VOD long enough for controls
- **Steps:**
  1. Press Down (or Centre) so the player controls appear and a button is highlighted
  2. Press Back once
- **Expected:** Controls hide; playback continues; still in the player
- **Pass/Fail:**

## SWEEP-BUG-032-02 — Second Back exits

- **Priority:** P0
- **Preconditions:** SWEEP-BUG-032-01 just passed (controls hidden, still playing)
- **Steps:**
  1. Press Back once more
- **Expected:** Leave the player to the previous screen (detail / catalogue)
- **Pass/Fail:**

## SWEEP-BUG-032-03 — Track menu still wins

- **Priority:** P1
- **Preconditions:** Controls up; open audio or subtitles panel
- **Steps:**
  1. Press Back once
  2. Press Back again
- **Expected:** First closes the panel (controls remain); second hides controls
- **Pass/Fail:**
