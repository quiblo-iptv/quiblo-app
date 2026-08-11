new for agile docs 
increment or include in current files 

FEATURES 

0 - add avatar for user creation , add avatar on the right of settings button , when it's clicked allow user to change how's watching.
1 - add autocomplete to search from current movies/series/tv in the cached database 
2 - add a learning algorithm/daemon machine learning ?! (teach me about that) that knows what the user likes and suggest upon his watching other similar works.
3 - allow on tv and android to hold press on history item >> them popup a list of just ( remove from watch history ) to remove the held item.
4 - allow to hold a channel in channels list and see (or request idk) it's EPG for programs guide and catalog with time line aligned with tv time.
5 - on README add section of get it on GitHub ( that downloads the apk from GitHub or redirect to release page ) 
6 - on series details screen on both apps allow user to do [ merge seasons so it's 1 list , switch order from asc to desc and vise versa , not in options , in same details page and it should be saved in profiles cache that for example I merged one piece and ordered it from last episode so I don't scroll 1000+ episode every time i watch it ]
7 - on series & movies sometimes artwork not shown or details not updated , add a new button to update it from TMBD ( only show this if the key is inserted) 
8 - enhance category edit option on settings to be in a scrollable container inside the settings screen.
9 - check title, text, description and others if contains any RTL language make the section RTL otherwise ( all LTR ) leave it LTR even if the app itself is LTR. 
10 - add close caption support for usual supported subtitled files ( check if provider gave it or choose from file )
11 - add subtitles options can be configured from the player itself by popup for text size color bg color etc.
12 - add multi audio support for single file check the file codex itself sometimes it has it (you know it right?). 
13 - (BIG TO HUGE , need it's own agile document) - Allow in options to merge all movies with same name(may differ in case and indentation) but different quality or different language and make only 1 entry to it and inside the player allow the user to choose between them.
14 - (this is for me as a user) allow on options to specify the main language of the shows to show and filter from so if i picked EN , don't show any Arabic show( that has Arabic only title "not mixed") and if i chose AR don't show any English show [ is this option possible while we don't know exactly what is the Arabic/English content ?]


BUG_FIX

1 - on tv on continue watching row , the first icon is always clipped by the border of it's container.
2 - on tv when switching between shows , last buffer still shows on the player until new content loads. 
3 - on tv add to favorites button text is trimmed and not shown completely and no remove from watch history button.
4 - on tv on sears or movies details screen (specially series) the art poster is trimmed from top and title of the show is not shown , because the focus on the first button steal the scroll to down so it stays cropped from beginning. [ we may make only the list scrollable but with reasonable percentage of the screen ].
5 - app don't ask me who's watching every time I open it , it should by default ask who's currently watching and allow pick the user create user or chose guest. ( use circles for avatar not squares , and center this view )
6 - In favorites movies are named VOD ! so We need to correct it and in both series and movies use normal case like "Movies" not all caps. 
7 - advanced search is so slow to load the tags (genres) so make it a progress bar under the search bar and when they appear remove the progress bar.
8 - In advanced Search screen title Series is not shown , series name is not shown (half cropped when hovered/focused) movies named FILMS ! why not same as all the app !? , also it's not shown while in the movies row.
9 - when I am on a movie or series and I press back i don't get me on details screen it get me to movies or series home screen, we need to get back to the same show details screen and if its a series we should keep the cursor on the position of last selected episode.
10 - on TV on settings when API Key text input is opened for entry the screen shakes crazy (focus race).
11 - on tv on settings api key text input is not aligned with other options on the right column.

ENHANCMENTS 

1 - but the advanced button of search on the right of the search bar and make them combined take the practical amount of space for search bar 
2 - put Quiblo logo in top of search bar , then Quiblo in text , then search area. 
3 - make the search bar glowing and make the glow travel throw the edge of the search bar. 
4 - is our design code to use rounded corners or cubic rounded corners !? can we increase the roundness.

---

## Where each item went (triage, 2026-08-10)

This page stays as the raw intake. Everything on it is now planned in the agile documents
below, and **the bug fixes are executed before any feature on this page.**

| Intake | ID | Document |
| :---- | :---- | :---- |
| BUG_FIX 1 | #012 | [`012` Bug Round — Round 3](../agile/012_Bug_Round_of_Quiblo_—_Round_3.md) |
| BUG_FIX 2 | #013 | `012` |
| BUG_FIX 3 | #014 | `012` |
| BUG_FIX 4 | #015 | `012` — mechanism confirmed, `TvMovieScreen.kt:110` |
| BUG_FIX 5 | #016 | `012` — decided: the app asks at every launch |
| BUG_FIX 6 | #017 | `012` — mechanism confirmed, `TvPosterRows.kt:257` |
| BUG_FIX 7 | #018 | `012` — mechanism confirmed, `SearchRepository.genreIndex()` |
| BUG_FIX 8 | #019 | `012` — "Films" confirmed at `strings.xml:67` |
| BUG_FIX 9 | #020 | `012` — **blocked on an acceptance decision**, AC-TV-03 says the opposite |
| BUG_FIX 10 | #021 | `012` |
| BUG_FIX 11 | #022 | `012` |
| FEATURE 12 | **#023** | `012` — **reclassified as a defect**: AC-PLAY-04 already requires it and the engine already does it |
| FEATURE 0, 1, 3…11, 14 | INC-F0…F14 | [`013` Increment Round](../agile/013_Increment_Round_of_Quiblo_—_the_catalogue_a_viewer_actually_uses.md) |
| FEATURE 2 | INC-F2 | `013` — answered there: content-based scoring, on device, not a model and not a daemon |
| FEATURE 13 | — | [`014` One Entry Per Title](../agile/014_One_Entry_Per_Title_of_Quiblo_—_duplicates,_qualities_and_languages.md) — its own document, as asked |
| FEATURE 14 | INC-F14 | `013` — answered there: possible, best-effort, and designed as hiding rather than selecting |
| ENHANCMENTS 1–4 | INC-E1…E4 | `013` |

Two questions on this page are answered in prose rather than planned as work: what a
recommendation engine can be without a backend (`013`, INC-F2), and whether a language filter
is possible when nobody knows what language anything is in (`013`, INC-F14).

  