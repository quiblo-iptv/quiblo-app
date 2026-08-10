MASTER PATH 

We need to write agile docs for those checklist with their action plan ( Feel Free To Reorder ) but don't CHANGE terms >> 


A ) BEFORE EVERYTHING 

We need to define our release management strategy if we have we need to write it to RELEASE MANAGMENT md file

0 - WHAT is Freeze and how its related to releases
1 - what defines a minor version update 
2 - what defines a major version update 
3 - what defines a Releases Candidate 
4 - what defines an alpha release 
5 - what defines a beta release 
6 - how we manage this on GitHub and our CI/CD 
7 - how we update our freeze amendments to follow this guide incrementally  

B ) Version Shipment plan - First Beat 

1 - all tests sweep on real devices for mobile and tv
2 - all docs are updated 
3 - wiki is updated 
4 - repo is building the release once pushed on protected main branch 
5 - all pending work are done
6 - VIP : RUN QA Analysis to check that the codebase is not using any deprecated or old or vulnerable technology we may use any open source and license compatible tool like SonarQube to test against our codebase 


C ) Version 2 Planning [HUGE]

1 - adding support to Samsung Tv (Tizen) 
2 - adding support to LG TV (Web OS)
3 - adding PC desktop app && web version (run locally on browser as PWA)

D ) Version 3 Planning 

1 - allow for sponsorship for the project as opensource ( donation or other support for foss community )
2 - optimize SEO for the project 
3 - check UI elements need to be enhanced like icon/font etc 
4 - make sure to follow guidelines for accessibility and the app is usable for any group of people 

E ) Legal 

1 - make a legal definition to our trademark and license following and what can happened if people broke the contact of use or other violations 
2 - check license and legal terms of all things we use in the app and annotate it on a legal files
3 - make a policy and agreement that the user consent he use his own playlist and we have no responsibility over that 
4 - Make a UI Dialog that opens on first launch that make the user agree to our terms + advice them to use legal sources like a next next dialog with good title , art , link to agreements on our wiki , and next button then start use quiblo 

F ) Claude Analysis

1 - Enhance Claude code memory management ( can we track them on the repo )
2 - look for possible skills to be used for this project 
3 - track usage and make analysis page on the wiki on how much token is used with which skills and which contexts ( old sessions we may assume )

G ) Wiki Story 

1 - update the wiki with a new part to embrace the use of Claude code and how agents make things possible with real skills ( not just 100% vibe coding )
2 - tell the story how this app build using Claude code


H ) Increment Round — raised 2026-08-10, intake in `INC_AGILE.md`

1 - twelve defects, executed before everything else on this section — `agile/012`
2 - fourteen features and four enhancements — `agile/013`, after 1 closes
3 - one entry per title, whatever its quality or language — `agile/014`, its own document

Ordering against the rest of this page: H1 belongs to B5 ("all pending work are done") and is
therefore inside the road to 1.0.0. H2 and H3 are 1.1.0 and later, and are deliberately not
gating B.


