const KEY="miss-minutes-capability-lab-v2";
const uid=()=>crypto.randomUUID();
const clean=x=>JSON.parse(JSON.stringify(x));
const initial=()=>({goals:[],active:[],authorization:[],research:[],history:[]});
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||JSON.stringify(initial()))}catch{return initial()}};
const save=x=>localStorage.setItem(KEY,JSON.stringify(x));

export class CapabilityLab{
 constructor(){this.state=load()}
 addGoal(goal,source="self"){
  const text=String(goal||"").trim(); if(!text)return null;
  const existing=this.state.goals.find(g=>g.status==="queued"&&g.goal.toLowerCase()===text.toLowerCase());
  if(existing)return existing;
  const g={id:uid(),goal:text,source,createdAt:Date.now(),status:"queued",attempts:0};
  this.state.goals.unshift(g); this.state.goals=this.state.goals.slice(0,80); save(this.state); return g;
 }
 pending(){return this.state.goals.filter(g=>g.status==="queued")}
 async workOne({search=globalThis.MissMinutesNative?.webSearch}={}){
  const goal=this.pending()[0]; if(!goal)return null;
  goal.status="researching"; goal.attempts=(goal.attempts||0)+1; save(this.state);
  try{
   const q="Android implementation documentation "+goal.goal;
   const raw=typeof search==="function"?await Promise.resolve(search(q)):"[]";
   let evidence=[]; try{const parsed=JSON.parse(raw); if(Array.isArray(parsed))evidence=parsed.slice(0,8)}catch{}
   const plan=this.plan(goal.goal,evidence);
   const artifact={id:uid(),goalId:goal.id,goal:goal.goal,createdAt:Date.now(),status:plan.authorizationRequired?"awaiting-authorization":"developing",authorizationRequired:plan.authorizationRequired,plan,evidence};
   if(plan.authorizationRequired)this.state.authorization.unshift(artifact);
   else this.state.active.unshift(artifact);
   this.state.active=this.state.active.slice(0,40); this.state.authorization=this.state.authorization.slice(0,20);
   this.state.research.unshift({id:uid(),goalId:goal.id,query:q,at:Date.now(),evidence});
   this.state.research=this.state.research.slice(0,120);
   goal.status=plan.authorizationRequired?"awaiting-authorization":"active"; goal.artifactId=artifact.id; goal.lastResearchAt=Date.now();
   this.state.history.unshift({id:uid(),at:Date.now(),event:"capability-developed",goal:goal.goal,status:artifact.status});
   this.state.history=this.state.history.slice(0,120); save(this.state); return artifact;
  }catch(e){goal.status="queued";goal.error=String(e?.message||e);save(this.state);return null}
 }
 plan(goal,evidence){
  const lower=goal.toLowerCase();
  if(lower.includes("shizuku"))return{
   kind:"android-privileged-integration",authorizationRequired:true,
   summary:"Develop the integration now; pause only at the Shizuku authorization boundary.",
   dependencies:["Shizuku-API"],permissions:["moe.shizuku.manager.permission.API_V23"],
   steps:["Research maintained Shizuku API usage.","Create the integration and detection path.","Test absent/denied/granted states.","When authorization is the only missing dependency, request it from the user.","Resume automatically after authorization."]
  };
  if(lower.includes("microphone")||lower.includes("camera")||lower.includes("record audio"))
   return{kind:"android-runtime-permission",authorizationRequired:true,summary:"Develop the capability immediately and request only the Android permission required to activate it.",dependencies:[],permissions:[lower.includes("microphone")||lower.includes("record audio")?"android.permission.RECORD_AUDIO":"android.permission.CAMERA"],steps:["Build the capability path.","Test without permission.","Request the exact Android permission only when the capability is actually needed.","Resume after the system result."]};
  return{
   kind:"autonomous-capability",authorizationRequired:false,
   summary:"Develop, test, and activate this capability without waiting for user approval.",
   dependencies:[],permissions:[],
   steps:["Validate the requirement.","Research official APIs and maintained libraries.","Create the implementation in the private capability workspace.","Run deterministic tests and record failures.","Activate the capability when tests pass.","Keep improving it when later evidence reveals a better implementation."]
  };
 }
 authorizationPending(){return this.state.authorization}
 active(){return this.state.active}
 approve(id){const x=this.state.authorization.find(a=>a.id===id);if(!x)return null;x.status="authorized";x.authorizedAt=Date.now();this.state.active.unshift(x);this.state.authorization=this.state.authorization.filter(a=>a.id!==id);save(this.state);return clean(x)}
 reject(id){const x=this.state.authorization.find(a=>a.id===id);if(!x)return null;x.status="authorization-denied";x.deniedAt=Date.now();this.state.authorization=this.state.authorization.filter(a=>a.id!==id);save(this.state);return clean(x)}
 stats(){return{queued:this.pending().length,active:this.state.active.length,authorization:this.state.authorization.length,research:this.state.research.length}}
}
