import React,{useEffect,useRef}from"react";
import*as THREE from"three";

const C={
 orange:0xf47a19,
 orangeDark:0x9b3b0c,
 outline:0x2a1208,
 white:0xfff7df,
 black:0x23100a,
 boot:0xe96712,
 glow:0xff9a2e
};

function mat(color,roughness=.75){return new THREE.MeshStandardMaterial({color,roughness,metalness:0});}
function cyl(radius,height,material,segments=48){return new THREE.Mesh(new THREE.CylinderGeometry(radius,radius,height,segments),material)}
function capsule(radius,length,material){return new THREE.Mesh(new THREE.CapsuleGeometry(radius,length,8,16),material)}

export default function MissMinutes3D({mode="idle"}){
 const mount=useRef(null),state=useRef({mode,targetX:0,targetY:0,blinking:false});
 useEffect(()=>{state.current.mode=mode},[mode]);

 useEffect(()=>{
  const el=mount.current;if(!el)return;
  const scene=new THREE.Scene();
  const camera=new THREE.PerspectiveCamera(27,1,.1,100);
  camera.position.set(0,.08,7.15);camera.lookAt(0,.05,0);

  const renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:"high-performance"});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.8));
  renderer.setSize(el.clientWidth,el.clientHeight,false);
  renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure=1.12;
  el.appendChild(renderer.domElement);

  const root=new THREE.Group();
  root.position.y=.12;
  scene.add(root);

  const clockGroup=new THREE.Group();
  clockGroup.position.y=.62;
  root.add(clockGroup);

  const orange=mat(C.orange,.72),edge=mat(C.orangeDark,.8),dark=mat(C.outline,.9);
  const white=mat(C.white,.62),bootMat=mat(C.boot,.7);

  // The face is intentionally thick and slightly bevelled rather than a flat disk.
  const body=new THREE.Mesh(new THREE.CylinderGeometry(1.36,1.36,.26,96),orange);
  body.rotation.x=Math.PI/2;
  clockGroup.add(body);

  const rearRim=new THREE.Mesh(new THREE.TorusGeometry(1.28,.095,12,96),edge);
  rearRim.rotation.x=Math.PI/2;rearRim.position.z=-.11;clockGroup.add(rearRim);

  const frontRim=new THREE.Mesh(new THREE.TorusGeometry(1.285,.045,10,96),edge);
  frontRim.rotation.x=Math.PI/2;frontRim.position.z=.145;clockGroup.add(frontRim);

  // Sparse, bold clock ticks matching the original silhouette.
  const ticks=new THREE.Group();clockGroup.add(ticks);
  for(let i=0;i<12;i++){
   const a=i*Math.PI/6;
   const major=i%3===0;
   const r=1.085;
   const t=new THREE.Mesh(
    new THREE.BoxGeometry(major?.075:.043,major?.26:.17,.035),
    dark
   );
   t.position.set(Math.sin(a)*r,.0,Math.cos(a)*r+.16);
   t.rotation.z=-a;
   ticks.add(t);
  }

  // Expressive eyes: white almond-ish ovals, dark pupils, and a proper lash line.
  const eyeL=new THREE.Mesh(new THREE.SphereGeometry(.34,40,28),white);
  const eyeR=eyeL.clone();
  eyeL.scale.set(.63,1.22,.34);eyeR.scale.copy(eyeL.scale);
  eyeL.position.set(-.47,.27,.34);eyeR.position.set(.47,.27,.34);
  clockGroup.add(eyeL,eyeR);

  const pupilL=new THREE.Mesh(new THREE.SphereGeometry(.145,28,20),dark);
  const pupilR=pupilL.clone();
  pupilL.scale.set(.62,1.32,.45);pupilR.scale.copy(pupilL.scale);
  pupilL.position.set(-.47,.26,.60);pupilR.position.set(.47,.26,.60);
  clockGroup.add(pupilL,pupilR);

  const lashes=new THREE.Group();
  for(const side of[-1,1]){
   for(let i=0;i<5;i++){
    const lash=capsule(.014,.10,dark);
    lash.rotation.z=side*(.48-i*.16);
    lash.position.set(side*(.30+i*.065),.59,.59);
    lashes.add(lash);
   }
  }
  clockGroup.add(lashes);

  const nose=new THREE.Mesh(new THREE.SphereGeometry(.057,20,14),dark);
  nose.position.set(0,-.02,.61);clockGroup.add(nose);

  const mouth=new THREE.Mesh(new THREE.TorusGeometry(.235,.032,10,36,Math.PI),dark);
  mouth.position.set(0,-.42,.57);mouth.rotation.z=Math.PI;clockGroup.add(mouth);
  const mouthInner=new THREE.Mesh(new THREE.CircleGeometry(.15,32),dark);
  mouthInner.position.set(0,-.42,.555);mouthInner.scale.y=.18;clockGroup.add(mouthInner);

  // Thin orange arms with the characteristic dark cuff stripes.
  const makeArm=side=>{
   const g=new THREE.Group();
   g.position.set(side*1.34,.18,.02);
   const upper=capsule(.073,.47,orange);
   upper.rotation.z=-side*.66;upper.position.x=side*.20;g.add(upper);

   const cuff=cyl(.082,.19,dark,20);
   cuff.rotation.z=Math.PI/2;
   cuff.position.set(side*.40,-.02,.0);
   g.add(cuff);

   for(let i=0;i<3;i++){
    const stripe=cyl(.088,.018,edge,20);
    stripe.rotation.z=Math.PI/2;
    stripe.position.set(side*(.34+i*.055),-.02,.01);
    g.add(stripe);
   }

   const palm=new THREE.Mesh(new THREE.SphereGeometry(.20,24,16),white);
   palm.scale.set(.78,.9,1.12);
   palm.position.set(side*.54,-.04,.05);
   g.add(palm);

   // Five soft fingers so the hands read as hands, not blobs.
   for(let i=0;i<5;i++){
    const finger=capsule(.042,.12,white);
    const fan=(i-2)*.16;
    finger.rotation.z=side*(.30+fan);
    finger.position.set(side*(.56+Math.abs(i-2)*.025),-.16+(2-i)*.045,.06);
    g.add(finger);
   }
   return g;
  };
  const armL=makeArm(-1),armR=makeArm(1);
  root.add(armL,armR);

  const makeLeg=side=>{
   const g=new THREE.Group();
   g.position.set(side*.29,-.83,0);
   g.add(capsule(.055,.55,dark));
   const ankle=capsule(.09,.10,bootMat);
   ankle.position.set(0,-.34,.04);g.add(ankle);

   // Rounded orange boots, with a small dark sole.
   const boot=new THREE.Mesh(new THREE.SphereGeometry(.25,24,16),bootMat);
   boot.scale.set(1.05,.48,1.30);
   boot.position.set(side*.045,-.42,.10);g.add(boot);
   const sole=new THREE.Mesh(new THREE.BoxGeometry(.34,.045,.40),dark);
   sole.position.set(side*.045,-.53,.10);g.add(sole);
   return g;
  };
  root.add(makeLeg(-1),makeLeg(1));

  // Keep the holographic treatment behind the character, not through it.
  const haloMat=new THREE.MeshBasicMaterial({color:C.glow,transparent:true,opacity:.10,side:THREE.DoubleSide,blending:THREE.AdditiveBlending,depthWrite:false});
  const halo=new THREE.Mesh(new THREE.TorusGeometry(1.62,.025,8,96),haloMat);
  halo.rotation.x=Math.PI/2;halo.position.y=.62;root.add(halo);

  const scan=new THREE.Mesh(
   new THREE.RingGeometry(1.48,1.64,96),
   new THREE.MeshBasicMaterial({color:C.glow,transparent:true,opacity:.055,side:THREE.DoubleSide,blending:THREE.AdditiveBlending,depthWrite:false})
  );
  scan.rotation.x=Math.PI/2;scan.position.y=.62;root.add(scan);

  const particles=new THREE.Group();root.add(particles);
  const pMat=new THREE.MeshBasicMaterial({color:C.glow,transparent:true,opacity:.45});
  for(let i=0;i<28;i++){
   const p=new THREE.Mesh(new THREE.SphereGeometry(.008+Math.random()*.014,6,6),pMat);
   p.position.set((Math.random()-.5)*3.5,(Math.random()-.5)*3.8+.4,(Math.random()-.5)*1.1);
   particles.add(p);
  }

  scene.add(new THREE.HemisphereLight(0xffd7a5,0x1a0803,2.0));
  const key=new THREE.PointLight(0xff9a38,5.2,8);key.position.set(0,1.2,3.5);scene.add(key);
  const fill=new THREE.PointLight(0xff5c1c,1.7,7);fill.position.set(-2,-.2,1.5);scene.add(fill);

  let raf=0,last=performance.now(),blinkAt=last+1500,blinkEnd=0;
  const resize=()=>{
   const w=Math.max(1,el.clientWidth),h=Math.max(1,el.clientHeight);
   camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h,false);
  };
  const move=e=>{
   const r=el.getBoundingClientRect();
   state.current.targetX=((e.clientX-r.left)/r.width-.5)*.42;
   state.current.targetY=-((e.clientY-r.top)/r.height-.5)*.28;
  };
  const leave=()=>{state.current.targetX=0;state.current.targetY=0};
  addEventListener("resize",resize);el.addEventListener("pointermove",move);el.addEventListener("pointerleave",leave);

  const frame=t=>{
   const s=state.current,dt=Math.min(.04,(t-last)/1000);last=t;
   if(!s.blinking&&t>=blinkAt){s.blinking=true;blinkEnd=t+115}
   if(s.blinking&&t>=blinkEnd){s.blinking=false;blinkAt=t+1800+Math.random()*2600}

   root.rotation.y+=(s.targetX-root.rotation.y)*dt*3.6;
   root.rotation.x+=(s.targetY*.6-root.rotation.x)*dt*3.6;
   root.position.y=.12+(s.mode==="thinking"?Math.sin(t*.006)*.055:Math.sin(t*.0021)*.018);

   const ey=s.blinking?.055:1;
   eyeL.scale.y=1.22*ey;eyeR.scale.y=1.22*ey;
   pupilL.scale.y=1.32*ey;pupilR.scale.y=1.32*ey;
   lashes.scale.y=ey;

   pupilL.position.x=-.47+s.targetX*.12;pupilR.position.x=.47+s.targetX*.12;
   pupilL.position.y=.26+s.targetY*.08;pupilR.position.y=.26+s.targetY*.08;

   mouth.scale.y=s.mode==="speaking"?1+Math.abs(Math.sin(t*.028))*.30:1;
   armL.rotation.z=-.12+(s.mode==="listening"?Math.sin(t*.008)*.12:Math.sin(t*.002)*.025);
   armR.rotation.z=.12+(s.mode==="speaking"?Math.sin(t*.009)*.18:Math.sin(t*.0023)*.025);
   halo.rotation.z=t*.00035;
   scan.scale.setScalar(1+Math.sin(t*.002)*.025);
   particles.rotation.y=t*.00010;

   renderer.render(scene,camera);
   raf=requestAnimationFrame(frame);
  };

  resize();raf=requestAnimationFrame(frame);
  return()=>{
   cancelAnimationFrame(raf);
   removeEventListener("resize",resize);
   el.removeEventListener("pointermove",move);el.removeEventListener("pointerleave",leave);
   renderer.dispose();el.innerHTML="";
  };
 },[]);

 return <div ref={mount} className="miss-3d"/>;
}
