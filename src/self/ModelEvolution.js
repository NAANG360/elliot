const KEY="miss-minutes-model-evolution-v1";
const clean=x=>JSON.parse(JSON.stringify(x));
const uid=()=>globalThis.crypto?.randomUUID?.()||("model-"+Date.now()+"-"+Math.random().toString(16).slice(2));
export const MODEL_LADDER=[
 {id:"mm-0.5b",label:"0.5B",parameters:0.5e9,model:"Qwen2.5-0.5B-Instruct",family:"Qwen2.5",quant:"Q4_K_M",approxFileMB:491,role:"starter brain",source:"https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",license:"Apache-2.0"},
 {id:"mm-1b",label:"~1B",parameters:1e9,model:"Llama-3.2-1B-Instruct",family:"Llama 3.2",quant:"Q4_K_M",approxFileMB:808,role:"intermediate growth brain",source:"https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct",license:"Llama 3.2 Community License"},
 {id:"mm-1.5b",label:"1.5B",parameters:1.5e9,model:"Qwen2.5-1.5B-Instruct",family:"Qwen2.5",quant:"Q4_K_M",approxFileMB:1120,role:"first larger target",source:"https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",license:"Apache-2.0"}];
const initial=()=>({currentStage:0,desiredStage:1,trainingGeneration:0,datasets:[],evaluations:[],promotions:[],history:[],status:"starter"});
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||JSON.stringify(initial()))}catch{return initial()}};
const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch{}};
export class ModelEvolution{
 constructor(){this.state={...initial(),...load()};this.state.currentStage=Math.max(0,Math.min(2,Number(this.state.currentStage)||0));this.state.desiredStage=Math.max(this.state.currentStage,Math.min(2,Number(this.state.desiredStage)||0))}
 get stage(){return MODEL_LADDER[this.state.currentStage]}
 get nextStage(){return MODEL_LADDER[Math.min(2,this.state.currentStage+1)]}
 snapshot(){return clean({...this.state,current:this.stage,next:this.nextStage})}
 setGoal(i){this.state.desiredStage=Math.max(this.state.currentStage,Math.min(2,Number(i)||0));this.state.status=this.state.desiredStage>this.state.currentStage?"growing":"stable";this.state.history.unshift({id:uid(),at:Date.now(),event:"growth-goal",to:MODEL_LADDER[this.state.desiredStage].id});this.state.history=this.state.history.slice(0,100);save(this.state);return this.snapshot()}
 researchPlan(){return{target:this.nextStage,principle:"Real parameter growth requires a new trained checkpoint.",steps:["Collect and score Miss Minutes traces.","Train or fine-tune the next-size checkpoint outside Android.","Run behavior, capability, memory, latency, integrity, and Guardian-reachability tests.","Quantize to GGUF and verify its hash.","Stage the new brain beside the known-good brain.","Promote only after the evaluation gate passes; keep rollback available."]}}
 recordEvaluation(scores,artifact={}){const s=Object.fromEntries(Object.entries(scores||{}).map(([k,v])=>[k,Math.max(0,Math.min(1,Number(v)||0))]));const values=Object.values(s);const result={id:uid(),at:Date.now(),stage:this.nextStage.id,scores:s,average:values.length?values.reduce((a,b)=>a+b,0)/values.length:0,artifact:clean(artifact)};this.state.evaluations.unshift(result);save(this.state);return result}
 canPromote(){const e=this.state.evaluations.find(x=>x.stage===this.nextStage.id);return Boolean(e&&e.average>=.78&&e.scores["behavior-regression"]>=.8&&e.scores["guardian-reachability"]>=.95)}
 promote(artifact={}){if(!this.canPromote())throw new Error("Growth gate has not passed yet.");const from=this.stage,to=this.nextStage;this.state.currentStage++;this.state.trainingGeneration++;this.state.status=this.state.currentStage>=this.state.desiredStage?"stable":"growing";this.state.promotions.unshift({id:uid(),at:Date.now(),from:from.id,to:to.id,artifact:clean(artifact)});save(this.state);return this.snapshot()}
}