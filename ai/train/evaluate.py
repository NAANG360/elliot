#!/usr/bin/env python3
"""Checkpoint gate: minimum training progress, finite weights and held-out loss."""
import argparse,json,math
from pathlib import Path
import torch
from train import ElliotMini

def main():
 p=argparse.ArgumentParser()
 p.add_argument("--checkpoint",required=True); p.add_argument("--report",required=True)
 p.add_argument("--min-steps",type=int,default=1000); p.add_argument("--max-val-loss",type=float,default=5.5)
 a=p.parse_args(); path=Path(a.checkpoint)
 if not path.is_file() or path.stat().st_size<100_000: raise SystemExit("FAIL: checkpoint missing or implausibly small")
 ck=torch.load(path,map_location="cpu",weights_only=False); cfg=ck.get("config",{})
 if not {"width","layers","heads","context","vocab_size"}.issubset(cfg) or cfg["vocab_size"]!=256:
  raise SystemExit("FAIL: checkpoint config mismatch")
 model=ElliotMini(cfg["width"],cfg["layers"],cfg["heads"],cfg["context"])
 missing,unexpected=model.load_state_dict(ck["model"],strict=False)
 if missing or unexpected: raise SystemExit(f"FAIL: state mismatch missing={missing} unexpected={unexpected}")
 finite=all(torch.isfinite(t).all().item() for t in model.parameters())
 step=int(ck.get("step",0)); loss=float(ck.get("best_validation_loss",float("inf")))
 if not finite: raise SystemExit("FAIL: non-finite parameters")
 if step<a.min_steps: raise SystemExit(f"FAIL: only {step} steps; require {a.min_steps}")
 if not math.isfinite(loss) or loss>a.max_val_loss: raise SystemExit(f"FAIL: held-out loss {loss} exceeds {a.max_val_loss}")
 report={"status":"PASS","checkpoint_bytes":path.stat().st_size,"step":step,"best_validation_loss":loss,
  "architecture":cfg,"finite_parameters":finite,
  "scope":"foundation-stage gate only; not an assistant capability certification or release approval"}
 Path(a.report).parent.mkdir(parents=True,exist_ok=True); Path(a.report).write_text(json.dumps(report,indent=2))
 print(json.dumps(report))
if __name__=="__main__": main()
