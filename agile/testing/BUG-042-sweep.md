# BUG-042 Manual Sweep

Run on the phone and the television, with an Xtream source that has favourites and a half-watched
film.

## 1. A new password, history kept

1. Change the account's password at the provider. Confirm a channel now fails in Quiblo.
2. Sources → Edit on that source. **Expect:** name, address and username filled in; the password
   field empty, saying empty keeps it.
3. Type the new password; Save and reload.
4. **Expect:** the catalogue reloads; channels play; the favourites and the half-watched film are
   still there.

## 2. A new address

1. Edit the source's address to the provider's new DNS name; save.
2. **Expect:** as above.

## 3. A mistake changes nothing

1. Edit the source with a wrong password; save.
2. **Expect:** a failure message; channels still play with the old password; editing again shows
   the old address and username.

## 4. Keeping the password

1. Edit only the name, leaving the password empty; save.
2. **Expect:** renamed, reloaded, still playing.

## 5. A playlist

1. Edit an M3U source's address.
2. **Expect:** no username or password fields; the playlist reloads from the new address.
