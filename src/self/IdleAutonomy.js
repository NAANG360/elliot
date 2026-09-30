export class IdleAutonomy{
 constructor({engine,observe,think,intervalMs=30000,onEvent=()=>{}}){this.engine=engine;this.observe=observe;this.think=think;this.intervalMs=intervalMs;this.onEvent=onEvent;this.timer=null;this.busy=false;this.lastInteraction=Date.now()}
 start(){if(this.timer)return;this.timer=setInterval(()=>this.tick(),this.intervalMs);this.onEvent({type:"idle-started"})}
 stop(){if(this.timer){clearInterval(this.timer);this.timer=null}this.onEvent({type:"idle-stopped"})}
 async tick(){
  if(this.busy||document.visibilityState==="visible"&&Date.now()-this.lastInteraction<90000)return;
  this.busy=true;
  try{const context=await Promise.resolve(this.observe());const idea=await Promise.resolve(this.think(context));if(idea)this.onEvent({type:"proposal",proposal:this.engine.propose(idea)})}
  catch(error){this.onEvent({type:"idle-error",error:String(error?.message||error)})}
  finally{this.busy=false}
 }
 touch(){this.lastInteraction=Date.now()}
}
