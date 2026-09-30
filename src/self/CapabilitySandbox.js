const SAFE_KEYS=["clock","memory","sensors","ui","message","random"];

export class CapabilitySandbox{
 constructor(api={}){this.api=Object.fromEntries(SAFE_KEYS.filter(k=>api[k]!==undefined).map(k=>[k,api[k]]))}
 async run(capability,{input=null,timeoutMs=1500}={}){
  if(typeof capability?.run!=="function")throw new Error("Capability entrypoint is not executable");
  const started=performance.now();
  let timer;
  const timeout=new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error("Capability timed out")),timeoutMs)});
  try{return await Promise.race([Promise.resolve(capability.run({input,api:Object.freeze(this.api)})),timeout])}
  finally{clearTimeout(timer);if(performance.now()-started>timeoutMs)throw new Error("Capability timed out")}
 }
}
