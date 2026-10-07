#!/usr/bin/env python3
from __future__ import annotations
import argparse, csv, subprocess, tempfile
from pathlib import Path

def run(cmd):
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)

def download(url, dst):
    dst.mkdir(parents=True, exist_ok=True)
    out=str(dst/"%(id)s.%(ext)s")
    run(["yt-dlp","--no-playlist","-f","bestaudio/best","-x","--audio-format","wav","--audio-quality","0","-o",out,url])
    files=sorted(dst.glob("*"))
    if not files: raise RuntimeError("yt-dlp produced no file")
    return files[-1]

def transcribe(path, model_name):
    from faster_whisper import WhisperModel
    model=WhisperModel(model_name,device="auto",compute_type="auto")
    segments,_=model.transcribe(str(path),vad_filter=True,beam_size=5)
    return [(float(s.start),float(s.end)," ".join(s.text.split()).strip()) for s in segments if s.text.strip()]

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--input",action="append",default=[])
    ap.add_argument("--youtube",action="append",default=[])
    ap.add_argument("--authorized",action="store_true")
    ap.add_argument("--output",required=True)
    ap.add_argument("--whisper-model",default="small")
    ap.add_argument("--min-seconds",type=float,default=1.0)
    ap.add_argument("--max-seconds",type=float,default=12.0)
    args=ap.parse_args()
    if args.youtube and not args.authorized:
        raise SystemExit("Refusing YouTube acquisition without --authorized.")
    out=Path(args.output).resolve(); wav=out/"wav"; wav.mkdir(parents=True,exist_ok=True)
    sources=[]
    for raw in args.input:
        p=Path(raw).expanduser()
        if p.is_dir(): sources.extend(x for x in sorted(p.rglob("*")) if x.is_file())
        elif p.is_file(): sources.append(p)
    with tempfile.TemporaryDirectory(prefix="miss-minutes-voice-") as tmp:
        t=Path(tmp)
        for i,url in enumerate(args.youtube): sources.append(download(url,t/f"youtube_{i}"))
        rows=[]; n=1
        for source in sources:
            normalized=t/f"source_{n}.wav"
            run(["ffmpeg","-y","-i",str(source),"-vn","-ac","1","-ar","22050","-sample_fmt","s16",str(normalized)])
            for start,end,text in transcribe(normalized,args.whisper_model):
                if end-start<args.min_seconds or end-start>args.max_seconds: continue
                name=f"{n:06d}.wav"; target=wav/name
                run(["ffmpeg","-y","-ss",f"{start:.3f}","-i",str(normalized),"-t",f"{max(.25,end-start):.3f}","-vn","-ac","1","-ar","22050","-sample_fmt","s16","-af","loudnorm=I=-20:TP=-2:LRA=11",str(target)])
                if target.stat().st_size<4096: target.unlink(missing_ok=True); continue
                rows.append((name,text)); n+=1
    with (out/"metadata.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f,delimiter="|",lineterminator="\n")
        w.writerows(rows)
    print(f"Prepared {len(rows)} clips in {out}. Review them before training.")

if __name__=="__main__": main()
