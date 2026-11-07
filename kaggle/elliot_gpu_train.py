#!/usr/bin/env python3
"""Kaggle GPU QLoRA fine-tuning for Elliot's Qwen2.5-0.5B-Instruct brain.

This is a separate, compatible path from the experimental byte-level foundation model.
It preserves that checkpoint; it does not pretend the byte model can be loaded into Qwen.
"""
import argparse, json, os, random
from pathlib import Path
import torch
from datasets import Dataset
from transformers import (AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig,
                          Trainer, TrainingArguments, DataCollatorForLanguageModeling)
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training

BASE_MODEL = "Qwen/Qwen2.5-0.5B-Instruct"

def load_records(paths):
    rows=[]
    for path in paths:
        p=Path(path)
        if not p.exists(): continue
        for n,line in enumerate(p.read_text(encoding="utf-8").splitlines(),1):
            if not line.strip(): continue
            obj=json.loads(line)
            msgs=obj.get("messages")
            if not isinstance(msgs,list) or len(msgs)<2:
                raise ValueError(f"{p}:{n}: expected messages[] conversation format")
            if not any(m.get("role")=="assistant" for m in msgs):
                raise ValueError(f"{p}:{n}: no assistant response")
            rows.append({"messages":msgs})
    # Stable exact deduplication by canonical JSON.
    unique={json.dumps(x,ensure_ascii=False,sort_keys=True):x for x in rows}
    return list(unique.values())

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--data",nargs="+",default=["ai/dataset/seed.jsonl"])
    ap.add_argument("--output",default="/kaggle/working/elliot-output")
    ap.add_argument("--base-model",default=BASE_MODEL)
    ap.add_argument("--epochs",type=float,default=3)
    ap.add_argument("--max-length",type=int,default=1024)
    ap.add_argument("--batch-size",type=int,default=2)
    ap.add_argument("--grad-accum",type=int,default=8)
    ap.add_argument("--learning-rate",type=float,default=2e-4)
    ap.add_argument("--resume",default="")
    a=ap.parse_args()
    if not torch.cuda.is_available():
        raise SystemExit("CUDA GPU not available. In Kaggle: Settings → Accelerator → GPU. Refusing CPU fallback.")
    print("GPU:",torch.cuda.get_device_name(0))
    records=load_records(a.data)
    if len(records)<10:
        raise SystemExit(f"Only {len(records)} unique conversations. Add a larger reviewed curriculum before real training; refusing to overfit the seed set.")
    random.Random(42).shuffle(records)
    cut=max(1,int(len(records)*.9))
    train_rows,eval_rows=records[:cut],records[cut:]
    tokenizer=AutoTokenizer.from_pretrained(a.base_model,trust_remote_code=True)
    if tokenizer.pad_token is None: tokenizer.pad_token=tokenizer.eos_token
    def encode(rows):
        texts=[tokenizer.apply_chat_template(x["messages"],tokenize=False,add_generation_prompt=False) for x in rows]
        ds=Dataset.from_dict({"text":texts})
        def tok(batch):
            out=tokenizer(batch["text"],truncation=True,max_length=a.max_length)
            out["labels"]=[ids[:] for ids in out["input_ids"]]
            return out
        return ds.map(tok,batched=True,remove_columns=["text"])
    train_ds,eval_ds=encode(train_rows),encode(eval_rows)
    qcfg=BitsAndBytesConfig(load_in_4bit=True,bnb_4bit_quant_type="nf4",
                            bnb_4bit_compute_dtype=torch.float16,bnb_4bit_use_double_quant=True)
    model=AutoModelForCausalLM.from_pretrained(a.base_model,quantization_config=qcfg,
              device_map="auto",torch_dtype=torch.float16)
    model.config.use_cache=False
    model=prepare_model_for_kbit_training(model)
    lora=LoraConfig(r=16,lora_alpha=32,lora_dropout=.05,bias="none",
        task_type="CAUSAL_LM",target_modules=["q_proj","k_proj","v_proj","o_proj","gate_proj","up_proj","down_proj"])
    model=get_peft_model(model,lora)
    model.print_trainable_parameters()
    out=Path(a.output); out.mkdir(parents=True,exist_ok=True)
    args=TrainingArguments(output_dir=str(out/"trainer-state"),num_train_epochs=a.epochs,
        per_device_train_batch_size=a.batch_size,per_device_eval_batch_size=1,
        gradient_accumulation_steps=a.grad_accum,learning_rate=a.learning_rate,
        fp16=True,bf16=False,logging_steps=5,eval_strategy="epoch",
        save_strategy="epoch",save_total_limit=2,load_best_model_at_end=True,
        metric_for_best_model="eval_loss",greater_is_better=False,
        optim="paged_adamw_8bit",gradient_checkpointing=True,
        report_to="none",seed=42,data_seed=42)
    trainer=Trainer(model=model,args=args,train_dataset=train_ds,eval_dataset=eval_ds,
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer,mlm=False))
    trainer.train(resume_from_checkpoint=a.resume or None)
    metrics=trainer.evaluate()
    adapter=out/"adapter"
    trainer.model.save_pretrained(adapter)
    tokenizer.save_pretrained(adapter)
    report={"base_model":a.base_model,"adapter_type":"PEFT LoRA (4-bit QLoRA)",
      "train_conversations":len(train_rows),"eval_conversations":len(eval_rows),
      "epochs":a.epochs,"max_length":a.max_length,"gpu":torch.cuda.get_device_name(0),
      "metrics":metrics,"note":"Seed corpus is a smoke-test curriculum, not sufficient for production quality."}
    (out/"training-report.json").write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
if __name__=="__main__": main()
