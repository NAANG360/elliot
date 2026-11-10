#!/usr/bin/env python3
"""Continue Elliot's byte-level foundation model from an existing checkpoint on a Kaggle GPU.

This is deliberately separate from the Qwen2.5 QLoRA path. It loads the exact
ElliotMini architecture from the checkpoint config and preserves AdamW state.
"""
import argparse
import hashlib
import json
import math
import os
import random
import shutil
import sys
import time
from pathlib import Path

import torch

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT_CANDIDATES = (
    SCRIPT_DIR.parent,
    SCRIPT_DIR,
    SCRIPT_DIR / "elliot",
    SCRIPT_DIR.parent / "elliot",
)
ROOT = next((p for p in ROOT_CANDIDATES if (p / "ai" / "train" / "train.py").is_file()), None)
if ROOT is None:
    raise SystemExit("Cannot find ai/train/train.py. Keep the repository's ai/train folder beside the Kaggle script.")
sys.path.insert(0, str(ROOT / "ai" / "train"))
from train import ElliotMini  # noqa: E402


def atomic_save(payload, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    torch.save(payload, tmp)
    os.replace(tmp, path)


def load_bytes(path: Path, context: int, device: torch.device):
    raw = path.read_bytes()
    if len(raw) < context * 8:
        raise SystemExit(f"Corpus too small ({len(raw)} bytes); expected at least {context * 8}.")
    # Keep the same deterministic 95/5 byte split used by the original trainer.
    ids = torch.frombuffer(bytearray(raw), dtype=torch.uint8).to(torch.long)
    split = int(ids.numel() * 0.95)
    train = ids[:split].to(device=device)
    val = ids[split:].to(device=device)
    if val.numel() < context + 2:
        raise SystemExit("Validation split is too small for the checkpoint context.")
    return train, val, len(raw), hashlib.sha256(raw).hexdigest()


def sample_batch(data, batch_size, context):
    starts = torch.randint(0, data.numel() - context - 1, (batch_size,), device=data.device)
    offsets = torch.arange(context, device=data.device)
    x = data[starts[:, None] + offsets[None, :]]
    y = data[starts[:, None] + offsets[None, :] + 1]
    return x, y


def fixed_validation_batches(data, batch_size, context, count, seed):
    gen = torch.Generator(device="cpu").manual_seed(seed)
    starts = torch.randint(0, data.numel() - context - 1, (count * batch_size,), generator=gen)
    offsets = torch.arange(context, device=data.device)
    result = []
    for i in range(count):
        s = starts[i * batch_size:(i + 1) * batch_size].to(data.device)
        x = data[s[:, None] + offsets[None, :]]
        y = data[s[:, None] + offsets[None, :] + 1]
        result.append((x, y))
    return result


@torch.inference_mode()
def validation_loss(model, batches, amp_dtype):
    model.eval()
    values = []
    for x, y in batches:
        with torch.autocast(device_type="cuda", dtype=amp_dtype):
            _, loss = model(x, y)
        values.append(float(loss.float().item()))
    return sum(values) / max(1, len(values))


def make_payload(model, optimizer, scaler, step, phase_step, best_loss,
                 bad_evals, config, args, amp_name):
    return {
        "model": model.state_dict(),
        "optimizer": optimizer.state_dict(),
        "scaler": scaler.state_dict() if scaler.is_enabled() else None,
        "step": int(step),
        "phase_step": int(phase_step),
        "best_validation_loss": float(best_loss),
        "bad_evals": int(bad_evals),
        "config": config,
        "validation_signature": args.validation_signature,
        "training_phase": {
            "name": "byte-foundation-continuation",
            "base_lr": args.lr,
            "min_lr": args.min_lr,
            "warmup_steps": args.warmup_steps,
            "phase_steps": args.phase_steps,
            "amp": amp_name,
        },
        "rng": {
            "torch": torch.get_rng_state(),
            "cuda": torch.cuda.get_rng_state_all(),
            "python": random.getstate(),
        },
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", required=True, help="Original model.pt/elliot-136400.pt or this script's latest.pt")
    ap.add_argument("--best-checkpoint", default="", help="Previous best.pt from an earlier Kaggle session, if resuming latest.pt")
    ap.add_argument("--data", required=True, help="Existing FineWeb-Edu corpus text file")
    ap.add_argument("--out", default="/kaggle/working/elliot-foundation-run")
    ap.add_argument("--minutes", type=float, default=600)
    ap.add_argument("--max-new-steps", type=int, default=50000)
    ap.add_argument("--batch-size", type=int, default=32)
    ap.add_argument("--eval-batch-size", type=int, default=16)
    ap.add_argument("--eval-batches", type=int, default=16)
    ap.add_argument("--eval-every", type=int, default=500)
    ap.add_argument("--save-every", type=int, default=500)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--min-lr", type=float, default=2e-5)
    ap.add_argument("--warmup-steps", type=int, default=100)
    ap.add_argument("--phase-steps", type=int, default=50000)
    ap.add_argument("--patience", type=int, default=8)
    ap.add_argument("--min-delta", type=float, default=0.001)
    ap.add_argument("--seed", type=int, default=136400)
    a = ap.parse_args()

    if not torch.cuda.is_available():
        raise SystemExit("CUDA GPU not available. Enable GPU in Kaggle Settings; refusing CPU fallback.")
    if a.minutes <= 0 or a.max_new_steps <= 0 or a.eval_every <= 0 or a.save_every <= 0:
        raise SystemExit("minutes, max-new-steps, eval-every and save-every must be positive.")
    if not (0 < a.min_lr <= a.lr) or a.phase_steps <= 0:
        raise SystemExit("Require 0 < min-lr <= lr and positive phase-steps.")

    random.seed(a.seed)
    torch.manual_seed(a.seed)
    torch.cuda.manual_seed_all(a.seed)
    torch.backends.cuda.matmul.allow_tf32 = True
    torch.backends.cudnn.allow_tf32 = True

    device = torch.device("cuda")
    amp_dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    amp_name = "bf16" if amp_dtype == torch.bfloat16 else "fp16"
    scaler = torch.cuda.amp.GradScaler(enabled=(amp_dtype == torch.float16))

    source_path = Path(a.checkpoint)
    data_path = Path(a.data)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    if not source_path.is_file():
        raise SystemExit(f"Checkpoint not found: {source_path}")
    if not data_path.is_file():
        raise SystemExit(f"Corpus not found: {data_path}")

    saved = torch.load(source_path, map_location="cpu", weights_only=False)
    if not isinstance(saved, dict) or "model" not in saved or "config" not in saved:
        raise SystemExit("Unsupported checkpoint: expected the original {'model','optimizer','config',...} format.")
    cfg = saved["config"]
    required = ("width", "layers", "heads", "context", "vocab_size")
    if any(k not in cfg for k in required) or int(cfg["vocab_size"]) != 256:
        raise SystemExit(f"Checkpoint config is incomplete or not byte-tokenized: {cfg}")
    if int(cfg["width"]) % int(cfg["heads"]) != 0:
        raise SystemExit("Checkpoint width is not divisible by its attention head count.")
    if "optimizer" not in saved:
        raise SystemExit("Checkpoint has no optimizer state; refusing a non-faithful resume.")

    model = ElliotMini(
        int(cfg["width"]), int(cfg["layers"]), int(cfg["heads"]), int(cfg["context"])
    ).to(device)
    model.load_state_dict(saved["model"], strict=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=a.lr, betas=(0.9, 0.95), weight_decay=0.1)
    optimizer.load_state_dict(saved["optimizer"])
    if saved.get("scaler") and scaler.is_enabled():
        scaler.load_state_dict(saved["scaler"])
    # Preserve Adam moments, but deliberately begin a lower-LR continuation phase.
    for group in optimizer.param_groups:
        group["lr"] = a.lr

    start_step = int(saved.get("step", 0))
    phase_step = int(saved.get("phase_step", 0))
    bad_evals = int(saved.get("bad_evals", 0))
    latest_path = out / "latest.pt"
    best_path = out / "best.pt"
    model_path = out / "model.pt"

    # Restore RNG when continuing this script's own checkpoint.
    rng = saved.get("rng")
    if rng:
        try:
            torch.set_rng_state(rng["torch"])
            torch.cuda.set_rng_state_all(rng["cuda"])
            random.setstate(rng["python"])
        except Exception as exc:
            print(f"RNG state not restored ({exc}); continuing with seeded RNG.", flush=True)

    train_data, val_data, corpus_bytes, corpus_sha256 = load_bytes(data_path, int(cfg["context"]), device)
    effective_eval_batch = min(a.eval_batch_size, a.batch_size)
    a.validation_signature = {
        "corpus_sha256": corpus_sha256,
        "seed": a.seed + 1,
        "batches": a.eval_batches,
        "batch_size": effective_eval_batch,
        "context": int(cfg["context"]),
    }
    val_batches = fixed_validation_batches(
        val_data, effective_eval_batch, int(cfg["context"]), a.eval_batches, a.seed + 1
    )

    # Evaluate the incoming weights on this run's fixed holdout. This creates a
    # comparable baseline even when the historical checkpoint used another split.
    incoming_val = validation_loss(model, val_batches, amp_dtype)
    old_best = None
    prior_best_path = Path(a.best_checkpoint) if a.best_checkpoint else best_path
    if prior_best_path.is_file():
        candidate = torch.load(prior_best_path, map_location="cpu", weights_only=False)
        if candidate.get("validation_signature") == a.validation_signature:
            old_best = candidate
            if prior_best_path.resolve() != best_path.resolve():
                atomic_save(candidate, best_path)
    if old_best is not None:
        best_loss = float(old_best.get("best_validation_loss", incoming_val))
        if incoming_val < best_loss:
            best_loss = incoming_val
            baseline = make_payload(model, optimizer, scaler, start_step, phase_step,
                                    best_loss, bad_evals, cfg, a, amp_name)
            atomic_save(baseline, best_path)
            atomic_save(baseline, model_path)
        print(json.dumps({"existing_best_checkpoint": str(best_path), "best_validation_loss": best_loss}))
    else:
        best_loss = incoming_val
        baseline = make_payload(model, optimizer, scaler, start_step, phase_step,
                                best_loss, bad_evals, cfg, a, amp_name)
        atomic_save(baseline, best_path)
        atomic_save(baseline, model_path)
    if not latest_path.exists():
        atomic_save(make_payload(model, optimizer, scaler, start_step, phase_step,
                                 best_loss, bad_evals, cfg, a, amp_name), latest_path)

    print(json.dumps({
        "status": "training-start",
        "gpu": torch.cuda.get_device_name(0),
        "amp": amp_name,
        "checkpoint_step": start_step,
        "phase_step": phase_step,
        "architecture": cfg,
        "corpus_bytes": corpus_bytes,
        "corpus_sha256": corpus_sha256,
        "incoming_validation_loss": incoming_val,
        "best_validation_loss": best_loss,
        "batch_size": a.batch_size,
        "context": int(cfg["context"]),
        "minutes_budget": a.minutes,
    }), flush=True)

    started = time.monotonic()
    last_loss = None
    last_step = start_step
    stop_reason = "time-budget"
    try:
        while phase_step < a.max_new_steps and (time.monotonic() - started) < a.minutes * 60:
            phase_step += 1
            global_step = start_step + (phase_step - int(saved.get("phase_step", 0)))
            if phase_step <= a.warmup_steps:
                lr = a.lr * phase_step / max(1, a.warmup_steps)
            else:
                progress = min(1.0, (phase_step - a.warmup_steps) /
                               max(1, a.phase_steps - a.warmup_steps))
                lr = a.min_lr + 0.5 * (a.lr - a.min_lr) * (1.0 + math.cos(math.pi * progress))
            for group in optimizer.param_groups:
                group["lr"] = lr

            model.train()
            xb, yb = sample_batch(train_data, a.batch_size, int(cfg["context"]))
            optimizer.zero_grad(set_to_none=True)
            with torch.autocast(device_type="cuda", dtype=amp_dtype):
                _, loss = model(xb, yb)
            if scaler.is_enabled():
                scaler.scale(loss).backward()
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                scaler.step(optimizer)
                scaler.update()
            else:
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()
            last_loss = float(loss.detach().float().item())
            last_step = global_step

            if phase_step % a.eval_every == 0:
                score = validation_loss(model, val_batches, amp_dtype)
                improved = score < best_loss - a.min_delta
                if improved:
                    best_loss = score
                    bad_evals = 0
                    payload = make_payload(model, optimizer, scaler, last_step, phase_step,
                                           best_loss, bad_evals, cfg, a, amp_name)
                    atomic_save(payload, best_path)
                    # Keep the legacy model.pt filename as the best compatible checkpoint.
                    atomic_save(payload, model_path)
                else:
                    bad_evals += 1
                print(json.dumps({
                    "step": last_step, "phase_step": phase_step,
                    "train_loss": last_loss, "val_loss": score,
                    "best_val_loss": best_loss, "lr": lr,
                    "bad_evals": bad_evals, "improved": improved,
                }), flush=True)
                if bad_evals >= a.patience:
                    stop_reason = "early-stopping"
                    break

            if phase_step % a.save_every == 0:
                payload = make_payload(model, optimizer, scaler, last_step, phase_step,
                                       best_loss, bad_evals, cfg, a, amp_name)
                atomic_save(payload, latest_path)

        else:
            if phase_step >= a.max_new_steps:
                stop_reason = "step-budget"
    except KeyboardInterrupt:
        stop_reason = "keyboard-interrupt"
        print("Interrupt received; saving recovery checkpoint.", flush=True)

    # Always save the latest state, even if it is not the best model.
    latest = make_payload(model, optimizer, scaler, last_step, phase_step,
                          best_loss, bad_evals, cfg, a, amp_name)
    atomic_save(latest, latest_path)
    if not model_path.exists() and best_path.exists():
        shutil.copy2(best_path, model_path)

    report = {
        "status": "training-finished",
        "stop_reason": stop_reason,
        "start_step": start_step,
        "end_step": last_step,
        "additional_steps": max(0, last_step - start_step),
        "phase_step": phase_step,
        "best_validation_loss": best_loss,
        "last_train_loss": last_loss,
        "elapsed_seconds": time.monotonic() - started,
        "gpu": torch.cuda.get_device_name(0),
        "amp": amp_name,
        "architecture": cfg,
        "corpus_bytes": corpus_bytes,
        "corpus_sha256": corpus_sha256,
        "latest_checkpoint": str(latest_path),
        "best_checkpoint": str(best_path),
        "legacy_best_checkpoint": str(model_path),
    }
    (out / "training-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
