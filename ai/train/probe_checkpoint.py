#!/usr/bin/env python3
"""Generate honest raw-completion probes from a published Elliot checkpoint."""
import argparse, json, random
from pathlib import Path
import torch
from train import ElliotMini, ByteTokenizer

PROMPTS = [
    ("Greeting", "User: Hello Elliot. Who are you?\nElliot:"),
    ("Knowledge", "User: What is the capital of France? Explain briefly.\nElliot:"),
    ("Reasoning", "User: If I have 3 apples and buy 2 more, how many apples do I have?\nElliot:"),
    ("Coding", "User: Write a Python function that adds two numbers.\nElliot:"),
    ("Conversation", "User: I had a difficult day today. Can you talk with me?\nElliot:"),
]

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--checkpoint", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--new-bytes", type=int, default=240)
    a=p.parse_args()
    torch.manual_seed(7); random.seed(7)
    ck=torch.load(a.checkpoint,map_location="cpu",weights_only=False)
    cfg=ck["config"]
    model=ElliotMini(cfg["width"],cfg["layers"],cfg["heads"],cfg["context"])
    model.load_state_dict(ck["model"]); model.eval()
    tok=ByteTokenizer()
    lines=[
      "# Elliot checkpoint conversation probe", "",
      "**Mode:** raw next-byte language-model completion; not an instruction-tuned chat interface.",
      f"**Checkpoint step:** {ck.get('step')}  ",
      f"**Best validation loss:** {ck.get('best_validation_loss')}",
      f"**Architecture:** {json.dumps(cfg, sort_keys=True)}", "",
      "These are genuine model-generated continuations. No assistant responses are supplied or repaired.", ""
    ]
    for name,prompt in PROMPTS:
        ids=torch.tensor([tok.encode(prompt)],dtype=torch.long)
        generated=[]
        with torch.inference_mode():
            for _ in range(a.new_bytes):
                inp=ids[:,-cfg["context"]:]
                logits,_=model(inp)
                probs=torch.softmax(logits[0,-1,:]/0.85,dim=-1)
                nxt=torch.multinomial(probs,1)
                generated.append(int(nxt.item()))
                ids=torch.cat([ids,nxt.view(1,1)],dim=1)
        completion=tok.decode(generated)
        lines += [f"## {name}", "", f"**Prompt:** `{prompt}`", "", "~~~text", completion, "~~~", ""]
    Path(a.output).write_text("\n".join(lines),encoding="utf-8")
    print(f"Wrote {a.output} with {len(PROMPTS)} genuine completions")
if __name__=="__main__": main()
