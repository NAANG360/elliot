#!/usr/bin/env python3
"""Tiny byte-level decoder-only transformer trained from random initialization."""
import argparse, json, math, os, random, time
from pathlib import Path
import torch
from torch import nn
from torch.nn import functional as F

class ByteTokenizer:
    vocab_size = 256
    def encode(self, text): return list(text.encode("utf-8", errors="replace"))
    def decode(self, ids): return bytes([int(x) % 256 for x in ids]).decode("utf-8", errors="replace")

class Block(nn.Module):
    def __init__(self, width, heads, context, dropout):
        super().__init__()
        self.n1=nn.LayerNorm(width); self.attn=nn.MultiheadAttention(width,heads,dropout=dropout,batch_first=True)
        self.n2=nn.LayerNorm(width); self.ff=nn.Sequential(nn.Linear(width,4*width),nn.GELU(),nn.Linear(4*width,width),nn.Dropout(dropout))
        self.register_buffer("mask",torch.triu(torch.ones(context,context,dtype=torch.bool),diagonal=1),persistent=False)
    def forward(self,x):
        n=self.n1(x); x=x+self.attn(n,n,n,attn_mask=self.mask[:x.size(1),:x.size(1)],need_weights=False)[0]
        return x+self.ff(self.n2(x))

class ElliotMini(nn.Module):
    def __init__(self, width=384, layers=6, heads=6, context=512, dropout=.1):
        super().__init__(); self.context=context
        self.token=nn.Embedding(256,width); self.pos=nn.Embedding(context,width)
        self.blocks=nn.ModuleList([Block(width,heads,context,dropout) for _ in range(layers)])
        self.norm=nn.LayerNorm(width); self.head=nn.Linear(width,256,bias=False); self.head.weight=self.token.weight
        self.apply(self._init)
    @staticmethod
    def _init(m):
        if isinstance(m,nn.Linear): nn.init.normal_(m.weight,0,.02); nn.init.zeros_(m.bias) if m.bias is not None else None
        elif isinstance(m,nn.Embedding): nn.init.normal_(m.weight,0,.02)
    def forward(self,idx,targets=None):
        b,t=idx.shape; x=self.token(idx)+self.pos(torch.arange(t,device=idx.device))[None,:,:]
        for block in self.blocks: x=block(x)
        logits=self.head(self.norm(x)); loss=None if targets is None else F.cross_entropy(logits.reshape(-1,256),targets.reshape(-1))
        return logits,loss

def batches(data, batch, context, device):
    starts=torch.randint(0,len(data)-context-1,(batch,))
    x=torch.stack([data[i:i+context] for i in starts]).to(device)
    y=torch.stack([data[i+1:i+context+1] for i in starts]).to(device)
    return x,y

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--data",required=True);p.add_argument("--out",required=True);p.add_argument("--steps",type=int,default=2000)
    p.add_argument("--batch",type=int,default=8);p.add_argument("--context",type=int,default=512);p.add_argument("--width",type=int,default=384)
    p.add_argument("--layers",type=int,default=6);p.add_argument("--heads",type=int,default=6);p.add_argument("--lr",type=float,default=3e-4)
    p.add_argument("--seed",type=int,default=42);p.add_argument("--eval-every",type=int,default=200);p.add_argument("--minutes",type=float,default=0);p.add_argument("--resume",action="store_true")
    a=p.parse_args(); random.seed(a.seed);torch.manual_seed(a.seed)
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    raw=Path(a.data).read_text(encoding="utf-8"); ids=ByteTokenizer().encode(raw)
    if len(ids)<a.context*4: raise SystemExit("Corpus too small: provide at least four context windows.")
    split=int(len(ids)*.95); train=torch.tensor(ids[:split],dtype=torch.long); val=torch.tensor(ids[split:],dtype=torch.long)
    if len(val)<a.context+2: raise SystemExit("Validation split too small.")
    device="cuda" if torch.cuda.is_available() else "cpu"
    model=ElliotMini(a.width,a.layers,a.heads,a.context).to(device)
    opt=torch.optim.AdamW(model.parameters(),lr=a.lr,betas=(.9,.95),weight_decay=.1)
    best=float("inf"); started=time.time(); start_step=0\n    checkpoint=out/"model.pt"\n    if a.resume and checkpoint.is_file():\n        saved=torch.load(checkpoint,map_location=device,weights_only=False)\n        model.load_state_dict(saved["model"])\n        if "optimizer" in saved: opt.load_state_dict(saved["optimizer"])\n        start_step=int(saved.get("step",0)); best=float(saved.get("best_validation_loss",best))\n        print(json.dumps({"resume_step":start_step,"best_validation_loss":best}))
    for step in range(start_step+1,a.steps+1):\n        if a.minutes>0 and (time.time()-started)>=a.minutes*60: break
        model.train(); xb,yb=batches(train,a.batch,a.context,device); _,loss=model(xb,yb)
        opt.zero_grad(set_to_none=True);loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1.0);opt.step()
        if step%a.eval_every==0 or step==a.steps:
            model.eval()
            with torch.no_grad():
                vals=[model(*batches(val,min(a.batch,4),a.context,device))[1].item() for _ in range(8)]
            score=sum(vals)/len(vals); print(json.dumps({"step":step,"train_loss":loss.item(),"val_loss":score,"device":device}))
            if score<best:
                best=score;torch.save({"model":model.state_dict(),"optimizer":opt.state_dict(),"step":step,"best_validation_loss":best,"config":{"width":a.width,"layers":a.layers,"heads":a.heads,"context":a.context,"vocab_size":256}},checkpoint)
    config={"family":"elliot-byte-transformer-v0","width":a.width,"layers":a.layers,"heads":a.heads,"context":a.context,"vocab_size":256,"initialization":"random","pretrained_weights":False}
    (out/"config.json").write_text(json.dumps(config,indent=2))
    (out/"tokenizer.json").write_text(json.dumps({"type":"utf8-byte","vocab_size":256},indent=2))
    (out/"training.json").write_text(json.dumps({"steps_completed":start_step + (step-start_step if "step" in locals() else 0),"target_steps":a.steps,"best_validation_loss":best,"seconds":time.time()-started,"device":device,"seed":a.seed,"source":str(Path(a.data).name)},indent=2))
    print("Saved best from-scratch checkpoint:",out/"model.pt")
if __name__=="__main__": main()
