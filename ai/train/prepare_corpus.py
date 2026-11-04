#!/usr/bin/env python3
"""Fetch a bounded, openly licensed educational corpus for Elliot's first training phase."""
import argparse
from pathlib import Path
from datasets import load_dataset

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--output",required=True)
    p.add_argument("--max-mb",type=int,default=64)
    a=p.parse_args()
    out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True)
    limit=a.max_mb*1024*1024
    # FineWeb-Edu is an educationally filtered public-web corpus; dataset card/license
    # and source provenance must be reviewed before promoting a checkpoint.
    stream=load_dataset("HuggingFaceFW/fineweb-edu",name="sample-10BT",split="train",streaming=True)
    written=0; docs=0
    mode="a" if out.exists() and out.stat().st_size<limit else "w"
    with out.open(mode,encoding="utf-8") as f:
        if mode=="a": written=out.stat().st_size
        for row in stream:
            text=row.get("text","").strip()
            if len(text)<300: continue
            block="\n\n"+text+"\n"
            size=len(block.encode("utf-8"))
            if written+size>limit: break
            f.write(block); written+=size; docs+=1
    if written<1024*1024: raise SystemExit("Corpus too small; refusing to train on an inadequate sample.")
    print(f"Corpus ready: {written} bytes; {docs} new documents; source=HuggingFaceFW/fineweb-edu sample-10BT")
if __name__=="__main__": main()
