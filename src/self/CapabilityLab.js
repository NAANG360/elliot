const KEY="miss-minutes-capability-lab-v1";
const uid=()=>crypto.randomUUID();
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||'{"goals":[],"staged":[],"research":[]}')}catch{return{goals:[],staged:[],research:[]}}};
const save=x=>localStorage.setItem(KEY,JSON.stringify(x));
const clean=x=>JSON.parse(JSON.stringify(x));
export class CapabilityLab{
 constructor(){this.state=load()}
 addGoal(goal){const g={id:uid(),goal:String(goal).trim(),createdAt:Date.now(),status:"queued"};if(!g.goal)return null;this.state.goals.unshift(g);this.state.goals=this.state.goals.slice(0,40);save(this.state);return g}
 pending(){return this.state.goals.filter(g=>g.status==="queued")}
 async workOne({search=globalThis.MissMinutesNative?.webSearch}={}){
  const goal=this.pending()[0];if(!goal||typeof search!=="function")return null;
  goal.status="researching";save(this.state);
  try{
   const q="Android "+goal.goal+" API library implementation documentation";
   const raw=await Promise.resolve(search(q));
   let results=[];try{results=JSON.parse(raw)}catch{}
   const evidence=Array.isArray(results)?results.slice(0,6):[];
   const staged={id:uid(),goalId:goal.id,title:this.title(goal.goal),createdAt:Date.now(),status:"awaiting-user",goal:goal.goal,evidence,blueprint:this.blueprint(goal.goal,evidence),requiresUserApproval:true};
   this.state.staged.unshift(staged);this.state.staged=this.state.staged.slice(0,20);
   goal.status="staged";goal.stagedId=staged.id;goal.lastResearchAt=Date.now();
   this.state.research.unshift({id:uid(),goalId:goal.id,query:q,at:Date.now(),evidence});this.state.research=this.state.research.slice(0,80);
   save(this.state);return staged;
  }catch(e){goal.status="queued";goal.error=String(e?.message||e);save(this.state);return null}
 }
 blueprint(goal,evidence){
  const lower=goal.toLowerCase();
  if(lower.includes("shizuku"))return{kind:"android-privileged-integration",summary:"Prepare Shizuku integration and request Shizuku permission only after explicit user approval.",dependencies:["Shizuku-API"],permissions:["moe.shizuku.manager.permission.API_V23"],steps:["Add the maintained Shizuku API dependency.","Add the provider metadata required by the API.","Detect whether Shizuku is running.","Check Miss Minutes' Shizuku permission.","If missing, show a clear approval step and then request Shizuku permission.","Run capability-specific operations only through the approved API surface."],tests:["Build without Shizuku present.","Run with Shizuku absent.","Run with Shizuku present but permission denied.","Run with permission granted.","Verify Guardian controls remain reachable."],evidence:evidence.map(x=>({title:x.title,url:x.url}))};
  return{kind:"capability-blueprint",summary:"Research and stage a user-approved capability; no privileged action is performed automatically.",dependencies:[],permissions:[],steps:["Validate the requirement.","Research official APIs and maintained libraries.","Build inside the capability sandbox.","Run deterministic tests.","Stage the capability for user approval.","Install only after approval."],tests:["Missing dependency","Permission denied","Happy path","Rollback"],evidence:evidence.map(x=>({title:x.title,url:x.url}))};
 }
 title(goal){const x=String(goal).trim();return x.length>48?x.slice(0,48)+"…":x}
 staged(){return this.state.staged.filter(x=>x.status==="awaiting-user")}
 approve(id){const x=this.state.staged.find(x=>x.id===id);if(!x)return null;x.status="approved";x.approvedAt=Date.now();save(this.state);return clean(x)}
 reject(id){const x=this.state.staged.find(x=>x.id===id);if(!x)return null;x.status="rejected";x.rejectedAt=Date.now();save(this.state);return clean(x)}
 stats(){return{queued:this.pending().length,awaitingApproval:this.staged().length,research:this.state.research.length}}
}