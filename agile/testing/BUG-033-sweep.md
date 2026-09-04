# BUG-033 Manual Sweep

A unit test cannot reach the thing that was actually reported: a real panel, rate-limiting a real
household, four days after anybody last looked. These are the checks a build cannot make.

## 1. The stored catalogue survives a category failure

1. Add an Xtream source and let it load. Note two or three category names on Live.
2. Point the app at a panel whose category endpoints answer 502 while its stream endpoints answer
   normally, or block those three URLs at the router.
3. Pull down to refresh, or wait for the scheduled sync.
4. **Expect:** the refresh reports a failure, and every category name from step 1 is still there.
5. **Fail if:** any category is named `__ungrouped__`, or the category list is one row.

## 2. A live-only account still loads

1. Use an account that carries live channels and no films or series.
2. Refresh.
3. **Expect:** the catalogue loads, grouped, with no failure reported.
4. **Fail if:** the load fails because the film category endpoint answered unhelpfully.

## 3. Hidden categories still match

1. Hide two categories in Settings.
2. Force a category failure as in check 1, then restore the endpoints and refresh.
3. **Expect:** the two categories are still hidden, by name.
4. **Fail if:** hidden categories reappear, which is what a rewritten `groupTitle` causes.

## 4. The recovery path still works

1. From a database already damaged by an older build, refresh by hand from Settings.
2. **Expect:** the grouping is restored.

This is how the fault was originally worked around, and it must keep working for anyone upgrading
with a catalogue already in the damaged state.
