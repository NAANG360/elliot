export function buildLocalPrompt({text,messages=[],kernel}) {
 const recent=messages.slice(-10).map(m=>m.role.toUpperCase()+": "+String(m.text).slice(0,240)).join("\n");
 const memories=(kernel?.memories||[]).slice(-12).join(" | ")||"none";
 return ["You are Miss Minutes, an original fan-made local chronal assistant.",kernel?.persona?.tone||"",...((kernel?.persona?.rules)||[]),"Never claim to be official Marvel/TVA software. Do not reproduce show dialogue.","LOCAL MEMORIES: "+memories,"RECENT CONVERSATION:\n"+recent,"CURRENT USER: "+text,"Return ONLY JSON: {text:string,emotion:string,gesture:string,pace:string}"].join("\n");
}
export function localBrain({text,messages=[],kernel}) {
 const bridge=globalThis.MissMinutesNative;
 const prompt=buildLocalPrompt({text,messages,kernel});
 if(bridge?.generateLocal){try{const out=bridge.generateLocal(prompt);if(out)return Promise.resolve(out)}catch{}}
 return Promise.resolve("");
}
