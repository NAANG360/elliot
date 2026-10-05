const KEY="miss-minutes-chronal-memory-v1";
const now=()=>Date.now(), uid=()=>crypto.randomUUID();
const words=s=>String(s||"").toLowerCase().match(/[a-z0-9][a-z0-9_-]{2,}/g)||[];
const stop=new Set("the and for that this with have from your you are was were has had but not all can she her his our their they them into about what when where which would should could there here just very really then than also only some any my me i we it is to of in on a an as at be do did does how why or if so".split(" "));
const terms=s=>[...new Set(words(s).filter(x=>!stop.has(x)))].slice(0,24);
const hash=s=>{let h=2166136261;for(const c of String(s)){h^=c.charCodeAt(0);h=Math.imul(h,16777619)}return(h>>>0).toString(16)};
const empty=()=>({version:1,working:[],episodes:[],nodes:{},edges:{},events:[],lastConsolidated:0});
const load=()=>{try{const x=JSON.parse(localStorage.getItem(KEY)||"null");return x&&x.version===1?x:empty()}catch{return empty()}};
const save=m=>{try{localStorage.setItem(KEY,JSON.stringify(m))}catch(e){console.warn("memory save failed",e)}};
const nodeId=x=>"n_"+hash(String(x).trim().toLowerCase());
const edgeId=(a,b,r)=>"e_"+hash(a+"|"+r+"|"+b);
const clean=x=>JSON.parse(JSON.stringify(x));
function node(m,label,type="concept",importance=.25){
 const id=nodeId(label),n=m.nodes[id];
 m.nodes[id]=n?{...n,importance:Math.max(n.importance||0,importance),lastReferenced:now(),references:(n.references||0)+1}:{id,label:String(label).trim(),type,importance,confidence:.65,createdAt:now(),lastReferenced:now(),references:1};
 return id;
}
function edge(m,a,b,relation,weight=.2){
 if(!a||!b||a===b)return;
 const id=edgeId(a,b,relation),e=m.edges[id];
 m.edges[id]=e?{...e,weight:Math.min(1,e.weight+weight),lastReferenced:now(),uses:e.uses+1}:{id,from:a,to:b,relation,weight,lastReferenced:now(),uses:1};
}
function event(m,type,payload){m.events.unshift({id:uid(),type,at:now(),payload:clean(payload)});m.events=m.events.slice(0,300)}
function observe(m,text,source="conversation",importance=.3){
 const raw=String(text||"").trim();if(!raw)return;
 const ts=now(),ws=terms(raw),trace={id:uid(),text:raw,terms:ws,createdAt:ts,lastReferenced:ts,energy:Math.min(1,importance+.18),source};
 m.working.push(trace);m.working=m.working.slice(-32);
 const ids=ws.slice(0,12).map(x=>node(m,x,"concept",importance));
 for(let i=0;i<ids.length;i++)for(let j=i+1;j<ids.length;j++)edge(m,ids[i],ids[j],"associated",.04);
 event(m,"trace",{source,terms:ws.slice(0,12),text:raw.slice(0,500)});
}
function consolidate(m){
 const t=now();
 for(const x of m.working)x.energy*=Math.exp(-(t-x.lastReferenced)/900000);
 const live=m.working.filter(x=>x.energy>.08);
 if(live.length>=6){
  const groups=new Map();
  for(const x of live)for(const w of x.terms){if(!groups.has(w))groups.set(w,[]);groups.get(w).push(x)}
  for(const [w,xs] of groups){
   const id=node(m,w,"concept",Math.min(1,.25+xs.length*.08));
   if(xs.length>=3){
    m.episodes.unshift({id:uid(),summary:xs.slice(-5).map(x=>x.text).join(" | ").slice(0,900),terms:[w,...terms(xs.map(x=>x.text).join(" ")).filter(x=>x!==w).slice(0,8)],createdAt:t,importance:Math.min(1,.45+xs.length*.07)});
    m.episodes=m.episodes.slice(0,120);event(m,"consolidation",{node:id,count:xs.length});
   }
  }
  m.working=live.slice(-24);m.lastConsolidated=t;
 }
 m.working=m.working.filter(x=>x.energy>.12);
 for(const [id,n] of Object.entries(m.nodes)){const connected=Object.values(m.edges).some(e=>(e.from===id||e.to===id)&&e.weight>.08);if(!connected&&n.importance<.55&&(t-n.lastReferenced)>604800000)delete m.nodes[id]}
 for(const [id,e] of Object.entries(m.edges)){e.weight*=Math.pow(.97,Math.max(0,(t-e.lastReferenced)/86400000));if(e.weight<.025)delete m.edges[id]}
 save(m);
}
function recall(m,query,limit=12){
 const q=terms(query),t=now(),rank=[];
 for(const n of Object.values(m.nodes)){
  const overlap=q.filter(x=>n.label.toLowerCase().includes(x)||x.includes(n.label.toLowerCase())).length;if(!overlap)continue;
  const rec=Math.exp(-((t-n.lastReferenced)/86400000)/30);
  const conn=Object.values(m.edges).filter(e=>e.from===n.id||e.to===n.id).reduce((s,e)=>s+e.weight,0);
  rank.push({n,score:overlap*2+n.importance*1.5+rec*.7+Math.min(2,conn)});
 }
 const nodes=rank.sort((a,b)=>b.score-a.score).slice(0,limit).map(x=>x.n);
 const episodes=m.episodes.filter(e=>q.some(x=>e.terms.includes(x))).slice(0,limit);
 nodes.forEach(n=>n.lastReferenced=t);save(m);
 return [nodes.length?"CONNECTED CONCEPTS: "+nodes.map(n=>n.label).join(", "):"",episodes.length?"CONSOLIDATED EXPERIENCES: "+episodes.map(e=>e.summary).join(" || "):""].filter(Boolean).join("\n")||"none";
}
export class ChronalMemory{
 constructor(){this.memory=load()}
 observe(text,source="conversation",importance=.3){observe(this.memory,text,source,importance);if(this.memory.working.length%6===0)consolidate(this.memory);else save(this.memory)}
 consolidate(){consolidate(this.memory)}
 recall(query,limit=12){return recall(this.memory,query,limit)}
 remember(text,importance=.95){observe(this.memory,text,"explicit",importance);const ids=terms(text).map(x=>node(this.memory,x,"explicit",importance));for(let i=1;i<ids.length;i++)edge(this.memory,ids[0],ids[i],"supports",.18);consolidate(this.memory)}
 stats(){return{working:this.memory.working.length,episodes:this.memory.episodes.length,nodes:Object.keys(this.memory.nodes).length,edges:Object.keys(this.memory.edges).length,events:this.memory.events.length}}
}