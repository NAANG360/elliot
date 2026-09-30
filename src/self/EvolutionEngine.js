import {CapabilityRegistry,validateManifest} from "./CapabilityRegistry.js";

const clone=v=>JSON.parse(JSON.stringify(v));
const uid=()=>globalThis.crypto?.randomUUID?.()||("evo-"+Date.now()+"-"+Math.random().toString(16).slice(2));

export class EvolutionEngine{
 constructor({load,save,checkpoint,registry}){
  this.load=load;this.save=save;this.checkpoint=checkpoint;this.registry=registry||new CapabilityRegistry();
 }
 inspect(){const k=this.load();return{generation:k.evolution?.generation||0,capabilities:this.registry.list(),lastGood:k.evolution?.lastGood||null}}
 propose({summary,reason,patch=[],capability=null}={}){
  return{proposalId:uid(),createdAt:new Date().toISOString(),summary:summary||"Unspecified evolution",reason:reason||"self-directed experiment",patch:clone(patch),capability:capability?clone(capability):null};
 }
 commit(proposal,{test=()=>true}={}){
  const current=this.load();
  const before=this.checkpoint(clone(current),"before "+proposal.summary);
  try{
   if(proposal.capability){
    const valid=validateManifest(proposal.capability);
    if(!valid.ok)throw new Error(valid.errors.join(", "));
   }
   const candidate=clone(before);
   if(Array.isArray(proposal.patch)){
    for(const op of proposal.patch){
     if(!Array.isArray(op.path)||!op.path.length)continue;
     let obj=candidate;
     for(let i=0;i<op.path.length-1;i++){const key=op.path[i];if(!obj[key]||typeof obj[key]!=="object")obj[key]={};obj=obj[key]}
     const key=op.path[op.path.length-1];
     if(op.op==="set")obj[key]=clone(op.value);
     else if(op.op==="delete")delete obj[key];
    }
   }
   const result=test(candidate,proposal);
   if(result!==true&&result?.ok!==true)throw new Error(result?.error||"Evolution tests failed");
   if(proposal.capability)this.registry.install(proposal.capability);
   candidate.evolution=candidate.evolution||{generation:0,ledger:[],snapshots:[],lastGood:null};
   candidate.evolution.generation=(candidate.evolution.generation||0)+1;
   candidate.evolution.ledger=[{id:proposal.proposalId,at:new Date().toISOString(),generation:candidate.evolution.generation,summary:proposal.summary,reason:proposal.reason,kind:proposal.capability?"capability+framework":"framework",reversible:true},...(candidate.evolution.ledger||[])].slice(0,100);
   this.save(candidate);
   return{ok:true,kernel:candidate,proposal};
  }catch(error){
   this.save(before);
   return{ok:false,error:String(error?.message||error),rolledBack:true,kernel:before,proposal};
  }
 }
}
