# FEAT-039 Manual Sweep

Run on the phone **and** the television, with at least two named profiles and a guest session.
Includes `FEAT-038`'s checks.

## Phone

1. Settings → Profile → **Manage profiles**. **Expect:** every named profile, no guest.
2. Tap a profile; change its name and face; **Save**. **Expect:** the new name and face in the list,
   in Settings and on the chooser; its favourites and Continue watching unchanged.
3. Open it again → **Delete this profile**. **Expect:** "Delete {name}?" and a sentence saying its
   favourites, continue watching and settings go with it. **Keep**: nothing happens.
4. Delete it for real. **Expect:** gone from the list and the chooser; other profiles untouched.
5. Delete the profile that is watching. **Expect:** the chooser appears.
6. On the chooser, long-press a profile. **Expect:** the same editor. Long-press the guest tile:
   **expect** nothing.
7. With TalkBack on, focus a profile on the chooser. **Expect:** the long-press action is announced
   as "Rename, change face or delete".

## Television

1. Settings → Profile → **Manage profiles** → **Show**. **Expect:** a row per named profile, no guest.
2. **Edit** a profile; Down to the faces, press one; Down to **Save**. **Expect:** saved, and the row
   is a row again.
3. **Edit** → **Delete**. **Expect:** the question, with **Keep** focused. Press OK: nothing deleted.
   Press Back from the question: nothing deleted.
4. **Delete for good**. **Expect:** gone; the other profiles untouched.
5. On the chooser, hold OK on a profile tile. **Expect:** the editor. Try Menu, on a remote that has
   one. **Expect:** the same. Back returns to the tiles.
6. **Expect** the hint line under the tiles whenever a named profile exists, and not on a first run.
