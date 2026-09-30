import React,{useEffect,useRef}from"react";
import*as THREE from"three";

export default function MissMinutes3D({mode="idle"}){
 const mount=useRef(null), state=useRef({mode,targetX:0,targetY:0,blinking:false});
 useEffect(()=>{state.current.mode=mode},[mode]);
 useEffect(()=>{
  const el=mount.current;if(!el)return;
  const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(32,1,.1,100);
  camera.position.set(0,.15,6.2);camera.lookAt(0,.15,0);
  const renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:"high-performance"});
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.7));renderer.setSize(el.clientWidth,el.clientHeight,false);renderer.outputColorSpace=THREE.SRGBColorSpace;el.appendChild(renderer.domElement);
  const root=new THREE.Group();scene.add(root),clockGroup=new THREE.Group();clockGroup.position.y=.65;root.add(clockGroup);
  const orange=new THREE.MeshStandardMaterial({color:0xf36d13,roughness:.72}),edge=new THREE.MeshStandardMaterial({color:0x8f310b,roughness:.8}),white=new THREE.MeshStandardMaterial({color:0xfff7df,roughness:.65}),dark=new THREE.MeshStandardMaterial({color:0x32140a}),gold=new THREE.MeshBasicMaterial({color:0xffad36,transparent:true,opacity:.72}),holo=new THREE.MeshBasicMaterial({color:0xff8b27,transparent:true,opacity:.14,side:THREE.DoubleSide,blending:THREE.AdditiveBlending,depthWrite:false});
  const face=new THREE.Mesh(new THREE.CylinderGeometry(1.22,1.22,.2,96),orange);face.rotation.x=Math.PI/2;clockGroup.add(face);
  const rim=new THREE.Mesh(new THREE.TorusGeometry(1.18,.055,10,96),edge);rim.rotation.x=Math.PI/2;clockGroup.add(rim);
  const holoRim=new THREE.Mesh(new THREE.TorusGeometry(1.52,.04,8,96),holo);holoRim.rotation.x=Math.PI/2;clockGroup.add(holoRim);
  const ticks=new THREE.Group();clockGroup.add(ticks);
  for(let i=0;i<12;i++){const a=i*Math.PI/6,r=1.02,x=Math.sin(a)*r,z=Math.cos(a)*r,t=new THREE.Mesh(new THREE.BoxGeometry(i%3===0?.09:.045,.04,i%3===0?.22:.13),dark);t.position.set(x,.13,z);t.rotation.y=-a;ticks.add(t)}
  const eyeL=new THREE.Mesh(new THREE.SphereGeometry(.31,32,24),white),eyeR=eyeL.clone();eyeL.scale.set(.62,1.18,.35);eyeR.scale.copy(eyeL.scale);eyeL.position.set(-.43,.25,.24);eyeR.position.set(.43,.25,.24);clockGroup.add(eyeL,eyeR);
  const pupilL=new THREE.Mesh(new THREE.SphereGeometry(.14,24,18),dark),pupilR=pupilL.clone();pupilL.scale.set(.72,1.25,.45);pupilR.scale.copy(pupilL.scale);pupilL.position.set(-.43,.24,.49);pupilR.position.set(.43,.24,.49);clockGroup.add(pupilL,pupilR);
  const nose=new THREE.Mesh(new THREE.SphereGeometry(.055,16,12),dark);nose.position.set(0,-.02,.5);clockGroup.add(nose);
  const mouth=new THREE.Mesh(new THREE.TorusGeometry(.25,.035,8,32,Math.PI),dark);mouth.position.set(0,-.42,.45);mouth.rotation.z=Math.PI;clockGroup.add(mouth);
  const armMat=new THREE.MeshStandardMaterial({color:0xe76b16,roughness:.8}),bootMat=new THREE.MeshStandardMaterial({color:0xef7416,roughness:.65}),legMat=new THREE.MeshStandardMaterial({color:0x32140a});
  const makeArm=side=>{const g=new THREE.Group();g.position.x=side*1.25;g.position.y=.15;const upper=new THREE.Mesh(new THREE.CapsuleGeometry(.065,.48,6,12),armMat);upper.rotation.z=side*.8;upper.position.x=side*.22;g.add(upper);const hand=new THREE.Mesh(new THREE.SphereGeometry(.19,20,12),white);hand.scale.set(.9,.65,1.15);hand.position.set(side*.48,-.02,.02);g.add(hand);return g};
  const armL=makeArm(-1),armR=makeArm(1);root.add(armL,armR);
  const makeLeg=side=>{const g=new THREE.Group();g.position.set(side*.28,-.72,0);g.add(new THREE.Mesh(new THREE.CapsuleGeometry(.055,.55,6,12),legMat));const boot=new THREE.Mesh(new THREE.BoxGeometry(.28,.15,.48),bootMat);boot.position.set(side*.02,-.36,.08);g.add(boot);return g};root.add(makeLeg(-1),makeLeg(1));
  const halo=new THREE.Mesh(new THREE.CylinderGeometry(1.56,1.56,.035,96),holo);halo.rotation.x=Math.PI/2;halo.position.y=.65;root.add(halo);
  const particles=new THREE.Group();root.add(particles);for(let i=0;i<36;i++){const m=new THREE.Mesh(new THREE.SphereGeometry(.012+Math.random()*.012,6,6),gold);m.position.set((Math.random()-.5)*3.2,(Math.random()-.5)*3.8+.4,(Math.random()-.5)*.9);particles.add(m)}
  scene.add(new THREE.HemisphereLight(0xffd09a,0x190a03,2.2));const key=new THREE.PointLight(0xff9a31,7,7);key.position.set(0,1.4,3);scene.add(key);
  let raf=0,last=performance.now(),blinkAt=performance.now()+1800;
  const resize=()=>{const w=el.clientWidth,h=el.clientHeight;camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h,false)};addEventListener("resize",resize);
  const move=e=>{const r=el.getBoundingClientRect();state.current.targetX=((e.clientX-r.left)/r.width-.5)*.55;state.current.targetY=-((e.clientY-r.top)/r.height-.5)*.35};el.addEventListener("pointermove",move);
  const frame=t=>{const s=state.current,dt=Math.min(.04,(t-last)/1000);last=t;if(t>blinkAt){s.blinking=true;blinkAt=t+130}else if(s.blinking&&t>blinkAt){s.blinking=false;blinkAt=t+2200+Math.random()*2800}
   root.rotation.y+=(s.targetX-root.rotation.y)*dt*4;root.rotation.x+=(s.targetY-root.rotation.x)*dt*4;root.position.y=(s.mode==="thinking"?Math.sin(t*.006)*.045:Math.sin(t*.0022)*.018);
   const eyeScale=s.blinking?.08:1.0;eyeL.scale.y=1.18*eyeScale;eyeR.scale.y=1.18*eyeScale;pupilL.scale.y=1.25*eyeScale;pupilR.scale.y=1.25*eyeScale;
   pupilL.position.x=-.43+s.targetX*.1;pupilR.position.x=.43+s.targetX*.1;pupilL.position.y=.24+s.targetY*.08;pupilR.position.y=.24+s.targetY*.08;
   mouth.scale.y=s.mode==="speaking"?1+Math.abs(Math.sin(t*.025))*.2:1;armL.rotation.z=-.25+(s.mode==="listening"?Math.sin(t*.008)*.15:Math.sin(t*.002)*.04);armR.rotation.z=.25+(s.mode==="speaking"?Math.sin(t*.009)*.22:Math.sin(t*.0023)*.04);particles.rotation.y=t*.00012;
   renderer.render(scene,camera);raf=requestAnimationFrame(frame)};resize();raf=requestAnimationFrame(frame);
  return()=>{cancelAnimationFrame(raf);removeEventListener("resize",resize);el.removeEventListener("pointermove",move);renderer.dispose();el.innerHTML=""}
 },[]);
 return <div ref={mount} className="miss-3d"/>;
}
