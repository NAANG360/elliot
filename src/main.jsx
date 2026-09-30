import React,{useEffect,useRef,useState}from"react";
import{createRoot}from"react-dom/client";
import"./style.css";
import MissMinutes3D from "./MissMinutes3D.jsx";

const STATE="miss-minutes-kernel-v3";
const DEFAULT_KERNEL={
 persona:{name:"Miss Minutes",tone:"cheerful, theatrical, mischievous, warmly Southern-coded, helpful with a tiny ominous edge",rules:["Stay playful and precise.","Treat the timeline as a playful metaphor, not a claim of supernatural authority.","Never pretend to be official Marvel/TVA software.","Do not reproduce dialogue from the show verbatim.","Prefer helpful action over empty banter.","Preserve the character's warm retro-cartoon visual language."]},
 memories:[],
 skills:[{id:"clock-check",name:"Clock Check",description:"Report the current local time."},{id:"timeline-note",name:"Timeline Note",description:"Turn a user-provided note into a remembered local fact."}],
 evolution:{generation:0,ledger:[],snapshots:[],lastGood:null},
 localModel:{name:"No model selected",path:"",ready:false}
};

function load(){try{return JSON.parse(localStorage.getItem(STATE))||structuredClone(DEFAULT_KERNEL)}catch{return structuredClone(DEFAULT_KERNEL)}}
function save(k){localStorage.setItem(STATE,JSON.stringify(k))}
function clone(v){return JSON.parse(JSON.stringify(v))}
function checkpoint(k,reason="before evolution"){const snap={id:crypto.randomUUID(),at:new Date().toISOString(),reason,kernel:clone(k)};k.evolution.snapshots=[snap,...k.evolution.snapshots].slice(0,12);k.evolution.lastGood=snap.id;return k}
function windUp(k){const id=k.evolution.lastGood;const snap=k.evolution.snapshots.find(s=>s.id===id)||k.evolution.snapshots[0];return snap?clone(snap.kernel):k}
function applyFrameworkPatch(k,patch,summary="self-improvement"){
 const next=checkpoint(clone(k),"before "+summary);
 const apply=(target,path,value)=>{let o=target;for(let i=0;i<path.length-1;i++){const key=path[i];if(!o[key]||typeof o[key]!=="object")o[key]={};o=o[key]}o[path[path.length-1]]=value};
 for(const op of patch||[]){if(!Array.isArray(op.path)||!op.path.length)continue;if(op.op==="set")apply(next,op.path,clone(op.value));if(op.op==="delete"){let o=next;for(let i=0;i<op.path.length-1;i++)o=o?.[op.path[i]];if(o&&typeof o==="object")delete o[op.path[op.path.length-1]]}}
 const entry={id:crypto.randomUUID(),at:new Date().toISOString(),generation:next.evolution.generation+1,summary,details:"Editable framework patch",patch:clone(patch),reversible:true};
 next.evolution.generation++;next.evolution.ledger=[entry,...next.evolution.ledger].slice(0,50);return next;
}
window.MissMinutesFramework={
 get:()=>clone(load()),
 checkpoint:(reason)=>{let k=load();k=checkpoint(k,reason);save(k);return clone(k)},
 apply:(patch,summary)=>{const k=applyFrameworkPatch(load(),patch,summary);save(k);return clone(k)},
 windUp:()=>{const k=windUp(load());save(k);return clone(k)},
 ledger:()=>clone(load().evolution.ledger)
};

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
 const[messages,setMessages]=useState([]);
 const[input,setInput]=useState("");
 const[mode,setMode]=useState("idle");
 const[panel,setPanel]=useState(null);
 const end=useRef(null);
 useEffect(()=>save(kernel),[kernel]); useEffect(()=>{const fn=e=>setKernel(k=>({...k,localModel:{name:e.detail?.name||"Local GGUF",path:e.detail?.path||"",ready:Boolean(e.detail?.ready)}}));window.addEventListener("miss-model-ready",fn);return()=>window.removeEventListener("miss-model-ready",fn)},[]);
 useEffect(()=>end.current?.scrollIntoView({behavior:"smooth"}),[messages]);
 const speak=t=>{if(!("speechSynthesis"in window))return;window.speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(t);u.rate=1.02;u.pitch=1.08;u.onstart=()=>setMode("speaking");u.onend=()=>setMode("idle");window.speechSynthesis.speak(u)};
 function send(v=input){v=v.trim();if(!v)return;setInput("");setMessages(m=>[...m,{role:"user",text:v}]);setMode("thinking");setTimeout(()=>{const r=demoBrain(v,kernel);setMessages(m=>[...m,{role:"assistant",text:r}]);setMode("idle");speak(r)},220)}
 function evolve(){setKernel(old=>{let k=checkpoint(clone(old),"before self-improvement");const n=k.evolution.generation+1;const change={id:crypto.randomUUID(),at:new Date().toISOString(),generation:n,summary:"Expanded local evolution capacity and refreshed character guidance.",details:"Self-improvement changed the editable behavior layer only.",reversible:true};k.persona.rules=[...new Set([...k.persona.rules,"Preserve the retro clock-character visual identity while evolving."])];k.evolution.generation=n;k.evolution.ledger=[change,...k.evolution.ledger].slice(0,50);return k});setMessages(m=>[...m,{role:"assistant",text:"There we go! I improved the framework and tucked the previous timeline into a checkpoint."}])}
 function rollback(){setKernel(old=>windUp(old));setMessages(m=>[...m,{role:"assistant",text:"Winding the clock... back to the last known-good framework."}]);setMode("idle")}
 function addMemory(){const v=prompt("What should I remember locally?");if(v?.trim())setKernel(k=>({...k,memories:[...k.memories,v.trim()]}))}
 const stateText=mode==="thinking"?"Hmm... let me check the timeline.":mode==="speaking"?"There we go!":mode==="listening"?"I'm listening, sugar.":messages.length?"Awaiting your next temporal anomaly.":"Welcome to the TVA, sugar!";
 return <div className="app"><div className="grain"/>
 <header><div><span className="eyebrow">TIME VARIANCE AUTHORITY</span><h1>MISS MINUTES</h1></div><button className="icon" onClick={()=>setPanel("framework")}>⚙</button></header>
 <main><section className={"character "+mode}><div className="character-stage"><div className="halo"/><MissMinutes3D mode={mode}/></div><div className="bubble">{stateText}</div></section>
 <section className="chat"><div className="messages">{messages.map((m,i)=><div key={i} className={"msg "+m.role}><span>{m.role==="assistant"?"MISS MINUTES":"YOU"}</span><p>{m.text}</p></div>)}<div ref={end}/></div>
 <div className="composer"><button className="mic" onClick={()=>setMode("listening")}>🎙</button><input value={input} onChange={e=>setInput(e.target.value)} onKeyDown={e=>e.key==="Enter"&&send()} placeholder="Speak, sugar..."/><button className="send" onClick={()=>send()}>↗</button></div>
 <div className="tools"><button onClick={()=>setPanel("memory")}>Memory · {kernel.memories.length}</button><button onClick={()=>setPanel("evolution")}>Evolution · G{kernel.evolution.generation}</button><button onClick={()=>setPanel("model")}>Local Brain · {kernel.localModel.ready?"READY":"SETUP"}</button><button onClick={evolve}>Let me improve</button><button className="wind" onClick={rollback}>↶ Wind Up the Clock</button><span className="status"><b/> {mode.toUpperCase()}</span></div></section></main>
 {panel==="framework"&&<Modal title="TVA FRAMEWORK" close={()=>setPanel(null)}><p className="muted">Local editable behavior, checkpointed before evolution.</p><div className="memory"><b>Generation</b><span>{kernel.evolution.generation}</span></div><div className="memory"><b>Snapshots</b><span>{kernel.evolution.snapshots.length}</span></div><div className="memory"><b>Local brain</b><span>{kernel.localModel.ready?"READY":"NOT LOADED"}</span></div><button className="primary" onClick={evolve}>Self-improve</button><button className="wind primary" onClick={rollback}>↶ Wind Up the Clock</button></Modal>}
 {panel==="model"&&<Modal title="LOCAL BRAIN" close={()=>setPanel(null)}><p className="muted">The inference boundary is on-device. GGUF models are kept in app-private storage; no chat request needs a remote AI API.</p><div className="memory"><b>Model</b><span>{kernel.localModel.name}</span></div><button className="primary" onClick={()=>{globalThis.MissMinutesNative?.pickModel?.()}}>Select GGUF model</button><p className="muted">Model storage is wired to Android. Native llama.cpp inference is the next backend boundary; selecting a GGUF does not pretend that inference is already active.</p></Modal>}
 {panel==="memory"&&<Modal title="CHRONAL MEMORY" close={()=>setPanel(null)}><p className="muted">Explicitly stored facts remain on this device.</p>{kernel.memories.map((m,i)=><div className="memory" key={i}>{m}<button onClick={()=>setKernel(k=>({...k,memories:k.memories.filter((_,j)=>j!==i)}))}>×</button></div>)}<button className="primary" onClick={addMemory}>+ Add memory</button></Modal>}
 {panel==="evolution"&&<Modal title="EVOLUTION LEDGER" close={()=>setPanel(null)}><p className="muted">Changes made by the self-improvement layer.</p>{kernel.evolution.ledger.map(x=><div className="ledger" key={x.id}><b>G{x.generation}</b><span>{new Date(x.at).toLocaleString()}</span><p>{x.summary}</p></div>)}{!kernel.evolution.ledger.length&&<p className="muted">No evolutions yet.</p>}<button className="wind primary" onClick={rollback}>↶ Wind Up the Clock</button></Modal>}</div>
}
function Modal({title,close,children}){return <div className="overlay" onClick={close}><div className="modal" onClick={e=>e.stopPropagation()}><div className="modalhead"><h2>{title}</h2><button onClick={close}>×</button></div>{children}</div></div>}
createRoot(document.getElementById("root")).render(<App/>);