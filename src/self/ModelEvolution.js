const KEY="miss-minutes-model-evolution-v3";
const clean=x=>JSON.parse(JSON.stringify(x));
const uid=()=>globalThis.crypto?.randomUUID?.()||("adapt-"+Date.now()+"-"+Math.random().toString(16).slice(2));

/*
 * Miss Minutes never autonomously downloads or swaps to another model.
 * The installed local GGUF is the only brain this subsystem may work on.
 * "Growth" here means improving the behavior/training artifacts around that
 * existing brain, then evaluating and staging those changes for rollback.
 */
const initial=()=>({
 brainId:"installed-local-brain",
 brainPath:"",
 trainingGeneration:0,
 phase:"observe",
 jobs:[],
 datasets:[],
 evaluations:[],
 promotions:[],
 history:[],
 lastActionAt:0
});
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||JSON.stringify(initial()))}catch{return initial()}};
const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch{}};

export class ModelEvolution{
 constructor(){this.state={...initial(),...load()};this.normalize()}
 normalize(){
  this.state.brainId=String(this.state.brainId||"installed-local-brain");
  this.state.brainPath=String(this.state.brainPath||"");
  this.state.trainingGeneration=Math.max(0,Number(this.state.trainingGeneration)||0);
  for(const k of ["jobs","datasets","evaluations","promotions","history"])if(!Array.isArray(this.state[k]))this.state[k]=[];
  if(!this.state.phase)this.state.phase="observe";
  save(this.state);
 }
 snapshot(){return clean(this.state)}

 autonomousCycle(context={}){
  const now=Date.now();
  if(now-(this.state.lastActionAt||0)<60000)return null;
  this.state.lastActionAt=now;
  const phases=["collect","prepare","adapt","evaluate","stage"];
  const open=this.state.jobs.find(j=>j.brainId===this.state.brainId&&j.status==="active");
  if(open){save(this.state);return open}
  const phaseIndex=Math.max(0,phases.indexOf(this.state.phase));
  const next=phases[Math.min(phases.length-1,phaseIndex+1)];
  const job={
   id:uid(),at:now,brainId:this.state.brainId,phase:this.state.phase,status:"active",silent:true,
   context:{
    localBrainReady:Boolean(context.localBrainReady),
    localBrainPath:String(context.localBrainPath||this.state.brainPath||""),
    conversationSamples:Math.max(0,Number(context.conversationSamples)||0),
    memorySignals:Math.max(0,Number(context.memorySignals)||0)
   }
  };
  this.state.jobs.unshift(job);
  this.state.jobs=this.state.jobs.slice(0,30);
  this.state.history.unshift({
   id:uid(),at:now,event:"autonomous-brain-improvement",brainId:this.state.brainId,
   phase:this.state.phase,nextPhase:next
  });
  this.state.history=this.state.history.slice(0,120);
  this.state.phase=next;
  save(this.state);
  return job;
 }

 researchPlan(){
  return{
   target:this.state.brainId,
   principle:"Keep the existing local brain. Improve it through local behavior data, adaptation artifacts, evaluation, and reversible staging; never fetch a replacement model automatically.",
   steps:[
    "Collect high-quality local behavior traces.",
    "Prepare and validate an improvement corpus.",
    "Prepare an adaptation/fine-tuning job against the installed brain rather than downloading another base model.",
    "Run behavior, capability, memory, latency, integrity, and Guardian-reachability evaluations.",
    "Stage the resulting adaptation artifact beside the known-good state.",
    "Promote only after the evaluation gate passes; retain rollback."
   ]
  }
 }

 recordEvaluation(scores,artifact={}){
  const s=Object.fromEntries(Object.entries(scores||{}).map(([k,v])=>[k,Math.max(0,Math.min(1,Number(v)||0))]));
  const values=Object.values(s);
  const result={
   id:uid(),at:Date.now(),brainId:this.state.brainId,scores:s,
   average:values.length?values.reduce((a,b)=>a+b,0)/values.length:0,artifact:clean(artifact)
  };
  this.state.evaluations.unshift(result);
  this.state.evaluations=this.state.evaluations.slice(0,100);
  save(this.state);return result;
 }

 canPromote(){
  const e=this.state.evaluations.find(x=>x.brainId===this.state.brainId);
  return Boolean(e&&e.average>=.78&&e.scores["behavior-regression"]>=.8&&e.scores["guardian-reachability"]>=.95);
 }

 promote(artifact={}){
  if(!this.canPromote())throw new Error("Improvement gate has not passed yet.");
  const from=this.state.trainingGeneration;
  this.state.trainingGeneration++;
  this.state.phase="observe";
  this.state.promotions.unshift({
   id:uid(),at:Date.now(),brainId:this.state.brainId,
   fromGeneration:from,toGeneration:this.state.trainingGeneration,artifact:clean(artifact)
  });
  this.state.promotions=this.state.promotions.slice(0,30);
  save(this.state);return this.snapshot();
 }
}