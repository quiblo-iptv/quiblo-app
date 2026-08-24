**Whose Settings Are These of Quiblo**

Seven items reported from the sofa against the shipped `0.25.0`, by living with the app for an
evening rather than by reading it. Six are faults against behaviour the app already claims; one
is new capability and needs a freeze amendment.

**Created:** 2026-08-24, against commit `493eda9` on `fix/merge-duplicates-perf-and-ambient`.
**Ships as:** mixed defect and feature work. **Two freeze amendments are required** — see
`FREEZE.md` Amendments 12 and 13.

---

**Numbered within the round**, as `022`, `025` and `027` number theirs: the code says ``029`` #2
where it is fixing the second of these, because a defect is a thing a round dealt with rather than
an entry in a register nobody keeps.

| # | Platform | Criterion | Description |
| :---- | :---- | :---- | :---- |
| #1 | TV | AC-TV-11, AC-TV-12 | A film or series screen is black. The ambient light every other television screen has never arrives on the two that have the best artwork to take it from |
| #2 | TV | AC-TV-14 | The favourite control on the hero slider is red — filled icon, red tint, red ground, red border — which on a television reads as an error |
| #3 | Both | — | *Merge duplicate titles* folds four listings into one and leaves them reachable from four shelves, so the catalogue still reads as four catalogues |
| #4 | TV | AC-TV-14 | Search result headings are on screen on a phone and on the emulator and missing on the television |
| #5 | Both | — | There is no way to remove a tab. An account with no live channels shows a Live tab that opens on nothing |
| #6 | Both | **AC-PROF-03** | Hiding a category does nothing to the catalogue, and every setting is shared by everybody on the device |
| #7 | Both | — | Nothing tells a viewer their build is eight months old |

## What connects them

**Four of the seven are the app being *lived with* rather than demonstrated.** #1 needs somebody
to open a film from a lit catalogue; #4 needs a real panel rather than the geometry the tests use;
#5 and #6 need a second person in the house. None is reachable by opening a screen and looking at
it, which is why the sweep has caught none of them.

**#6 is two faults that read as one complaint.** The category hiding was never wired to the
browse queries at all, *and* every preference in the app was app-wide while favourites were not.
A viewer meeting both at once reports "profile hides do not work", which is true twice over and
for two unrelated reasons.

**#3 and #6 are the same shape from opposite ends**: one setting that describes what the
catalogue *is*, and one that was described that way and should not have been.

---

## #1 — the detail screens are black

**Mechanism.** `TvApp` owns a stack of overlays and the shell is only composed when that stack is
empty — `if (current == null)`. The ambient layer and its `LocalAmbientSink` were inside `TvShell`,
so the moment anything opened over it both went out of composition with it. `TvMovieScreen` and
`TvSeriesScreen` have reported their artwork through that sink since the day they were written;
what they were reporting it to was the composition local's own do-nothing default, and there was
no backdrop left drawing anything.

The screens were correct. The wiring was one level too low.

**Fix.** The `Box` that paints the black, the backdrop and the drifting glow, and the
`CompositionLocalProvider` that carries the sink, move up to `TvApp` and wrap **both** the shell
and the overlay. What is left of the navigation decision is `TvContent`, split out so that the
routing does not live inside those two — which is how the shell came to own the light in the first
place.

The phone had the same seam and fixed it a commit earlier (`493eda9`); this is the television half
of the same lesson: **the screen knows the artwork, only the shell knows the window.**

## #2 — a red heart on the hero slider

**Mechanism.** `HeroFavoriteButton` painted four things red when a title was favourited: the
icon's tint, the box behind it, the border round it, and a second red at a different alpha when it
also had focus. Red on a television is an error or a recording light. On the largest and most
glanced-at element in the app it reads as neither of the things it meant.

**Fix.** The box, the border and the tint follow **focus** only, exactly as the Play button beside
it does, so the two read as one row of controls rather than as one control and one alarm. Whether
a title is favourited is said once, unambiguously, by whether the heart is filled or hollow —
which is what the report asked for and what the icon was already doing correctly underneath four
layers of red.

## #3 — merging titles, and still four shelves

**Mechanism.** Not a defect: a gap. `014` merges the *listings* of a title, and a provider that
carries a film in four qualities usually files those four under four categories — `FILMS HD`,
`FILMS 4K`, `FILMS AR`. Merging the rows leaves one row reachable from several shelves, so the
shelf count drops and the number of shelves does not.

**Fix.** A second switch, `mergeCategories`, offered only while merging duplicates is on. The
repository reports it as `false` whenever the first is off rather than leaving each screen to
remember, because collapsing shelves without merging copies gives one grid in which every film
appears four times in a row — the worst of both settings and exactly what a screen reading the raw
switch would draw.

**Two screens rather than one that draws both**, and the reason is the query behind them. Shelves
read the first tiles of every category, capped in SQL (`BrowseScope.CATEGORY_ROWS`); a single grid
has no categories to cap by and has to be paged (`BrowseScope.CATALOGUE`). Those are chosen when
the ViewModel is built, so `TvPosterRows` dispatches to `TvCategoryShelves` or to the new
`TvMergedGrid` and neither carries a branch about the other. The phone was already a flat grid
with a category filter over it, so there the setting removes the filter chip.

## #4 — the headings are on a phone and not on the television

**Mechanism.** `TvSearchResultsFitTest` composes the real header at `960×540dp` and asserts that a
focused result keeps its title and that its row's heading stays on screen. It passes. The panel
the report came from is not that panel.

Search stacks a field, an *Advanced* chip and a strip of genre chips above its results and hands
the poster row what is left. The tile is a fixed 150dp of artwork — 279dp of column once the
growth a focused tile needs and its own title line are counted — plus 46dp of heading above it.
Where the remainder is less than that sum, the row is taller than its viewport, the list scrolls
the focused tile into view, and the heading goes off the top. A viewer then cannot tell whether
they are looking at films or at series, which is the whole job the heading has.

**Fix.** `posterWidthFor(available)` — take the height the row is actually given, subtract every
constant in the row that is not artwork, and the rest is the poster, clamped to 100–150dp.
`TvSearchScreen` measures with `BoxWithConstraints` and passes it down through `TvCategoryList` to
each tile. The catalogue screens take the default and are unchanged.

The subtraction is a sum of the file's own constants rather than an estimate, so retuning any of
them moves it. **A fixed size cannot be right on a panel it has never seen**, which is what
"responsive for all devices" means here.

## #5 — a tab you never use, every evening

**Mechanism.** Not a defect: absent capability. An M3U with no VOD shows a Movies tab that opens on
nothing; a household that never watches series still walks past Series to reach Films.

**Fix.** `AppTab` in `:core:model` — `LIVE`, `MOVIES`, `SERIES`, `FAVOURITES` — with a per-profile
set of hidden ones. **Four, not every tab, and the omissions are the design.** Search, Sources and
the television's Home have no entry: two of them are the only way to reach something that is not
on a shelf, and hiding the third leaves a shell that opens on a tab it has been told not to draw.
Having no entry makes hiding them unrepresentable rather than merely avoided.

Each shell maps its own bar onto it, because the two do not agree on what a tab is. Three details
that are decisions:

- **The bar's cursor is a position in the visible list, not a `TvTab` ordinal.** Those are the same
  number only while nothing is hidden, and conflating them moves the selection onto a tab the
  viewer cannot see.
- **The last visible tab cannot be hidden.** The repository refuses it; the switch springs back.
  A clamp rather than a disabled control, because which one is last changes as the others are
  switched.
- **Hiding the tab you are standing on moves you off it**, on both apps. It is a press away on the
  settings screen and is the one way to be left looking at a screen with no way back to it.

## #6 — the hides that did nothing, and the settings that belonged to nobody

Two mechanisms, reported as one complaint.

**Mechanism (a): hiding a category never reached the catalogue.** `CategoryRepository` filters
hidden categories out of the *category list*, and `SearchRepository` passes `includeHidden` to the
search query. The browse queries never looked at the table at all — `observeBrowse`, `pagedBrowse`,
`observeCategoryRows`, `observeRecentlyAdded` and `observeLastInListOrder` have no such predicate,
and `CategoryOverrideDao.observeHiddenTitles` had **no caller anywhere in the repository**. So
every title in a hidden shelf was still in the phone's grid, still in the television's rows, and
still in Recently Added. The setting appeared to do nothing on the screen a viewer hides a
category *for*.

**Fix (a).** The predicate is added to all five. Favourites pass `includeHiddenCategories = 1` on
the rule the script filter already follows: a list somebody built by hand is not the catalogue,
and dropping something they picked is not a filter but a loss.

**Mechanism (b): every preference was app-wide.** Amendment 6 gave a profile favourites and
resume points "and nothing else", and each setting in `PlayerSettingsStore` carried a comment
arguing why it was app-wide. **The argument was wrong in the same way each time**: it reasoned
from the catalogue being one catalogue, when a theme, a seek interval, a set of hidden writing
systems and a list of shelves are statements about what one *viewer* wants to look at. A household
had two people with their own favourites and one shared everything else, with nothing on screen
saying which was which.

**Fix (b).** Every key in `PlayerSettingsStore` becomes `name@profileId`, with the unscoped key as
the read fallback. That is the migration, and it is deliberately not a one-off copy: an install
configured once keeps every setting for every profile until somebody changes one, at which point
only that profile moves. Nothing is lost, nobody is asked to set the app up again, and there is no
moment where a half-run migration has written some keys and not others. `ScriptFilterRepository`
and `ChannelLogoStore` get the same treatment.

`PlayerSettingsRepository` adds the profile: every read is `flatMapLatest` over the active profile
rather than a one-off id, so switching person redraws the theme, the shelves and the tab bar the
way it already redrew favourites.

**And the screen says which is which.** Settings splits into **Profile** and **App** on both apps.
Profile holds what a person chooses; App holds the television itself — sources, the metadata key,
the backup file, updates, licences. The ambiguity the report names is answered by the layout, not
by a sentence somebody has to find.

`checkUpdatesOnLaunch` is the single exception and stays app-wide, because it decides whether the
*device* makes a request. See #7.

## #7 — nothing says the build is old

**Mechanism.** Not a defect: absent capability, and the reason `026` gave for its absence no longer
holds on its own. Quiblo is installed from an APK and has no store behind it. `026` added a *Check
for updates* button to the television's Settings and wrote down, as a constraint, that nothing
would ever be checked unprompted. A button nobody presses is a fix nobody installs.

**Fix.** One check when the app opens, and four rules that keep §4.5 honest:

1. **It is a setting.** On by default, and off means no request is made at all rather than the
   answer being hidden.
2. **It asks this project's own releases page and nothing else.** No analytics, no identifier, no
   payload — the same public JSON document the button fetches.
3. **Once per process**, so a profile switch or backing out to the shell does not ask again.
4. **Silence is silence.** Every outcome but "there is a newer one" leaves the app exactly as it
   was. Up to date, offline and unreachable are answers for the row in Settings, where somebody
   asked.

The television draws its own panel rather than a dialog — this app has no `AlertDialog` anywhere
and the way in is a poor place to grow the first one — and downloads through the verified-checksum
path `026` already built, sharing one state machine with the settings row. The phone opens the
releases page instead: it holds no `REQUEST_INSTALL_PACKAGES` and, under AC-NFR-04, must not.

**The consent copy is amended rather than left standing.** `tv_consent_terms_body` promised that
nothing leaves the device except to the servers a viewer names. That is now false by one request,
so the screen says so and names the switch.

---

## Verification

- `./gradlew testDebugUnitTest` — green across every module.
- `./gradlew detekt` — clean.
- `./gradlew :app:lintDebug :app-tv:lintDebug` — clean.

## What this round does not do

- **No parental controls.** #5 hides tabs and #6 hides shelves; both are per profile and neither
  locks anything. A control that *appears* to restrict is worse than no control, and
  `PLAN.md` §6 keeps that parked.
- **No per-profile playlists.** Sources, credentials and the metadata key stay on the device. A
  household that had to type its Xtream password once per person would rightly call that a bug.
- **No "skip this version".** A viewer who dismisses an update is saying not now, and a switch
  recording which release they said no to is a second setting nobody can find again to undo.
