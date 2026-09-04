# FEAT-032 Manual Sweep

This one is a property of a physical screen, so it is checked on one.

## 1. Playing still holds the screen on

1. Set the device display timeout to its shortest setting.
2. Play something and do not touch the remote for longer than that timeout.
3. **Expect:** the picture stays up.
4. **Fail if:** the screen dims mid-scene — that is the fault the flag exists to prevent, and it
   must not have been reintroduced.

## 2. Paused lets it go

1. Pause. Do not touch anything.
2. **Expect:** the screen dims or sleeps on the device's own timeout.

## 3. Buffering counts as watching

1. Pause and resume on a slow stream so it buffers for a while.
2. **Expect:** the screen stays on through the buffering.

## 4. The setting works

1. Settings → turn "Dim the screen when paused" off.
2. Pause and wait past the display timeout.
3. **Expect:** the picture stays up.

## 5. Both apps

Repeat 1 and 2 on the phone and on the television. The two players hold the flag in different
code and only one of them was restructured.
