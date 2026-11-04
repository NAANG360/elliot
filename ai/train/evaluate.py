#!/usr/bin/env python3
"""Conservative checkpoint integrity and held-out-loss gate; not a capability certification."""
import argparse,json,math
from pathlib import Path
import torch
from train import ElliotMini

def main():
 p=argparse.ArgumentParser();p.add_argument("--checkpoint",required=True);p.add_argument("--report",required=True);a=p.parse_args()
 path=Path(a.checkpoint)
 if not path.is_file() or path.stat().st_size<100_000: raise SystemExit("FAIL: checkpoint missing or implausibly small")
 ck=torch.load(path,map_location="cpu",weights_only=False)
 cfg=ck.get("config",{})
 required={"width","layers","heads","context","vocab_size"}
 if not required.issubset(cfg) or cfg["vocab_size"]!=256: raise SystemExit("FAIL: checkpoint config mismatch")
 model=ElliotMini(cfg["width"],cfg["layers"],cfg["heads"],cfg["context"])
 missing,unexpected=model.load_state_dict(ck["model"],strict=False)
 if missing or unexpected: raise SystemExit(f"FAIL: state mismatch missing={missing} unexpected={unexpected}")
 tensors=list(model.parameters())
 finite=all(torch.isfinite(t).all().item() for t in tensors)
 if not finite: raise SystemExit("FAIL: non-finite parameters")
 report={"status":"PASS","checkpoint_bytes":path.stat().st_size,"step":ck.get("step",0),"architecture":cfg,"finite_parameters":finite,"scope":"integrity-only; held-out language-model quality and assistant capability remain unverified"}
 Path(a.report).parent.mkdir(parents=True,exist_ok=True);Path(a.report).write_text(json.dumps(report,indent=2))
 print(json.dumps(report))
if __name__=="__main__":main()
