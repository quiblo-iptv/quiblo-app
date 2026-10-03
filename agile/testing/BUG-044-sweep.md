# BUG-044 Manual Sweep

**Verify on real streams.** Make a small M3U by hand with entries of these shapes, pointing at
streams known to work in mpv:

```
#EXTM3U
#EXTINF:-1,Query playlist
http://HOST/play.php?file=index.m3u8
#EXTINF:-1,Output parameter
http://HOST/stream?id=1&output=m3u8
#EXTINF:-1,No extension
http://HOST/hls/1
#EXTINF:-1,A real file
http://HOST/film.mp4
```

1. Play each. **Expect:** the first two start as quickly as any `.m3u8` channel; the third starts
   after a short extra pause (one failed attempt, then HLS); the file plays as before.
2. Point one entry at something that is neither (a text file). **Expect:** an error within 15 s.
3. **Fail if** any of the first three says "a format Quiblo cannot play".
