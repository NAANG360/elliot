#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,shutil
from pathlib import Path

def make_tokens(config,tokens):
    cfg=json.loads(config.read_text(encoding="utf-8"))
    pairs=[]
    for symbol,ids in (cfg.get("phoneme_id_map") or {}).items():
        for idx in ids: pairs.append((int(idx),symbol))
    pairs.sort()
    tokens.write_text("\n".join(f"{i}\t{s}" for i,s in pairs)+"\n",encoding="utf-8")

def bundle(a):
    onnx=Path(a.onnx).resolve(); config=Path(a.config).resolve(); dataset=Path(a.dataset).resolve(); out=Path(a.output).resolve()
    if not onnx.is_file() or not config.is_file(): raise SystemExit("ONNX model and matching config are required")
    espeak=dataset/"espeak-ng-data"
    if not espeak.is_dir(): raise SystemExit("dataset/espeak-ng-data is missing; copy the eSpeak data used by training into the dataset")
    out.mkdir(parents=True,exist_ok=True)
    shutil.copy2(onnx,out/onnx.name)
    shutil.copy2(config,out/(onnx.name+".json"))
    make_tokens(config,out/"tokens.txt")
    shutil.copytree(espeak,out/"espeak-ng-data",dirs_exist_ok=True)
    print("Android voice bundle ready:",out)

ap=argparse.ArgumentParser()
sub=ap.add_subparsers(dest="command",required=True)
b=sub.add_parser("make-android-bundle")
b.add_argument("--onnx",required=True); b.add_argument("--config",required=True); b.add_argument("--dataset",required=True); b.add_argument("--output",required=True)
a=ap.parse_args()
if a.command=="make-android-bundle": bundle(a)
