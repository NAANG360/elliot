#!/usr/bin/env python3
"""Validate Miss Minutes JSONL training records without external dependencies."""

import json
import sys
from pathlib import Path

EMOTIONS={"warm","curious","amused","concerned","excited","neutral"}
GESTURES={"idle","wave","point","nod","think","reset","celebrate"}
PACES={"calm","normal","bright"}

def fail(line, message):
    print(f"line {line}: {message}", file=sys.stderr)
    return 1

def main(path):
    errors=0
    records=0
    for line_no, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        try:
            item=json.loads(raw)
        except json.JSONDecodeError as exc:
            errors += fail(line_no, f"invalid JSON: {exc}")
            continue
        messages=item.get("messages")
        if not isinstance(messages,list) or not messages:
            errors += fail(line_no,"messages must be a non-empty list")
            continue
        roles=[m.get("role") for m in messages if isinstance(m,dict)]
        if "user" not in roles or "assistant" not in roles:
            errors += fail(line_no,"record needs user and assistant messages")
        assistant=next((m for m in reversed(messages) if isinstance(m,dict) and m.get("role")=="assistant"),None)
        if not assistant or not isinstance(assistant.get("content"),str):
            errors += fail(line_no,"assistant content must be text")
            continue
        try:
            out=json.loads(assistant["content"])
        except json.JSONDecodeError:
            errors += fail(line_no,"assistant content must be a JSON object")
            continue
        if not isinstance(out,dict) or not isinstance(out.get("text"),str):
            errors += fail(line_no,"assistant JSON needs a text string")
        if out.get("emotion") not in EMOTIONS:
            errors += fail(line_no,f"emotion must be one of {sorted(EMOTIONS)}")
        if out.get("gesture") not in GESTURES:
            errors += fail(line_no,f"gesture must be one of {sorted(GESTURES)}")
        if out.get("pace") not in PACES:
            errors += fail(line_no,f"pace must be one of {sorted(PACES)}")
        records += 1
    print(f"validated {records} records; errors={errors}")
    return 1 if errors else 0

if __name__=="__main__":
    raise SystemExit(main(sys.argv[1] if len(sys.argv)>1 else "ai/dataset/seed.jsonl"))
