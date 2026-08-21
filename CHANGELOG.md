# Changelog

What changed in Quiblo, in the words of the people who changed it.

Every released version has a section here, and the release workflow publishes that section as the
release notes. **A generated list of commit subjects is not release notes.** Somebody arriving at
the releases page wants to know what they get by installing this version, and a link to a diff
answers a different question — one they did not ask and cannot read.

Write the entry when you build the thing, not at release time. The version headings are added by
the release lane; put new lines under `Unreleased` and they move up on their own.

Formatted after [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioned by
`docs/RELEASE-MANAGEMENT.md`.

## Unreleased

### Added

- **Animated branding splash screen with version indicator.** A glowing, animated launch screen with the signature Quiblo mark, title, tagline, and version number positioned at the bottom right welcomes viewers before smoothly transitioning into the app.
- **Ambient lighting backdrop on movie and series detail screens.** Detail screens for films and television series now extract ambient color accents from poster artwork and softly project them onto the background for an immersive cinematic backdrop on both TV and mobile.
- **A featured hero slider on Android TV with D-pad navigation.** The TV browse home features a full-bleed hero slider with a 6-step D-pad navigation flow across top controls, play, in-slider favorites, and slide pagination indicators.
- **Toggle favorites directly from the TV hero slider.** A heart button beside the play button allows adding or removing featured movies and series from your favorites without leaving the hero banner.
- **High-resolution backdrops and artwork.** Movie and series backdrop images are upgraded to full resolution and posters to high-definition formats for sharp display on large television screens.

### Changed

- **Polished TV home screen layout and styling.** Content shelves are ordered (Popular Movies, Popular Series, Recently Added, You May Like) with title-cased headings, and a subtle dark gradient scrim keeps top bar navigation clear over full-bleed backdrops.

## 0.20.2

### Fixed

- **Pressing back in the player exits playback immediately.** The player controls no longer intercept the back button to only hide themselves, so backing out of a stream returns you straight to the previous screen without requiring multiple back presses.

## 0.20.1

### Fixed

- **Backing out of a film puts you back on the tile you opened it from.** The television used to
  land you at the top of the catalogue, so finding your place again meant scrolling the row you
  had just been in. Every tab keeps its own place too, and switching between them no longer
  snatches the remote off the tab bar.
- **The remote can no longer be left on nothing inside the player.** The controls arrive with the
  stream — the seek buttons, the timeline, the subtitle button — so a button could disappear from
  under the cursor and leave presses going nowhere. The player insists on holding focus, and asks
  again the moment nothing inside the controls has it.
- **Scrubbing the timeline shows you where you are going.** The mark has a handle, and it sits in
  a lane of its own so the buttons underneath do not shift as it grows.
- **A focused search result is no longer sliced along its title.** The room a tile reserves to
  grow into now belongs to the tile itself, so the list brings all of it into view instead of
  stopping ten millimetres short.
- **"You may like" appears for people who star titles and finish none of them.** A favourite is
  evidence about your taste, and the row now treats it as such — a starred film nobody has played
  is a reason to suggest something in its own right.

### Changed

- **Advanced search can look at live channels from the search screen.** It is a switch beside the
  field, rather than a setting two screens away.
- **Include hidden is a switch, not a chip.** It sits beside the field instead of behind the
  suggestions, and the sentence saying what is being searched moves under the field, where it is
  read.
- **A genre chip chooses, and Clear unchooses.** Pressing a chip a second time no longer takes it
  off, which made a list of chips behave differently depending on where you had been.

## 0.20.0

### Added

- **The television asks for your playlist on the first screen, instead of leaving you to find
  it.** Setting Quiblo up used to end at the terms and drop you into an app with nothing in it,
  where the next thing to do was four presses deep in Settings. There is a third page now: add
  the playlist or account you already have and it loads before the app opens, or skip it and do
  it later. If the playlist will not load, it says so and offers to try again — rather than
  letting you in and leaving you to work out that nothing arrived.
- **You can drag the film's timeline with the remote.** The progress bar takes focus now, one
  press below play and pause. Left and right move a mark along it and **the presses stack**: six
  presses are one jump of six, made half a second after you stop, instead of six separate jumps
  and six re-buffers. Hold a direction and it speeds up, so crossing a two-hour film takes a few
  seconds — while the first few presses are still worth exactly the skip interval you chose in
  Settings, because that is what a small correction is for. Live channels have no timeline, since
  there is nothing to move through.
- **The television can tell you when there is a newer Quiblo, and fetch it.** A television has no
  store to update this app from, so Settings → About now has a **Check now** button. It looks at
  this project's own releases page, tells you if a newer version is there, downloads it, and
  **checks it against the published checksum before offering to install it** — a download that
  does not match is deleted rather than handed to the installer. Your set still asks its own
  permission before anything is installed, and if it refuses outright, the file is downloaded and
  named so you can install it from a file manager. **Nothing is checked unless you press the
  button**: Quiblo does not look at anything on its own, and that has not changed.
- **You may like actually looks at what you watched.** It scored on genre and nothing else, which
  is why watching One Piece produced The Boys, The Umbrella Academy and a dubbed Arabic family
  drama: at the level of "series, Action & Adventure" those are the same thing. It now weighs
  thirteen things — including whether something is **anime** rather than merely animated, what
  language it was made in, the words in its description, how many times you watched it, at what
  hour, whether you searched for it or took it off a shelf, whether it is a favourite, and what you
  said about it. Each of your strongest few titles proposes its own four, so somebody who watches
  anime and one cookery programme gets suggestions from both rather than from the average of them.
- **And it waits until it has something to say.** Below five titles watched, three of them most of
  the way through, the row is not drawn at all. One thing watched cannot produce four honest
  suggestions, and a confident wrong answer is worse than no answer.
- **You can say what you thought.** A thumbs up and a thumbs down on every film and series, on both
  apps. Pressing the lit one again takes it back. A thumbs down stops that title suggesting
  anything and stops it being suggested; a thumbs up makes it count for more. Nothing leaves the
  device — there is nowhere for it to go.
- **Quiblo keeps your catalogue up to date on its own.** Until now the only way a new film reached
  the app was somebody opening Settings and pressing Refresh, so "Recently added" was really
  answering "what has your provider added since you last thought to check". It now syncs every four
  days in the background, and it *merges* rather than rebuilding: a title that is still there keeps
  its place and the date it first appeared, a title that has just arrived is dated now, and one your
  provider has dropped goes. That last part is what gives an M3U playlist a recently-added row at
  all — an M3U carries no dates, so the only honest answer is when Quiblo first saw it.
- **And it checks what is popular every forty hours.** That check used to happen only when you
  opened For You, which made "what is popular now" a question about how often you opened a tab. It
  still costs at most two requests in that window, and still nothing at all without your own Movie
  Database key.
- **You can put your categories in your own order.** Settings has had "hide" and "rename" since it
  had a category list; it now has move up and move down as well, on the phone and on the
  television. The order you set is the order Live, Movies, Series and Favourites draw their rows
  in, on both apps. Categories you have not moved stay in your provider's own order, behind the
  ones you have — so ordering three shelves out of ninety moves three shelves and leaves the rest
  alone. Your order survives a refresh, because it is stored against your provider's own name for
  the category rather than against anything that gets renumbered.
- **Now popular is two rows, and each is a top ten.** Films and series came from two different
  lists and were drawn as one row of five and five, numbered 1 to 5 twice with a badge to tell the
  halves apart. They are now **Popular films** and **Popular series**, ten each, numbered once, and
  the number stands beside the poster where you can read it from the sofa.
- **A popular title your provider does not carry now says so instead of disappearing.** The row
  used to quietly drop them, so a top ten arrived with four films in it and no way to tell whether
  the other six were unpopular or simply absent from your account. Every place is filled: an
  unavailable title keeps its number, shows its poster dimmed with an "Unavailable" mark, and says
  **Not available yet** under it when you press it. Nothing is fetched for it and nothing tries to
  play. As before, none of this exists at all without your own Movie Database key.

### Changed

- **The playlist screen sits in the middle of the television, and Save is lit like Search.** It
  started at the left edge and ran to some fraction of whatever panel it found itself on, which on
  a fifty-inch set meant a form full of long URLs pressed against the bezel with half the screen
  empty beside it. It is a centred column now. Save carries the same travelling light that Search
  and Play have — dimmer than Search's, because Search is the only thing on its screen and Save
  sits beside a form you are still filling in.
- **Search's light now fills the screen like every other tab's.** It was drawn on the search
  screen rather than behind the whole app, so it stopped dead at the margins and left a dark band
  under the tab bar — a lit rectangle on a dark screen rather than light in a room. It reaches all
  four corners and passes behind the bar now, exactly as the light from a poster does.
- **For You draws last night's rows the moment it opens.** Now popular and You may like were both
  worked out from scratch every time the tab was opened — a whole catalogue matched against a
  popular list, and a scoring pass over every film and series you have — while you watched an empty
  shelf. Both are remembered now and drawn straight away, then quietly brought up to date behind
  what is on screen. They are remembered per profile, so nobody sees anybody else's.
- **You may like grows rather than reshuffling.** The same viewing scored twice a fortnight apart
  gives mostly the same titles in a different order, and a shelf that rearranges itself for no
  visible reason is one nobody learns the shape of. What is there stays where it is and new
  suggestions arrive on the end. Two things do leave it: a suggestion whose cause you have watched
  again since — a second viewing is the strongest thing you can tell it, and it should move that
  title's suggestions rather than leave a fortnight-old answer in front of them — and a suggestion
  your provider no longer carries.
- **Recently Added holds fifteen.** It held forty, which was chosen as more than anyone would walk
  through rather than as an answer to "what is new".
### Fixed

- **"Resume from" is there when you come back.** Watching four minutes of something and pressing
  back regularly gave you a **Play** button, as though you had never opened it — and the only way
  to get the right button was to leave the screen and go back into it. Three things were wrong and
  all three are fixed. The screen asked where you had got to at the same moment the player was
  writing it down, and nothing decided which happened first; it now watches the answer instead of
  asking once, so a position that arrives a moment late still moves the button. On the phone the
  back press that made the position worth saving was also what cancelled the save; it is now
  written somewhere the screen going away cannot reach. And a position is written down every ten
  seconds of playback rather than only when you stop, so a television switched off at the wall or
  an app killed for memory no longer loses everything since you pressed play.
- **A film's details sit in the middle of the television screen.** A series fills the screen with
  its episodes and a film has nothing below its buttons, so a film's details were pressed against
  the top with the whole lower half of the panel empty. A long description still starts at the top
  and scrolls exactly as before.

- **Hiding a writing system now applies to For You as well.** Now popular and You may like were the
  two rows that ignored the setting entirely: hide Arabic, and the catalogue, the search results and
  Recently Added would all respect it while those two carried on proposing Arabic titles. They were
  the worst two rows to get it wrong in, because they are the ones that offer something you did not
  ask for. Both now honour it. A row can come out shorter than its usual length when a hidden title
  would have filled a place — the alternative was letting the setting decide which titles count as
  popular, which is a different claim entirely.

## 0.19.0

### Added

### Changed

- **The ambient light keeps up with the picture.** It read the screen a little under twice a second
  and then took most of another second to change, so at worst the glow was around two seconds
  behind the frame it came from — near enough to look deliberate, far enough to look like two
  separate things. It now reads four times as often and settles twice as fast, which is close
  enough to read as the picture's own light. A hard cut is still a fade rather than a flash.
- **And the light behind the catalogue keeps up with the remote.** It took most of a second to
  change colour, which was set that way so that holding right along a row would not strobe — but
  nothing is even asked for until the remote has rested on a tile, so there was never a queue of
  colours to strobe between. It now settles as quickly as the player's.
- **Search and Live put out the light the catalogue left on.** The glow behind the app comes from
  whatever poster the remote is resting on, and neither of those screens has one — so arriving at
  either of them left the colours of a film you looked at two tabs ago sitting behind an empty
  search box. They now fade it out on the way in.
- **And Search lights itself instead.** Two soft pools that travel round the screen on the same
  six-second circuit as the highlight going round the search box, turning slowly through the
  colours as they go. It is exactly as bright as a poster's light and it needs nothing from the
  network.
- **Generated profile pictures are faces now.** They were four coloured shapes; they are a face on
  a coloured tile — the same generator library, a different one of its styles. Every profile
  already wearing a generated picture becomes a face the next time the app opens, and it is the
  same face on your phone, on the television, and after a restore, because what is stored is still
  only the seed. Pictures picked from the illustrated set are untouched.
- **Advanced search answers a genre instead of thinking about it.** Filtering by a genre used to
  read every film and series on your account and re-clean all of their titles from scratch — fifty
  thousand of them on a large provider, every single time you pressed a genre, with nothing kept
  between presses. It looked like the app had hung; it was working, extremely hard, on a question
  it had already answered once. Each title's cleaned name is now worked out when your playlist is
  loaded and remembered, so a genre is one indexed lookup.
- **Hiding a writing system no longer costs anything to read.** The same change underneath: which
  scripts a title is written in is worked out once and stored, rather than re-read letter by letter
  for every row of every screen, every time the screen changes. Movies, Series and Live all draw
  sooner for it, and a title you have hidden is no longer fetched from the database only to be
  thrown away.
- **The "how much of your catalogue is described" figure is counted rather than derived**, which
  was the third thing making the search screen slow to open.
- **Movies, Series and Live load a screenful rather than a catalogue.** Opening a tab used to read
  every single title of that kind out of the database — tens of thousands of rows on a large
  account — build an object for each, and hand the lot to a list that draws about a dozen. It now
  loads pages as you scroll. On the television the poster grid asks for the first forty titles of
  each category, which is what a row can show anyway: nobody presses right forty times.
- **The channel list on the television pages too**, and still asks for the guide of the first ten
  channels the moment it opens, so the list is not blank until you rest on a row.

One consequence worth knowing: pressing a title now hands the player the titles loaded so far to
zap along, rather than every title in the catalogue. The old list was only complete because the
screen was paying to load all of it.

Upgrading keeps your catalogue exactly as it is. The new information is filled in quietly after the
app starts, and until it is, hiding works exactly as it did before — nothing appears or disappears
in the meantime.
- **Back twice on Search closes Quiblo, and it asks first.** Backing out used to hand the press to
  the system, which puts the app in the background rather than closing it — so the next launch
  carried on as whoever was watching last, and there was no way to get the "who is watching"
  screen back. The first press now says "Press back again to close" along the bottom, the second
  closes, and closing forgets who was watching so the next launch asks.

### Fixed

- **Now popular no longer goes missing on the television.** The row is fetched with your Movie
  Database key, and the key is kept encrypted — which means reading it takes a moment. For You
  was built before that moment had passed, found no key, fetched nothing, and had no way to ask
  again for as long as the tab stayed open. It now waits for the key, and a key pasted into
  Settings while the tab is open fills the row straight away instead of waiting for a restart.
- **The settings and profile buttons are highlighted again.** Sometimes the app opened, or came
  back from Settings, with nothing on the top bar lit and the remote apparently doing nothing — the
  selected tab kept its underline, which made it look as though the bar was awake when it was not.
  The app was asking the bar to take focus a moment before the bar existed, and not noticing it had
  been refused. It now asks again until it lands.

## 0.18.0

### Added

- **The television's Recently Added tab is now For You, and holds three rows.** The first is
  Recently Added itself, unchanged. The second, **Now popular**, is what the world is watching of
  the things your own provider actually carries — five films and five series, numbered, from The
  Movie Database's weekly lists. The third, **You may like**, suggests titles from what has been
  watched on your profile, and every tile says which of your own choices put it there.
- **Suggestions are worked out on the device and nowhere else.** No account, no server, nothing
  sent anywhere: it is arithmetic over the genres already in your cache, weighted by how much of
  each thing you actually watched and by how recently. It is per profile, so nobody in the house
  sees anybody else's viewing.
- **A row that has nothing to say is not drawn.** No Movie Database key, a catalogue nobody has
  scanned yet, a profile that has not watched anything: each of those simply removes a row rather
  than leaving an empty shelf or a spinner that never finishes.
- **Now popular costs two requests a week.** The lists are kept for seven days, so opening the app
  every evening asks for nothing extra, and a service that refuses leaves last week's row standing
  rather than emptying it.

### Fixed

- **Hiding a writing system now hides a title with any of it in.** It read the first letter of a
  title and stopped, so anything a provider had prefixed in English — a quality marker, a channel
  number, a stray "The" — came back in full for somebody who had asked not to be shown that
  script. A tag in brackets on the end is still ignored, because `Oppenheimer [عربي]` is an
  English film with an Arabic dub and hiding Arabic should not lose it. The same tag written
  without brackets cannot be told apart from a title, and that one is hidden.
- **A hidden category is hidden from search too.** Switching a category off in Settings took it
  out of the category list and nowhere else, so every search kept answering from it — the one
  place you are least able to tell where a result came from. Advanced search has an Include
  hidden switch for the times you want to look there anyway; it covers hidden writing systems in
  the same press.
- **Advanced search returns films *and* series.** A genre search read a fixed number of titles
  from the catalogue and only then split them into the two rows — and because a provider's films
  are all stored ahead of its series, on any real account those titles were all films and the
  series row came back empty. Which row was empty depended on nothing you could see, which is why
  it looked random. Each row now takes its own share.
- **Hiding a writing system no longer shortens the results.** The filter ran after the database
  had already cut the list to a screenful, so a search with plenty of matches could show one.

### Changed

- **Advanced search leaves live channels out.** A live channel carries no genre and never will,
  so a genre filter could only match one on the words in its name — which filled a row nobody
  filtering by genre had asked for. Settings has a switch for anyone who wants it back, and with
  it off the channels are not looked up at all rather than looked up and discarded.
- **The television asks before it types.** Every text field on the television — the search box,
  the playlist and account forms, the metadata key, a profile's name — used to throw the
  on-screen keyboard over the screen the moment the remote landed on it, so walking down the
  settings list opened and dismissed a keyboard at every field on the way. A field now rests
  under focus and opens its keyboard when it is pressed, the same way a field on a phone is
  tapped before it is typed into.
- **The category editor is a room you enter, not a list you walk through.** It was a scroller
  among the settings rows, so passing it on the way to anything below cost one press per
  category — two hundred of them on a real account. It is now shut by default and says how many
  categories there are and how many are hidden; one press opens it, Back closes it, and walking
  off either end closes it behind you. The rows inside have room to breathe, which they did not.

## 0.17.0

### Added

- **Recently Added says what each poster is.** A row that mixes films and series is the one row
  in the app where the screen cannot already say which is which, so each tile carries a Movie or
  Series label in the corner opposite its score.
- **Recently Added covers the last thirty days**, rather than the newest forty titles whenever
  they arrived. A service that added nothing this month now says so instead of showing last
  spring.
- **A playlist that carries no dates gets the end of its own list** — the latest films and series
  in the order the provider lists them, interleaved so neither crowds the other out. The row is
  headed "Latest in your playlist" rather than "Recently added", because where something sits in
  a playlist is not a date and the screen does not pretend otherwise. It replaces the empty tab
  0.16.0 shipped for a playlist with no dates in it.
- **A film says what year it is from and how long it runs**, beside its score and certificate.
  Both come from the playlist where the panel supplies them and from The Movie Database where it
  does not, so the line fills in for an M3U playlist too. The year replaces the full release date
  the television used to print: nobody choosing a film needs the day of the month.
- **A series says what year it began**, on both apps, and it says so whether or not a metadata
  key is configured — the year is usually the panel's own.
- **Every episode says how long it is.** Read from either field panels use for it, including the
  written `00:47:15` form, and omitted rather than shown as zero where a provider does not time
  its episodes.

### Fixed

- **The television's channel list shows what is on now without being prodded.** The guide was
  fetched only for the row the remote had come to rest on, and nothing has focus when Live opens
  — so the whole list drew with no programme against any channel until you happened to stop on
  one. The top of a fresh list is now asked about straight away. It is a fixed ten channels and
  no more, because "fetch for every visible row" against a large account is what gets a provider
  to start refusing an app.
- **A guide that is not arriving says why.** A provider refusing guide requests, and a provider
  that simply has no listings for your channels, both used to look exactly like a blank line —
  which reads as Quiblo being broken. They are now two different sentences, and one of them is
  worth taking to your provider.
- **An hour of looking up films and series is not thrown away by a restart.** A scan that had
  finished could come back reporting nothing done and a search screen describing none of the
  catalogue. Two causes, both closed: work already looked up now counts as looked up whatever
  its age, so a device whose clock is wrong when it starts up — a television that boots before
  it fetches the time — can no longer age the whole cache at once; and the scan pushes what it
  has learned onto the disk as it goes rather than leaving it in a log a power cut can take
  back. Settings on both apps now shows how many titles are held, which is the number to read
  before switching off and again afterwards.
- **Saving the same TMDB key twice no longer empties the cache.** Clearing on a *changed* key is
  deliberate — a different key can answer differently — but re-entering the key already saved
  threw away every lookup standing behind it.

## 0.16.0

### Added

- **A Recently Added tab on the television**, between Live and Movies, holding the newest films
  and series on the service in one row rather than one row each — somebody wondering what is new
  is not also choosing between two formats. Xtream accounts fill it, because a panel says when it
  added each title and the app now keeps that date; M3U playlists carry no dates at all and the
  tab says so instead of showing a list ordered by nothing. It costs no extra request to the
  provider: the dates arrive inside the film and series lists the app already fetches.

## 0.15.1

### Fixed

- **The television's launcher tile is a banner rather than a cropped icon.** It was the square
  app icon dropped into a 16:9 frame, which left a dark bar down each side and a mark stretched
  to the top and bottom edges. It is now drawn at 320x180 with the name beside the mark, which
  is what a viewer picks the app out by from across a room.
- **The gear and the profile picture sit together at the end of the top bar.** They were spaced
  as far apart as two tab labels and read as two unrelated controls.
- **Advanced sits under the search field at rest, and the whole resting screen is centred.** It
  was beside the field, balanced by an invisible copy of itself, which centred the field while
  leaving what a viewer actually sees hanging to the right of the middle. The block also sits a
  little above the half-way line rather than on it, because a block on the true middle of a
  television reads as low.

## 0.15.0

### Added

- **Pick a face for a profile on the television.** Creating a profile offers a row of generated
  pictures rather than the fixed set of drawn faces, so a household of five is not choosing
  between five things that look alike from the sofa. A profile made before this still shows its
  initial, and one made on the television draws the same on the phone.
- **The profile icon is on the top bar**, to the right of the gear, and it does the one thing a
  household reaches for: hand the remote to somebody else. Right from the gear reaches it, Left
  goes back.

### Fixed

- **The resting search screen is centred on the panel, and stays centred if the bar above it
  grows.** It is measured from both ends now instead of from the name in the middle of it.

## 0.14.3

### Fixed

- **The search screen is centred, on any panel.** It was a fixed gap under the tab bar, then a
  sum worked out from the window's height — the first moved every time the mark or the name was
  resized, the second was right on one screen and wrong on others. Both the area and the block
  are measured now, so there is no number left in it to be wrong.
- **The mark is the size it says it is.** It was drawn from the launcher icon, which keeps its
  outer third as margin for a launcher to crop, so a large logo came out filling about sixty per
  cent of its space. It fills the space now, and the name under it is smaller than it is.

## 0.14.2
### Fixed

- **The mark on the search screen is the size it says it is.** It was drawn from the launcher
  icon, which reserves its outer third as margin for a launcher to crop — so asking for a large
  logo produced one filling about sixty per cent of its space, and it looked small beside its own
  wordmark. It now fills the box, and the word under it is smaller than it is.
- **The search field sits on the middle of the screen.** The invisible spacer balancing Advanced
  was a copy of its text and not of the control, so it was short by the button's padding and the
  field sat thirty pixels left of the mark above it.
- **The travelling highlight travels.** It was a gradient rotated behind the box, which spreads
  colour evenly by angle — on a field ten times wider than it is tall the light crawled the short
  ends and leapt across the long ones. It now moves along the outline itself at a constant speed,
  and it is softer than the first attempt, which read as a loading bar rather than as light.

### Changed

- **The television's text boxes are plain boxes now.** They were Material's outlined fields,
  whose label floats up out of the box and leaves a strip of empty space above it — which is
  where the search field's highlight was being drawn. The box is the box; the label sits inside
  it and the typed text replaces it.
- **The mark and the name on the search screen are bigger**, and sized against a television
  rather than a laptop.
- **Important buttons carry the same highlight, at a third of the brightness.** Play, Resume,
  Save and Try again say "this is the one" without competing with wherever the remote is.

### Fixed

- **The background light keeps up with the remote now.** It was fetching each poster again from
  the provider rather than reusing the one already on screen, so the colours arrived seconds
  after the tile did. Every poster's colours are worked out once and kept, and nothing starts
  until focus settles — so walking back along a row is instant and flying through one costs
  nothing at all.
## 0.14.1

### Fixed

- **Search only offers Advanced once now.** It was on screen twice — beside the field and again
  in the row under it — which on a television reads as two controls rather than as one with two
  ways in. Press right from the search box to reach it.
- **The search box sits on the middle of the screen again.** Putting Advanced next to it had
  pushed it left of the mark and the name above it.
- **The search box's highlight is visible from a sofa.** It was a hairline drawn just inside the
  outline, which from across a room looked like a smudge beside the field rather than the field
  lighting up. It now traces the outline itself and carries a soft halo.

## 0.14.0

### Added

- **The television is not black any more.** Whatever has focus lights the screen behind it — the
  poster you are looking at tints the corners of the catalogue, and in the player the picture
  lights its own letterbox bars, so a film in 2.35:1 or a channel in 4:3 sits in a room rather
  than in a void. It is light added to the black, never a replacement for it: artwork with no
  usable colour in it leaves the screen exactly as it was.
- **Search shows the app's own mark above the name**, and **Advanced now sits beside the field**
  as well as under it — press right from the search box to reach it.
- **The search field has a slow travelling highlight** while the remote is somewhere else. It
  stops the moment the field takes focus, because the focus ring is the one moving thing on a
  television that must never be competed with.

## 0.13.1

### Fixed

- **"On now" no longer freezes at the moment you opened the screen.** The guide asked what the
  time was once, when the list was built, and then kept re-answering with it — so a channel list
  left open went on showing whatever was airing when you opened it.
- **Errors say what went wrong again in released builds.** Every typed playback and network error
  — timed out, unreachable, unsupported format — was being decided by a class name that the
  release build renames, so a shipped APK could only ever say "something went wrong". Debug
  builds were unaffected, which is why it went unseen.
- **A cancelled request is no longer reported as a provider failure.** Leaving a screen while it
  was loading produced an error about your playlist, and the work carried on in the background
  after it had been cancelled.
- **Skipping and browsing recover from the clock going backwards.** Both request limiters
  measured against a clock that a time correction can move backwards — routine on a television
  box with no battery-backed clock — and a backward jump stopped them letting anything through
  until it caught up.
- **An episode no longer restarts from the beginning when the screen is rebuilt.** The guard
  against reloading recognised films and channels and quietly exempted every episode, so
  returning to one lost your place in it and any subtitle file you had attached.
- **A subtitle file that is too large is refused instead of crashing.** The size limit was
  checked after the whole file had been read into memory, so the one case it exists for was the
  one case it could not prevent.
- **Searching for a title containing `%` or `_` finds that title.** They were being read as
  wildcards, so those searches matched far more than they should.
- **Restoring a backup is all-or-nothing.** A failure part-way through used to leave half an
  import behind.
- **The app recovers when the encrypted credential store will not open** — after a restore to a
  new device, say — instead of failing every playlist that needs a password with no way forward.
- **Films and series with the same name no longer share a detail screen.** Two titles a provider
  filed under one name could show each other's plot and artwork.
- **Xtream addresses with IPv6 or a username in them are accepted.**

### Changed

- **A large playlist is parsed off the screen's thread and read as it arrives** rather than held
  in memory whole. A 67,000-entry playlist was parsed on the frame you were looking at.
- **The next-episode banner no longer flickers back on after starting the next episode.** Pressing
  next, or letting the countdown run out, replaced the episode straight away while the player was
  still reporting the previous one as finished — so for the moment in between, the banner slid
  back in offering the episode that had just started. It never got far enough to do anything; it
  looked wrong, and it happened between every pair of episodes of a series watched through.

## 0.13.0

### Added

- **Next and previous episode, on the television.** A series now travels with the player, so the
  buttons either side of the transport move along it — in the order the episodes were made,
  whichever order the list happens to be sorted in. They stop at the first and last episode
  rather than wrapping round; a series is a thing that finishes.
- **The next episode starts on its own.** When one ends, a banner slides in at the top right
  counting down, with Stop and Play now under it. The count is set under Settings → Playback →
  "Start the next episode after", from three seconds to fifteen, or Off — off still offers the
  next episode and waits for you to choose it. Back cancels it on the way out.
- **The television player has real controls now.** Play and pause sit in the middle of the screen
  with the two skips either side of them and the episode steps outside those, and subtitles,
  audio and picture fit are a row underneath. Press down for them, down again to reach the second
  row, and the D-pad walks between them. Subtitles and audio open the same panel at their own
  heading rather than at the top of it.
- Every button says what it is to TalkBack, which an icon on its own does not.

### Changed

- The remote's own keys still do everything they did with nothing on screen — play, skip, change
  channel — so the fast way to pause has not moved. What changed is that the arrows belong to the
  controls while the controls are up, which is what makes them reachable at all.
- The controls stay up for six seconds rather than four, counted from the last press rather than
  from when they opened. They are something to navigate now, not something to read.
- Picture fit no longer has to be reached through the Menu key or a spare press of Up. Those still
  work; there is a button for it now, which is what that pairing was always standing in for.

## 0.12.0

### Added

- **Subtitles are drawn.** They were not before: the player selected a text track and showed
  nothing, because nothing on screen was drawing the cues. Every subtitle track the app has ever
  offered was invisible.
- **Subtitle size, colour and background, set from inside the player.** In Audio and subtitles,
  while a subtitle is showing, so the effect is visible as it is chosen rather than guessed at
  from a settings screen. Starts from the caption style set in Android's own accessibility
  settings, and "Match system" goes back to it.
- **A channel's whole programme guide, on a timeline.** Long-press a channel — with a finger on
  the phone, by holding the centre button on the television — and the listing is laid out against
  the clock: an hour behind, half a day ahead, each programme as wide as it is long. What is on
  now is marked, and the times are the television's own, whatever zone the panel keeps.
- The phone opens the strip scrolled to now and writes out whichever programme you tap, since a
  half-hour block has room for a title and not for a plot. The television walks the strip with the
  D-pad and shows the same detail above it, with no dialog: the channel list stays behind the
  panel and Back closes it.
- The full listing is asked of the provider only when a viewer asks for it, and once per channel
  per session. Browsing a list still fetches nothing but now and next.

## 0.11.0

### Added

- **Subtitle files, from a panel or from the device.** A film whose panel supplies subtitle files
  now offers them in the player's subtitle list. Any film can also be given one from the device:
  pick a `.srt`, `.vtt`, `.ass` or `.ttml`, and it joins the list beside whatever the stream
  already carried. The choice is remembered against the title, so it is still there next time.
- **Subtitle files are read in the encoding they were written in.** An Arabic `.srt` in
  windows-1256 — which is most of them — used to be a screen of symbols, because the player
  assumed UTF-8. Files are now read in the encoding the bytes actually indicate, and stored
  readable.
- A picked file is copied into the app rather than referenced, so it survives the picker's
  permission expiring and the file being moved or renamed later.

## 0.10.0

### Added

- **Hide titles written in a script you do not read.** Settings offers ten writing systems —
  Arabic, Chinese, Cyrillic, Greek, Hebrew, Japanese, Korean, Latin, Thai, Devanagari — and hiding
  one removes those titles from browse and from search. Nothing is hidden until you hide it, and
  favourites and what you have half-watched are never filtered: those are titles you picked by
  hand.

## 0.9.0

### Added

- **Right-to-left titles are laid out right-to-left.** A film or a channel whose name is written in
  Arabic or Hebrew now reads from the correct edge, on both the phone and the television, without
  turning the rest of the screen around it.

## 0.8.0

### Added

- **Forget a title from Continue watching, on the television.** Long-press a tile and it goes.
- **Search suggests as you type**, from what has already been loaded, so it costs no extra request
  to the panel.

### Fixed

- The category list in Settings no longer grows past the screen on either app.

## 0.7.0

Nothing user-facing. Published by the release lane on a merge that changed only documentation.

## 0.6.0

### Added

- **Read a long-running series the way you want to.** Seasons can be merged into one continuous
  list, and the order reversed so the newest episode is first. Remembered per person, per series.
- **A refresh button on both detail screens**, on both apps, for a title whose artwork or plot
  came back wrong or not at all.

---

Versions before 0.6.0 predate this file. Their releases are on the
[releases page](https://github.com/quiblo-iptv/quiblo-app/releases).
