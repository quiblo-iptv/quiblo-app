# FEAT-031 Manual Sweep

WorkManager's real scheduling, a real panel's tolerance, and an upgrade over an existing install
are three things no unit test reaches.

## 1. An existing install moves off four days

1. Install a build from before this change and open it once.
2. `adb shell dumpsys jobscheduler | grep -i quiblo` — note the four-day job.
3. Install this build over it and open it.
4. **Expect:** the old job is gone and a four-hourly one is in its place.
5. **Fail if:** both are listed, or the four-day one survives.

## 2. Changing the setting takes effect now

1. Settings → Check for new content → Daily.
2. Check the scheduler again.
3. **Expect:** the job's period is 24 hours without reopening the app.

## 3. Opening the app does not restart the interval

1. Note the job's next-run time.
2. Leave the app and open it three times.
3. **Expect:** the next-run time has not moved.
4. **Fail if:** it resets on each launch — that is the bug `KEEP` exists to prevent, and it makes
   the sync never run for the households that use the app most.

## 4. An unchanged account is cheap

1. With a proxy or the panel's own access log, watch one scheduled run against an account nothing
   has been added to.
2. **Expect:** four requests — auth, live, films, series.
3. **Fail if:** the three category calls are made anyway.

## 5. New content actually appears

1. Have the provider add a title, or use an account that gets them daily.
2. **Expect:** it is in Recently Added within one interval, without a manual refresh.

## 6. The panel does not start refusing

Left running for a few days at four hours on a real account: no block, no "provider refused"
message. This is the check that decides whether four hours is the right default.
