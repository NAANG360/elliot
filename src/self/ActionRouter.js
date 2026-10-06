const ACTIONS=Object.freeze(["web_search","open_url","remember","forget_memory","request_camera","request_microphone","capability_goal","self_improve"]);

export class ActionRouter{
 constructor({bridge,memory,onCapability,onSelfImprove,onMemoryChange}={}){
  this.bridge=bridge||globalThis.MissMinutesNative;
  this.memory=memory;
  this.onCapability=onCapability;
  this.onSelfImprove=onSelfImprove;
  this.onMemoryChange=onMemoryChange;
 }
 async run(actions=[]){
  const list=Array.isArray(actions)?actions.slice(0,4):[];
  const results=[];
  for(const raw of list){
   const action=raw&&typeof raw==="object"?raw:{};
   const type=String(action.type||"").trim();
   if(!ACTIONS.includes(type)){results.push({type,status:"rejected",result:"Unknown action."});continue}
   try{
    if(type==="web_search"){
     const query=String(action.query||"").trim().slice(0,240);
     if(!query)throw new Error("Missing search query");
     const data=await this.search(query);
     results.push({type,status:"done",query,result:data});
    }else if(type==="open_url"){
     const url=String(action.url||"").trim();
     if(!/^https?:\\/\\//i.test(url))throw new Error("Only http/https URLs may be opened.");
     const ok=Boolean(this.bridge?.openUrl?.(url));
     results.push({type,status:ok?"done":"failed",result:ok?"Opened the URL on the device.":"The device could not open that URL."});
    }else if(type==="remember"){
     const fact=String(action.text||action.fact||"").trim().slice(0,1000);
     if(!fact)throw new Error("Missing memory text");
     this.memory?.remember(fact,.98);
     this.onMemoryChange?.();
     results.push({type,status:"done",result:"Saved to local chronal memory."});
    }else if(type==="forget_memory"){
     const needle=String(action.text||action.fact||"").trim().toLowerCase();
     if(!needle)throw new Error("Missing memory text");
     const memories=this.memory?.recall(needle,32)||"";
     results.push({type,status:"done",result:"Memory retrieval completed. Exact deletion is handled by the local memory UI.",matches:memories});
    }else if(type==="request_camera"||type==="request_microphone"){
     const kind=type==="request_camera"?"camera":"microphone";
     const ok=Boolean(this.bridge?.requestMediaAccess?.(kind));
     results.push({type,status:ok?"done":"awaiting-user",result:ok?kind+" access is already granted or was accepted.":"Android is waiting for the user's permission decision."});
    }else if(type==="capability_goal"){
     const goal=String(action.goal||"").trim().slice(0,500);
     if(!goal)throw new Error("Missing capability goal");
     const item=this.onCapability?.(goal);
     results.push({type,status:"queued",result:"Capability work has been queued.",goal,id:item?.id||null});
    }else if(type==="self_improve"){
     const item=this.onSelfImprove?.();
     results.push({type,status:"done",result:item||"Self-improvement cycle completed."});
    }
   }catch(e){results.push({type,status:"failed",result:String(e?.message||e)})}
  }
  return results;
 }
 search(query){
  return new Promise((resolve,reject)=>{
   const id=this.bridge?.startWebSearch?.(query);
   if(!id){reject(new Error("Web search is unavailable."));return}
   const poll=()=>{
    let packet="";
    try{packet=this.bridge.pollWebSearch(id)}catch(e){reject(e);return}
    const cut=packet.indexOf("\\n");
    const status=cut<0?packet:packet.slice(0,cut);
    const body=cut<0?"":packet.slice(cut+1);
    if(status==="RUNNING"){setTimeout(poll,120);return}
    if(status!=="DONE"){reject(new Error(body||"Web search failed."));return}
    try{resolve(JSON.parse(body))}catch{resolve(body)}
   };
   poll();
  });
 }
}
