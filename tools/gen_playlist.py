"""Generate a synthetic M3U for acceptance testing.

Entirely fabricated: every host is under .invalid (RFC 2606), which can never resolve.
No provider name, hostname or credential appears here (AC-LEGAL-04). The point is to
exercise list size and parser behaviour, not to play anything.
"""
import random
import sys

random.seed(1979)

GROUPS = [
    "News", "Sports", "Movies", "Kids", "Music", "Documentary",
    "Entertainment", "Lifestyle", "Science", "Regional",
]
ADJ = ["Blue", "Northern", "Crystal", "Prime", "Metro", "Global", "Vivid", "Coastal"]
NOUN = ["Channel", "Network", "TV", "Broadcast", "Vision", "Media"]


def main(count: int, path: str) -> None:
    lines = ["#EXTM3U"]
    for i in range(1, count + 1):
        group = GROUPS[i % len(GROUPS)]
        name = f"{random.choice(ADJ)} {random.choice(NOUN)} {i}"
        lines.append(
            f'#EXTINF:-1 tvg-id="ch{i}.invalid" tvg-name="{name}" '
            f'tvg-logo="http://logos.example.invalid/{i}.png" group-title="{group}",{name}'
        )
        lines.append(f"http://streams.example.invalid/live/{i}.m3u8")

    # A deliberate tail of the malformed cases AC-PL-04 names, so the sweep exercises
    # them at scale rather than only in unit tests.
    lines.append("#EXTINF:-1 group-title=\"News\",Unescaped, comma in the name")
    lines.append("http://streams.example.invalid/live/comma.m3u8")
    lines.append("#EXTINF:-1 group-title=\"News\",Missing URL")
    lines.append("#EXTINF:-1,No group at all")
    lines.append("http://streams.example.invalid/live/nogroup.m3u8")
    lines.append("this line is not a directive and not a url")
    lines.append("#EXTINF:-1 group-title=\"News\",Truncated final entry")

    body = "\r\n".join(lines)          # CRLF throughout (AC-PL-04)
    with open(path, "wb") as handle:
        handle.write(b"\xef\xbb\xbf")  # UTF-8 BOM (AC-PL-04)
        handle.write(body.encode("utf-8"))
        # No trailing newline: the final line is deliberately truncated.

    print(f"wrote {path}: {count} valid entries + 5 malformed tail cases")


if __name__ == "__main__":
    main(int(sys.argv[1]), sys.argv[2])
