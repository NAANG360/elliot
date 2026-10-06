const KEY="miss-minutes-model-evolution-v2";
const clean=x=>JSON.parse(JSON.stringify(x));
const uid=()=>globalThis.crypto?.randomUUID?.()||("model-"+Date.now()+"-"+Math.random().toString(16).slice(2));

export const MODEL_LADDER=[
 {id:"mm-0.5b",label:"0.5B",parameters:0.5e9,model:"Qwen2.5-0.5B-Instruct",family:"Qwen2.5",quant:"Q4_K_M",approxFileMB:491,role:"starter brain",source:"https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",license:"Apache-2.0"},
 {id:"mm-1b",label:"~1B",parameters:1e9,model:"Llama-3.2-1B-Instruct",family:"Llama 3.2",quant:"Q4_K_M",approxFileMB:808,role:"intermediate growth brain",source:"https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct",license:"Llama 3.2 Community License"},
 {id:"mm-1.5b",label:"1.5B",parameters:1.5e9,model:"Qwen2.5-1.5B-Instruct",family:"Qwen2.5",quant:"Q4_K_M",approxFileMB:1120,role:"larger target brain",source:"https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",license:"Apache-2.0"}
];

const initial=()=>({currentStage:0,desiredStage:2,trainingGeneration:0,phase:"observe",jobs:[],datasets:[],evaluations:[],promotions:[],history:[],lastActionAt:0});
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||JSON.stringify(initial()))}catch{return initial()}};
const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch{}};

export class ModelEvolution{
 constructor(){this.state={...initial(),...load()};this.normalize()}
 normalize(){
  this.state.currentStage=Math.max(0,Math.min(2,Number(this.state.currentStage)||0));
  this.state.desiredStage=Math.max(this.state.currentStage,Math.min(2,Number(this.state.desiredStage)===0?0:Number(this.state.desiredStage)||2));
  for(const k of ["jobs","datasets","evaluations","promotions","history"])if(!Array.isArray(this.state[k]))this.state[k]=[];
  if(!this.state.phase)this.state.phase="observe";
  save(this.state);
 }
 get stage(){return MODEL_LADDER[this.state.currentStage]}
 get nextStage(){return MODEL_LADDER[Math.min(2,this.state.currentStage+1)]}
 snapshot(){return clean({...this.state,current:this.stage,next:this.nextStage})}

 autonomousCycle(context={}){
  const now=Date.now();
  if(now-(this.state.lastActionAt||0)<60000)return null;
  this.state.lastActionAt=now;
  if(this.state.currentStage>=this.state.desiredStage){this.state.phase="observe";save(this.state);return null}
  const target=this.nextStage;
  const open=this.state.jobs.find(j=>j.target===target.id&&j.status==="active");
  if(open){save(this.state);return open}
  const phases=["collect","prepare","train","evaluate","stage"];
  const phaseIndex=Math.max(0,phases.indexOf(this.state.phase));
  const next=phases[(phaseIndex+1)%phases.length];
  const job={id:uid(),at:now,target:target.id,phase:this.state.phase,status:"active",silent:true,context:{
   localBrainReady:Boolean(context.localBrainReady),
   conversationSamples:Math.max(0,Number(context.conversationSamples)||0),
   memorySignals:Math.max(0,Number(context.memorySignals)||0)
  }};
  this.state.jobs.unshift(job);
  this.state.jobs=this.state.jobs.slice(0,30);
  this.state.history.unshift({id:uid(),at:now,event:"autonomous-growth-step",target:target.id,phase:this.state.phase,nextPhase:next});
  this.state.history=this.state.history.slice(0,120);
  this.state.phase=next;
  save(this.state);
  return job;
 }

 setGoal(i){
  this.state.desiredStage=Math.max(this.state.currentStage,Math.min(2,Number(i)||0));
  this.state.phase="observe";
  this.state.history.unshift({id:uid(),at:Date.now(),event:"growth-goal",to:MODEL_LADDER[this.state.desiredStage].id});
  this.state.history=this.state.history.slice(0,120);save(this.state);return this.snapshot();
 }

 researchPlan(){return{target:this.nextStage,principle:"Real capacity growth requires a new trained checkpoint.",steps:["Collect high-quality local behavior traces.","Prepare and validate a training corpus.","Train or fine-tune the next-size checkpoint outside the Android runtime.","Run behavior, capability, memory, latency, integrity, and Guardian-reachability evaluations.","Quantize the candidate and verify its artifact hash.","Stage it beside the known-good brain.","Promote only after the evaluation gate passes; retain rollback."]}}

 recordEvaluation(scores,artifact={}){
  const s=Object.fromEntries(Object.entries(scores||{}).map(([k,v])=>[k,Math.max(0,Math.min(1,Number(v)||0))]));
  const values=Object.values(s);
  const result={id:uid(),at:Date.now(),stage:this.nextStage.id,scores:s,average:values.length?values.reduce((a,b)=>a+b,0)/values.length:0,artifact:clean(artifact)};
  this.state.evaluations.unshift(result);this.state.evaluations=this.state.evaluations.slice(0,100);save(this.state);return result;
 }

 canPromote(){
  const e=this.state.evaluations.find(x=>x.stage===this.nextStage.id);
  return Boolean(e&&e.average>=.78&&e.scores["behavior-regression"]>=.8&&e.scores["guardian-reachability"]>=.95);
 }

 promote(artifact={}){
  if(!this.canPromote())throw new Error("Growth gate has not passed yet.");
  const from=this.stage,to=this.nextStage;
  this.state.currentStage++;this.state.trainingGeneration++;this.state.phase="observe";
  this.state.promotions.unshift({id:uid(),at:Date.now(),from:from.id,to:to.id,artifact:clean(artifact)});
  this.state.promotions=this.state.promotions.slice(0,30);save(this.state);return this.snapshot();
 }
}