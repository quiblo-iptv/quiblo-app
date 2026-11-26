# FEAT-040 Manual Sweep

On the phone and on the television box.

1. Play a film; tap the info button (phone: top bar; television: last button of the options row).
   **Expect:** the overlay at the top left, updating once a second; tap again and it goes.
2. With it open: play, pause, seek, change audio, lock the phone screen. **Expect:** all work; the
   overlay stays.
3. Rotate the phone, and move to the next episode. **Expect:** still open.
4. Play an HEVC film and read the decoder line. **Expect:** the box's hardware decoder, not
   `SOFTWARE`; if it is software, the line is amber.
5. Skip forward and back on a film that stutters. **Expect:** the decoder history shows each new
   decoder with its time; dropped/s goes amber while it stutters.
6. Read the display line on a 60 Hz television with a 23.976 fps film. **Expect:** amber.
7. **Fail if** the overlay shows any part of a stream address, or takes a tap or the remote's focus.
