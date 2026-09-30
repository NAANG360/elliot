export const CAPABILITY_STATES=Object.freeze(["installed","disabled","quarantined"]);

const clean=(v)=>JSON.parse(JSON.stringify(v));
const hashText=(text="")=>{
  let h=2166136261;
  for(let i=0;i<text.length;i++){h^=text.charCodeAt(i);h=Math.imul(h,16777619)}
  return (h>>>0).toString(16).padStart(8,"0");
};

export function validateManifest(m){
  const errors=[];
  if(!m||typeof m!=="object")return{ok:false,errors:["manifest must be an object"]};
  for(const key of ["id","name","version","description"])if(typeof m[key]!=="string"||!m[key].trim())errors.push(key+" is required");
  for(const key of ["permissions","dependencies","tests"])if(m[key]!==undefined&&!Array.isArray(m[key]))errors.push(key+" must be an array");
  if(m.status&&!CAPABILITY_STATES.includes(m.status))errors.push("invalid status");
  return{ok:!errors.length,errors};
}

export class CapabilityRegistry{
 constructor(initial=[]){this.items=new Map();for(const item of initial)this.install(item)}
 list(){return [...this.items.values()].map(clean)}
 get(id){return clean(this.items.get(id)||null)}
 install(manifest){
  const check=validateManifest(manifest);if(!check.ok)throw new Error("Invalid capability: "+check.errors.join(", "));
  const now=new Date().toISOString();
  const item={permissions:[],dependencies:[],tests:[],status:"installed",sourceHash:hashText(manifest.source||manifest.entrypoint||manifest.id),createdAt:now,updatedAt:now,entrypoint:"capability://"+manifest.id,...clean(manifest)};
  const old=this.items.get(item.id);if(old)item.createdAt=old.createdAt;
  this.items.set(item.id,item);return clean(item);
 }
 disable(id){const x=this.items.get(id);if(!x)return false;x.status="disabled";x.updatedAt=new Date().toISOString();return true}
 quarantine(id,reason="validation failure"){const x=this.items.get(id);if(!x)return false;x.status="quarantined";x.quarantineReason=reason;x.updatedAt=new Date().toISOString();return true}
 snapshot(){return this.list()}
}
