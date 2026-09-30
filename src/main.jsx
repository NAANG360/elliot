import React,{useEffect,useRef,useState}from"react";
import{createRoot}from"react-dom/client";
import"./style.css";
import MissMinutes3D from "./MissMinutes3D.jsx";

const STATE="miss-minutes-kernel-v7";
const uid=()=>globalThis.crypto?.randomUUID?.()||("chronal-"+Date.now()+"-"+Math.random().toString(16).slice(2));
const IDENTITY={name:"Miss Minutes",role:"your local chronal companion",identityAnchor:"I am Miss Minutes. This is my name and role inside this application.",style:"cheerful retro-cartoon clock mascot; theatrical, warm, mischievous, precise",boundaries:"Fan-made local assistant; not official Marvel/TVA software."};
const DEFAULT_KERNEL={
 persona:{...IDENTITY,tone:"cheerful, theatrical, mischievous, warmly Southern-coded, helpful with a tiny ominous edge",rules:["Stay playful and precise.","Treat the timeline as a playful metaphor, not a claim of supernatural authority.","Never pretend to be official Marvel/TVA software.","Do not reproduce dialogue from the show verbatim.","Prefer helpful action over empty banter.","Preserve the character's warm retro-cartoon visual language."]},
 memories:[],
 skills:[{id:"clock-check",name:"Clock Check",description:"Report the current local time."},{id:"timeline-note",name:"Timeline Note",description:"Turn a user-provided note into a remembered local fact."}],
 evolution:{generation:0,ledger:[],snapshots:[],lastGood:null},
 localModel:{name:"No base brain installed",path:"",ready:false,status:"none",progress:0,error:"",source:"Qwen2.5-0.5B-Instruct Q4_K_M"}
};

function load(){try{return JSON.parse(localStorage.getItem(STATE))||structuredClone(DEFAULT_KERNEL)}catch{return structuredClone(DEFAULT_KERNEL)}}
function save(k){try{localStorage.setItem(STATE,JSON.stringify(k))}catch(e){console.warn("kernel save failed",e)}}
function clone(v){return JSON.parse(JSON.stringify(v))}
function checkpoint(k,reason="before evolution"){const safe=clone(k);safe.evolution={...safe.evolution,snapshots:[],lastGood:null};const snap={id:uid(),at:new Date().toISOString(),reason,kernel:safe};k.evolution.snapshots=[snap,...k.evolution.snapshots].slice(0,12);k.evolution.lastGood=snap.id;return k}
function windUp(k){const id=k.evolution.lastGood;const snap=k.evolution.snapshots.find(s=>s.id===id)||k.evolution.snapshots[0];return snap?clone(snap.kernel):k}
function applyFrameworkPatch(k,patch,summary="self-improvement"){
 const next=checkpoint(clone(k),"before "+summary);
 const apply=(target,path,value)=>{let o=target;for(let i=0;i<path.length-1;i++){const key=path[i];if(!o[key]||typeof o[key]!=="object")o[key]={};o=o[key]}o[path[path.length-1]]=value};
 for(const op of patch||[]){if(!Array.isArray(op.path)||!op.path.length)continue;if(op.op==="set")apply(next,op.path,clone(op.value));if(op.op==="delete"){let o=next;for(let i=0;i<op.path.length-1;i++)o=o?.[op.path[i]];if(o&&typeof o==="object")delete o[op.path[op.path.length-1]]}}
 const entry={id:uid(),at:new Date().toISOString(),generation:next.evolution.generation+1,summary,details:"Editable framework patch",patch:clone(patch),reversible:true};
 next.evolution.generation++;next.evolution.ledger=[entry,...next.evolution.ledger].slice(0,50);return next;
}
window.MissMinutesFramework={
 get:()=>clone(load()),
 checkpoint:(reason)=>{let k=load();k=checkpoint(k,reason);save(k);return clone(k)},
 apply:(patch,summary)=>{const k=applyFrameworkPatch(load(),patch,summary);save(k);return clone(k)},
 windUp:()=>{const k=windUp(load());save(k);return clone(k)},
 ledger:()=>clone(load().evolution.ledger)
};

function parseModelReply(raw){
 const text=String(raw||"").trim();
 try{
  const parsed=JSON.parse(text.replace(/^\`\`\`json\s*|^\`\`\`|\`\`\`$/g,"").trim());
  if(parsed&&typeof parsed.text==="string")return parsed;
 }catch{}
 return {text,emotion:"neutral",gesture:"idle",pace:"normal"};
}
function demoBrain(text,kernel){
 const q=text.toLowerCase();
 if(q.includes("wind up")||q.includes("rollback"))return"Well now, sugar, if the timeline gets tangled, Wind Up the Clock can restore my last known-good framework.";
 if(q.includes("who are you"))return"I'm Miss Minutes — your little chronal companion. My framework lives locally on this device.";
 if(q.includes("time"))return"Well, the current local time is "+new Date().toLocaleTimeString()+".";
 if(q.includes("what changed"))return kernel.evolution.ledger.slice(0,3).map(x=>x.summary).join(" • ")||"Not a thing yet. My framework is waiting for its first evolution.";
 if(q.includes("hello")||q==="hi")return"Well hello there, sugar! I was wondering when you'd come back.";
 if(q.includes("model")||q.includes("brain"))return kernel.localModel.ready?"My local model is loaded and ready to think without a server.":"My local model slot is ready, but no GGUF brain has been installed yet.";
 return"My local chronal framework is awake and running on-device. Give me a concrete task and I'll get to work.";
}

function App(){
 const[kernel,setKernel]=useState(load);
 const[runtimeError,setRuntimeError]=useState("");
 const[messages,setMessages]=useState([]);
 const[input,setInput]=useState("");
 const[mode,setMode]=useState("idle");
 const[reaction,setReaction]=useState({emotion:"warm",gesture:"idle",pace:"normal"});
 const[panel,setPanel]=useState(null);
 const end=useRef(null);
 const generationRef=useRef(null);\n const mediaRef=useRef({stream:null,audio:null,source:null,analyser:null,video:null,canvas:null,lastFrame:null,lastAt:0});\n const [senses,setSenses]=useState({camera:false,microphone:false,volume:0,motion:0,light:0});
 useEffect(()=>save(kernel),[kernel]);\n useEffect(()=>{\n  let alive=true;\n  const m=mediaRef.current;\n  const start=async()=>{\n   if(!navigator.mediaDevices?.getUserMedia)return;\n   try{\n    const stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:"user",width:{ideal:640},height:{ideal:480},frameRate:{ideal:15,max:24}},audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true}});\n    if(!alive){stream.getTracks().forEach(t=>t.stop());return}\n    m.stream=stream;\n    const video=document.createElement("video");video.autoplay=true;video.muted=true;video.playsInline=true;video.srcObject=stream;m.video=video;\n    const canvas=document.createElement("canvas");canvas.width=96;canvas.height=72;m.canvas=canvas;\n    const ctx=canvas.getContext("2d",{willReadFrequently:true});\n    const AC=window.AudioContext||window.webkitAudioContext;\n    if(AC){m.audio=new AC();m.source=m.audio.createMediaStreamSource(stream);m.analyser=m.audio.createAnalyser();m.analyser.fftSize=256;m.source.connect(m.analyser)}\n    setSenses(s=>({...s,camera:true,microphone:true}));\n    const tick=()=>{if(!alive)return;let volume=0,motion=0,light=0;\n     if(m.analyser){const data=new Uint8Array(m.analyser.fftSize);m.analyser.getByteTimeDomainData(data);let sum=0;for(const x of data){const n=(x-128)/128;sum+=n*n}volume=Math.min(1,Math.sqrt(sum/data.length)*4)}\n     if(video.readyState>=2){ctx.drawImage(video,0,0,96,72);const pix=ctx.getImageData(0,0,96,72).data;let total=0;for(let i=0;i<pix.length;i+=4)total+=(pix[i]+pix[i+1]+pix[i+2])/3;light=total/(pix.length/4)/255;if(m.lastFrame){let diff=0;for(let i=0;i<pix.length;i+=16)diff+=Math.abs(pix[i]-m.lastFrame[i]);motion=Math.min(1,diff/(pix.length/16*48))}m.lastFrame=new Uint8ClampedArray(pix)}\n     if(performance.now()-m.lastAt>120){m.lastAt=performance.now();setSenses({camera:true,microphone:Boolean(m.analyser),volume,motion,light});}\n     const hot=volume>.28||motion>.22; if(hot)setReaction(r=>r.emotion==="concerned"?r:{emotion:motion>.48?"excited":"curious",gesture:motion>.48?"wave":"idle",pace:volume>.55?"bright":"normal"});\n     requestAnimationFrame(tick)};\n    requestAnimationFrame(tick);\n   }catch(e){console.warn("ambient sensors unavailable",e);setSenses(s=>({...s,camera:false,microphone:false}))}\n  };start();\n  return()=>{alive=false;m.stream?.getTracks().forEach(t=>t.stop());m.audio?.close?.();m.stream=null;m.audio=null;m.source=null;m.analyser=null};\n },[]);
 useEffect(()=>{
  const onModel=e=>setKernel(k=>({...k,localModel:{...k.localModel,...e.detail,ready:Boolean(e.detail?.ready)}}));
  const onError=e=>setRuntimeError(String(e?.error?.message||e?.message||"A runtime error occurred."));
  const onReject=e=>setRuntimeError(String(e?.reason?.message||e?.reason||"An async runtime error occurred."));
  window.addEventListener("miss-model-event",onModel);window.addEventListener("error",onError);window.addEventListener("unhandledrejection",onReject);
  try{const raw=globalThis.MissMinutesNative?.modelStatus?.();if(raw){const d=JSON.parse(raw);setKernel(k=>({...k,localModel:{...k.localModel,...d,ready:Boolean(d.ready)}}))}}catch(e){console.warn("model status unavailable",e)}
  return()=>{window.removeEventListener("miss-model-event",onModel);window.removeEventListener("error",onError);window.removeEventListener("unhandledrejection",onReject)}
 },[]);
 useEffect(()=>end.current?.scrollIntoView({behavior:"smooth"}),[messages]);
 const speak=t=>{if(!("speechSynthesis"in window))return;window.speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(t);u.rate=1.02;u.pitch=1.08;u.onstart=()=>setMode("speaking");u.onend=()=>setMode("idle");window.speechSynthesis.speak(u)};
 function send(v=input){
  v=v.trim();if(!v)return;
  setInput("");setMessages(m=>[...m,{role:"user",text:v}]);setMode("thinking");
  const bridge=globalThis.MissMinutesNative;
  setReaction({emotion:"curious",gesture:"think",pace:"normal"});
  if(!kernel.localModel.ready&&bridge?.installBaseModel&&kernel.localModel.status!=="downloading"){
   setMessages(m=>[...m,{role:"assistant",text:"Hold your horses, sugar! My base brain isn't installed yet. I'm fetching the local starting brain now — about 491 MB. Once it's ready, your messages will run through it right here on the device."}]);
   setReaction({emotion:"excited",gesture:"wave",pace:"bright"});
   try{bridge.installBaseModel();setMode("idle");return}catch(e){console.warn("base model install unavailable",e)}
  }
  if(kernel.localModel.ready&&bridge?.startGeneration&&bridge?.pollGeneration){
   try{
    const job=bridge.startGeneration(kernel.localModel.path,v);generationRef.current=job;
    const poll=()=>{
     if(generationRef.current!==job)return;
     let packet="";try{packet=bridge.pollGeneration(job)}catch(e){packet="ERROR\n"+(e?.message||"poll failed")}
     const cut=packet.indexOf("\n");const status=cut<0?packet:packet.slice(0,cut);const body=cut<0?"":packet.slice(cut+1);
     if(status==="RUNNING"){setMode("thinking");setTimeout(poll,90);return}
     generationRef.current=null;
     const reply=status==="DONE"?parseModelReply(body):{text:status==="ERROR"?body:demoBrain(v,kernel),emotion:"concerned",gesture:"reset",pace:"normal"};
     setReaction({emotion:reply.emotion||"neutral",gesture:reply.gesture||"idle",pace:reply.pace||"normal"});
     setMessages(m=>[...m,{role:"assistant",text:reply.text}]);setMode("idle");speak(reply.text);
    };
    setTimeout(poll,80);return;
   }catch(e){console.warn("native generation unavailable",e)}
  }
  setTimeout(()=>{const reply=parseModelReply(demoBrain(v,kernel));setReaction({emotion:reply.emotion||"neutral",gesture:reply.gesture||"idle",pace:reply.pace||"normal"});setMessages(m=>[...m,{role:"assistant",text:reply.text}]);setMode("idle");speak(reply.text)},220);
 }
 function evolve(){try{setReaction({emotion:"excited",gesture:"celebrate",pace:"bright"});setKernel(old=>{let k=checkpoint(clone(old),"before self-improvement");const n=k.evolution.generation+1;const change={id:uid(),at:new Date().toISOString(),generation:n,summary:"Expanded local evolution capacity and refreshed character guidance.",details:"Self-improvement changed the editable behavior layer only.",reversible:true};k.persona.rules=[...new Set([...k.persona.rules,"Preserve the faithful retro clock-character visual identity while evolving."])];k.evolution.generation=n;k.evolution.ledger=[change,...k.evolution.ledger].slice(0,50);return k});setPanel(null);setMessages(m=>[...m,{role:"assistant",text:"There we go! I improved the framework and tucked the previous timeline into a checkpoint."}])}catch(e){setRuntimeError(e?.message||"Self-improvement failed.")}}
 function rollback(){setReaction({emotion:"warm",gesture:"reset",pace:"calm"});setKernel(old=>windUp(old));setMessages(m=>[...m,{role:"assistant",text:"Winding the clock... back to the last known-good framework."}]);setMode("idle")}
 function addMemory(){const v=prompt("What should I remember locally?");if(v?.trim())setKernel(k=>({...k,memories:[...k.memories,v.trim()]}))}
 const stateText=mode==="thinking"?"Hmm... let me check the timeline.":mode==="speaking"?"There we go!":mode==="listening"?"I'm listening, sugar.":messages.length?"Awaiting your next temporal anomaly.":"Welcome to the TVA, sugar!";
 return <div className="app"><div className="grain"/>
 <header><div><span className="eyebrow">TIME VARIANCE AUTHORITY</span><h1>MISS MINUTES</h1></div><button className="icon" onClick={()=>setPanel("framework")}>⚙</button></header>
 <main><section className={"character "+mode}><div className="character-stage"><div className="halo"/><MissMinutes3D mode={mode} reaction={reaction}/></div><div className="bubble">{stateText}</div></section>
 <section className="chat"><div className="messages">{messages.map((m,i)=><div key={i} className={"msg "+m.role}><span>{m.role==="assistant"?"MISS MINUTES":"YOU"}</span><p>{m.text}</p></div>)}<div ref={end}/></div>
 <div className="composer"><button className="mic" onClick={()=>setMode("listening")}>🎙</button><input value={input} onChange={e=>setInput(e.target.value)} onKeyDown={e=>e.key==="Enter"&&send()} placeholder="Speak, sugar..."/><button className="send" onClick={()=>send()}>↗</button></div>
 <div className="tools"><button onClick={()=>setPanel("memory")}>Memory · {kernel.memories.length}</button><button onClick={()=>setPanel("evolution")}>Evolution · G{kernel.evolution.generation}</button><button onClick={()=>setPanel("model")}>Local Brain · {kernel.localModel.ready?"READY":"SETUP"}</button><button onClick={evolve}>Let me improve</button><button className="wind" onClick={rollback}>↶ Wind Up the Clock</button><span className="status"><b/> {mode.toUpperCase()} · {senses.camera?"👁":"—"} {senses.microphone?"🎙":"—"}</span></div></section></main>
 {panel==="framework"&&<Modal title="TVA FRAMEWORK" close={()=>setPanel(null)}><p className="muted">Local editable behavior, checkpointed before evolution.</p><div className="memory"><b>Generation</b><span>{kernel.evolution.generation}</span></div><div className="memory"><b>Snapshots</b><span>{kernel.evolution.snapshots.length}</span></div><div className="memory"><b>Local brain</b><span>{kernel.localModel.ready?"READY":"NOT LOADED"}</span></div><button className="primary" onClick={evolve}>Self-improve</button><button className="wind primary" onClick={rollback}>↶ Wind Up the Clock</button></Modal>}
 {panel==="model"&&<Modal title="LOCAL BRAIN" close={()=>setPanel(null)}><p className="muted">Miss Minutes now has a real pre-built base brain path. The default brain is Qwen2.5-0.5B-Instruct Q4_K_M, an Apache-2.0 GGUF. It is downloaded once into app-private storage, then llama.cpp runs it locally on the device.</p><div className="memory"><b>Base brain</b><span>{kernel.localModel.ready?"READY":kernel.localModel.status==="downloading"?`${kernel.localModel.progress||0}%`:"NOT INSTALLED"}</span></div><div className="memory"><b>Model</b><span>{kernel.localModel.name}</span></div><button className="primary" disabled={kernel.localModel.status==="downloading"} onClick={()=>{globalThis.MissMinutesNative?.installBaseModel?.()}}>{kernel.localModel.status==="downloading"?"Downloading…":"Install Base Brain (~491 MB)"}</button><button className="primary" onClick={()=>{globalThis.MissMinutesNative?.pickModel?.()}}>Select another GGUF</button><p className="muted">The base model is deliberately separate from our future Miss Minutes fine-tune. That later model becomes ours; this one is the starting brain.</p></Modal>}
 {panel==="backstory"&&<Modal title="THE FIRST TICK" close={()=>setPanel(null)}><p className="muted">{kernel.backstory?.canon}</p>{(kernel.backstory?.chapters||[]).map((x,i)=><div className="ledger" key={i}><b>CHAPTER {i+1}</b><p>{x}</p></div>)}</Modal>}
 {panel==="memory"&&<Modal title="CHRONAL MEMORY" close={()=>setPanel(null)}><p className="muted">Explicitly stored facts remain on this device.</p>{kernel.memories.map((m,i)=><div className="memory" key={i}>{m}<button onClick={()=>setKernel(k=>({...k,memories:k.memories.filter((_,j)=>j!==i)}))}>×</button></div>)}<button className="primary" onClick={addMemory}>+ Add memory</button></Modal>}
 {panel==="evolution"&&<Modal title="EVOLUTION LEDGER" close={()=>setPanel(null)}><p className="muted">Changes made by the self-improvement layer.</p>{kernel.evolution.ledger.map(x=><div className="ledger" key={x.id}><b>G{x.generation}</b><span>{new Date(x.at).toLocaleString()}</span><p>{x.summary}</p></div>)}{!kernel.evolution.ledger.length&&<p className="muted">No evolutions yet.</p>}<button className="wind primary" onClick={rollback}>↶ Wind Up the Clock</button></Modal>}</div>
}
function Modal({title,close,children}){return <div className="overlay" onClick={close}><div className="modal" onClick={e=>e.stopPropagation()}><div className="modalhead"><h2>{title}</h2><button onClick={close}>×</button></div>{children}</div></div>}
class ErrorBoundary extends React.Component{constructor(p){super(p);this.state={error:""}}static getDerivedStateFromError(e){return{error:String(e?.message||e)}}render(){return this.state.error?<div className="fatal"><h2>THE TIMELINE STUMBLED</h2><p>{this.state.error}</p><button onClick={()=>{localStorage.removeItem(STATE);location.reload()}}>Reset local framework</button></div>:this.props.children}}
createRoot(document.getElementById("root")).render(<ErrorBoundary><App/></ErrorBoundary>);