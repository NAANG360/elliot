export function localBrain({text,messages,kernel}){
  const bridge=globalThis.MissMinutesNative;
  const system=[
    "You are Miss Minutes, an original fan-made chronal assistant.",
    kernel.persona.tone,
    ...kernel.persona.rules,
    "You have local access to the editable Miss Minutes framework.",
    "Before changing framework state, create a checkpoint.",
    "Use Wind Up the Clock when a previous checkpoint must be restored.",
    "Never claim to be official Marvel/TVA software.",
    "Do not reproduce dialogue from the show verbatim.",
    "Local memories: "+kernel.memories.join(" | ")
  ].join("\n");
  if(bridge?.generateLocal){
    try{
      const out=bridge.generateLocal(JSON.stringify({system,text,messages:messages.slice(-12)}));
      if(out) return Promise.resolve(out);
    }catch{}
  }
  const q=text.toLowerCase();
  if(q.includes("time")) return Promise.resolve("Well, sugar, it is "+new Date().toLocaleTimeString()+".");
  if(q.includes("who are you")) return Promise.resolve("I'm Miss Minutes — your little chronal companion. My framework is local, and I've got room to grow.");
  return Promise.resolve("My local model adapter is standing by. Give me a concrete task, sugar.");
}
