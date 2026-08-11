"""Read local Claude Code transcripts and emit token aggregates — numbers only.

`agile/010` §F3. The transcripts are a record of how this project was built, and they are also
full of exactly what `AC-LEGAL-04` forbids anywhere near this repository: real panel hostnames,
real responses, and the debugging that got an account blocked. So the safety here is structural
rather than careful.

**This script never reads a text field.** It looks at three keys per line — `timestamp`,
`message.model` and `message.usage` — and nothing else. Not the prompt, not the response, not a
tool call, not a file path, not the working directory. There is no filtering step that could be
written too loosely, because no content is ever loaded to be filtered.

**And it checks its own output before writing.** Every string that reaches the file has to match
a date, a session id or a model name. Anything else and it refuses to write at all, which is the
mechanical version of "reviewed by a person before it is committed".

The transcripts themselves are never copied, never summarised and never committed. This reads
them where they live.

Usage:
    python tools/usage_aggregates.py [output.json]
"""
import json
import pathlib
import re
import sys
from collections import defaultdict

# Where Claude Code keeps transcripts on this machine. The project has moved and been renamed,
# so its history lives in three directories — the first one predates the rename to Quiblo, and
# leaving it out would start the record in the middle.
TRANSCRIPT_ROOT = pathlib.Path.home() / ".claude" / "projects"
PROJECT_DIRS = (
    "C--Users-Maxmya-DEV-vibrato-tv",
    "C--Users-Maxmya-DEV-quiblo",
    "C--Users-Maxmya-DEV-quiblo-quiblo-iptv",
)

# What a string in the output is allowed to look like. Everything else is refused.
SAFE_PATTERNS = (
    re.compile(r"^\d{4}-\d{2}-\d{2}$"),                       # a date
    re.compile(r"^[0-9a-f]{8}$"),                             # a shortened session id
    re.compile(r"^claude-[a-z0-9.\-]+$"),                     # a model id
    re.compile(r"^[a-z_]+$"),                                 # our own keys and labels
    re.compile(r"^\d+$"),                                     # a count rendered as a string
)


def read_session(path: pathlib.Path) -> dict | None:
    """Totals for one transcript. Only usage, model and timestamp are ever touched."""
    totals = {
        "messages": 0,
        "input_tokens": 0,
        "output_tokens": 0,
        "cache_read_tokens": 0,
        "cache_creation_tokens": 0,
    }
    models: set[str] = set()
    days: set[str] = set()

    with path.open(encoding="utf-8", errors="replace") as handle:
        for line in handle:
            try:
                record = json.loads(line)
            except (ValueError, TypeError):
                # A truncated final line is normal in a session that is still open.
                continue

            message = record.get("message")
            if not isinstance(message, dict):
                continue
            usage = message.get("usage")
            if not isinstance(usage, dict):
                continue

            totals["messages"] += 1
            for key, field in (
                ("input_tokens", "input_tokens"),
                ("output_tokens", "output_tokens"),
                ("cache_read_tokens", "cache_read_input_tokens"),
                ("cache_creation_tokens", "cache_creation_input_tokens"),
            ):
                value = usage.get(field)
                if isinstance(value, int):
                    totals[key] += value

            model = message.get("model")
            if isinstance(model, str) and model.startswith("claude-"):
                models.add(model)

            stamp = record.get("timestamp")
            if isinstance(stamp, str) and len(stamp) >= 10:
                days.add(stamp[:10])

    if totals["messages"] == 0:
        return None

    return {
        "session": path.stem[:8],
        "first_day": min(days) if days else None,
        "last_day": max(days) if days else None,
        "models": sorted(models),
        **totals,
    }


def check_no_content(node: object, where: str = "root") -> None:
    """Refuse to write anything that is not a number, a date, a session id or a model name."""
    if isinstance(node, dict):
        for key, value in node.items():
            check_no_content(key, where)
            check_no_content(value, f"{where}.{key}")
    elif isinstance(node, list):
        for item in node:
            check_no_content(item, where)
    elif isinstance(node, str):
        if not any(pattern.match(node) for pattern in SAFE_PATTERNS):
            raise SystemExit(
                f"Refusing to write: unexpected string at {where}. Only dates, session ids and "
                f"model names may leave this script (AC-LEGAL-04)."
            )
    elif node is not None and not isinstance(node, (int, float, bool)):
        raise SystemExit(f"Refusing to write: unexpected value type at {where}.")


def main(destination: str) -> None:
    sessions = []
    for directory in PROJECT_DIRS:
        folder = TRANSCRIPT_ROOT / directory
        if not folder.is_dir():
            print(f"skipped (not found): {directory}", file=sys.stderr)
            continue
        for transcript in sorted(folder.glob("*.jsonl")):
            session = read_session(transcript)
            if session:
                sessions.append(session)

    if not sessions:
        raise SystemExit("No transcripts found. Nothing written.")

    sessions.sort(key=lambda s: (s["first_day"] or "", s["session"]))

    totals = defaultdict(int)
    for session in sessions:
        for key in ("messages", "input_tokens", "output_tokens",
                    "cache_read_tokens", "cache_creation_tokens"):
            totals[key] += session[key]

    # The number nobody expects, and the one that explains the cost: almost all of the input a
    # long session pays for is cache reads rather than fresh tokens.
    read_and_fresh = totals["cache_read_tokens"] + totals["input_tokens"]
    # Three decimals, not one. At this ratio a rounded figure reads as 100% and invites the
    # reader to assume a rounding error rather than the actual result, which is that fresh input
    # is a rounding error.
    cache_share = round(100 * totals["cache_read_tokens"] / read_and_fresh, 3) if read_and_fresh else 0.0

    by_day: dict[str, int] = defaultdict(int)
    for session in sessions:
        if session["first_day"]:
            by_day[session["first_day"]] += session["output_tokens"]

    payload = {
        "schema_version": 1,
        "sessions": len(sessions),
        "first_day": sessions[0]["first_day"],
        "last_day": max(s["last_day"] or "" for s in sessions),
        "totals": dict(totals),
        "cache_read_share_percent": cache_share,
        "output_tokens_by_first_day": dict(sorted(by_day.items())),
        "by_session": sessions,
    }

    check_no_content(payload)
    pathlib.Path(destination).write_text(
        json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8"
    )
    print(f"{len(sessions)} sessions, {totals['output_tokens']:,} output tokens -> {destination}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/usage-aggregates.json")
