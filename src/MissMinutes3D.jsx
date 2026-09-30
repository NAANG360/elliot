import React,{useEffect,useRef}from"react";
import*as THREE from"three";

const C={
 orange:0xf27a18,
 orangeHi:0xff9630,
 orangeDark:0x9a390b,
 ink:0x2a1107,
 cream:0xfff4d6,
 white:0xfffff4,
 holo:0xff9d38,
 holoSoft:0xffc06a
};

function standard(color,roughness=.72){return new THREE.MeshStandardMaterial({color,roughness,metalness:0});}
function basic(color,opacity=1,extra={}){return new THREE.MeshBasicMaterial({color,transparent:opacity<1,opacity,...extra});}
function capsule(radius,length,material){return new THREE.Mesh(new THREE.CapsuleGeometry(radius,length,8,16),material);}

function makeGlove(material){
  const g=new THREE.Group();
  const palm=new THREE.Mesh(new THREE.SphereGeometry(.17,20,14),material);
  palm.scale.set(.86,1.05,.72);g.add(palm);
  for(let i=0;i<5;i++){
    const finger=capsule(.035,.105,material);
    const fan=(i-2)*.17;
    finger.rotation.z=fan;
    finger.position.set(fan*.42,.125+Math.abs(i-2)*.018,.0);
    g.add(finger);
  }
  return g;
}

export default function MissMinutes3D({mode="idle",reaction={emotion:"neutral",gesture:"idle",pace:"normal"}}){
 const mount=useRef(null);
 const state=useRef({mode,reaction,targetX:0,targetY:0,blink:false,nextBlink:performance.now()+1700});
 useEffect(()=>{state.current.mode=mode},[mode]);
 useEffect(()=>{state.current.reaction=reaction||{}},[reaction]);

 useEffect(()=>{
  const el=mount.current;if(!el)return;
  const scene=new THREE.Scene();
  const camera=new THREE.OrthographicCamera(-2.15,2.15,2.55,-2.55,.1,30);
  camera.position.set(0,.05,8);camera.lookAt(0,.05,0);

  const renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:"high-performance"});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.7));
  renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure=1.08;
  el.appendChild(renderer.domElement);

  const root=new THREE.Group();root.position.y=.05;scene.add(root);
  const mascot=new THREE.Group();root.add(mascot);

  const orange=standard(C.orange,.78), orangeEdge=standard(C.orangeDark,.82);
  const ink=standard(C.ink,.88), cream=standard(C.cream,.58), white=standard(C.white,.55);
  const bootMat=standard(C.orangeHi,.65);

  // A shallow beveled clock-face slab: 2.5D silhouette first, depth second.
  const faceShape=new THREE.Shape();
  faceShape.absarc(0,0,1.42,0,Math.PI*2,false);
  const face=new THREE.Mesh(new THREE.ExtrudeGeometry(faceShape,{
    depth:.18,bevelEnabled:true,bevelSegments:3,bevelSize:.045,bevelThickness:.045,curveSegments:64
  }),orange);
  face.position.z=-.09;
  mascot.add(face);

  const backEdge=new THREE.Mesh(new THREE.TorusGeometry(1.39,.055,10,96),orangeEdge);
  backEdge.position.z=-.12;mascot.add(backEdge);

  // Clock ticks are printed onto the face, never replaced by a giant front rim.
  const ticks=new THREE.Group();ticks.position.z=.115;mascot.add(ticks);
  for(let i=0;i<12;i++){
    const a=i*Math.PI/6,major=i%3===0;
    const tick=new THREE.Mesh(new THREE.BoxGeometry(major?.065:.038,major?.24:.145,.025),ink);
    tick.position.set(Math.sin(a)*1.115,Math.cos(a)*1.115,0);
    tick.rotation.z=-a;
    ticks.add(tick);
  }

  // Expressive cream eyes with dark pupils and individual lashes.
  const eyeL=new THREE.Mesh(new THREE.SphereGeometry(.34,32,24),cream);
  const eyeR=eyeL.clone();
  eyeL.scale.set(.62,1.08,.30);eyeR.scale.copy(eyeL.scale);
  eyeL.position.set(-.43,.31,.38);eyeR.position.set(.43,.31,.38);
  mascot.add(eyeL,eyeR);

  const pupilL=new THREE.Mesh(new THREE.SphereGeometry(.145,24,18),ink);
  const pupilR=pupilL.clone();
  pupilL.scale.set(.64,1.22,.42);pupilR.scale.copy(pupilL.scale);
  pupilL.position.set(-.43,.27,.62);pupilR.position.set(.43,.27,.62);
  mascot.add(pupilL,pupilR);

  const lashes=new THREE.Group();mascot.add(lashes);
  for(const side of[-1,1]){
    for(let i=0;i<5;i++){
      const lash=capsule(.014,.095,ink);
      lash.rotation.z=side*(.52-i*.15);
      lash.position.set(side*(.29+i*.066),.61,.59);
      lashes.add(lash);
    }
  }

  const nose=new THREE.Mesh(new THREE.SphereGeometry(.055,20,14),ink);
  nose.position.set(0,-.015,.66);mascot.add(nose);

  const mouthCurve=new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(-.235,-.35,.64),
    new THREE.Vector3(0,-.47,.68),
    new THREE.Vector3(.235,-.35,.64)
  );
  const mouth=new THREE.Mesh(new THREE.TubeGeometry(mouthCurve,20,.026,10,false),ink);
  mascot.add(mouth);

  const makeArm=side=>{
    const g=new THREE.Group();
    const shoulder=new THREE.Group();g.add(shoulder);
    shoulder.position.set(side*1.34,.05,.02);
    const upper=capsule(.07,.38,orange);
    upper.rotation.z=-side*.64;upper.position.x=side*.18;shoulder.add(upper);
    const fore=capsule(.06,.31,orange);
    fore.rotation.z=-side*.30;fore.position.set(side*.38,-.17,.01);shoulder.add(fore);

    const cuff=new THREE.Mesh(new THREE.CylinderGeometry(.073,.073,.13,18),ink);
    cuff.rotation.z=Math.PI/2;cuff.position.set(side*.52,-.29,.02);shoulder.add(cuff);
    for(let i=0;i<3;i++){
      const stripe=new THREE.Mesh(new THREE.CylinderGeometry(.078,.078,.014,18),orangeEdge);
      stripe.rotation.z=Math.PI/2;stripe.position.set(side*(.46+i*.045),-.29,.025);shoulder.add(stripe);
    }

    const glove=makeGlove(white);
    glove.position.set(side*.63,-.37,.05);
    glove.scale.x=side;
    shoulder.add(glove);
    return g;
  };
  const armL=makeArm(-1),armR=makeArm(1);root.add(armL,armR);

  const makeLeg=side=>{
    const g=new THREE.Group();g.position.set(side*.30,-1.31,.0);
    const leg=capsule(.052,.54,ink);g.add(leg);
    const boot=new THREE.Mesh(new THREE.SphereGeometry(.24,24,16),bootMat);
    boot.scale.set(1.08,.48,1.32);boot.position.set(side*.045,-.37,.08);g.add(boot);
    const sole=new THREE.Mesh(new THREE.BoxGeometry(.34,.045,.38),ink);
    sole.position.set(side*.045,-.49,.08);g.add(sole);
    return g;
  };
  root.add(makeLeg(-1),makeLeg(1));

  // Holographic machinery stays behind her silhouette.
  const holoRingMat=basic(C.holo,.14,{side:THREE.DoubleSide,blending:THREE.AdditiveBlending,depthWrite:false});
  const halo=new THREE.Mesh(new THREE.TorusGeometry(1.64,.022,8,96),holoRingMat);
  halo.position.z=-.30;root.add(halo);

  const ring2=new THREE.Mesh(new THREE.RingGeometry(1.52,1.60,96),basic(C.holoSoft,.055,{side:THREE.DoubleSide,blending:THREE.AdditiveBlending,depthWrite:false}));
  ring2.position.z=-.31;root.add(ring2);

  const scanGroup=new THREE.Group();scanGroup.position.z=-.32;root.add(scanGroup);
  const scanMat=basic(C.holo,.075,{blending:THREE.AdditiveBlending,depthWrite:false});
  for(let i=-4;i<=4;i++){
    const line=new THREE.Mesh(new THREE.PlaneGeometry(3.5,.012),scanMat);
    line.position.y=i*.32;scanGroup.add(line);
  }

  const particles=new THREE.Group();particles.position.z=-.28;root.add(particles);
  const pMat=basic(C.holoSoft,.40,{blending:THREE.AdditiveBlending,depthWrite:false});
  for(let i=0;i<34;i++){
    const p=new THREE.Mesh(new THREE.SphereGeometry(.008+Math.random()*.013,6,6),pMat);
    const a=Math.random()*Math.PI*2,r=1.55+Math.random()*.8;
    p.position.set(Math.cos(a)*r,(Math.sin(a)*r)*.72+(Math.random()-.5)*.4,0);
    particles.add(p);
  }

  scene.add(new THREE.HemisphereLight(0xffd6a0,0x170702,1.9));
  const key=new THREE.PointLight(0xffa040,4.4,8);key.position.set(0,1.5,4);scene.add(key);
  const fill=new THREE.PointLight(0xff5b1e,1.2,7);fill.position.set(-2,-1,2);scene.add(fill);

  const resize=()=>{
    const w=Math.max(1,el.clientWidth),h=Math.max(1,el.clientHeight);
    const aspect=w/h;
    const view=2.45;
    camera.left=-view*aspect;camera.right=view*aspect;camera.top=view;camera.bottom=-view;
    camera.updateProjectionMatrix();renderer.setSize(w,h,false);
  };
  const move=e=>{
    const r=el.getBoundingClientRect();
    state.current.targetX=((e.clientX-r.left)/r.width-.5)*.34;
    state.current.targetY=-((e.clientY-r.top)/r.height-.5)*.24;
  };
  const leave=()=>{state.current.targetX=0;state.current.targetY=0};
  addEventListener("resize",resize);el.addEventListener("pointermove",move);el.addEventListener("pointerleave",leave);

  let raf=0,last=performance.now();
  const frame=t=>{
    const s=state.current,rx=s.reaction||{},gesture=rx.gesture||"idle",emotion=rx.emotion||"neutral";
    const dt=Math.min(.04,(t-last)/1000);last=t;
    if(!s.blink&&t>=s.nextBlink){s.blink=true;s.nextBlink=t+105+Math.random()*55}
    if(s.blink&&t>=s.nextBlink){s.blink=false;s.nextBlink=t+1700+Math.random()*2800}

    const bright=rx.pace==="bright",calm=rx.pace==="calm";
    root.rotation.y+=(s.targetX-root.rotation.y)*dt*4;
    root.rotation.x+=(s.targetY*.55-root.rotation.x)*dt*4;

    let bob=.025;
    if(s.mode==="thinking")bob=.06;
    if(gesture==="celebrate"||emotion==="excited")bob=.075;
    if(gesture==="wave")bob=.045;
    mascot.position.y=Math.sin(t*(bright?.0055:.0024))*bob;
    mascot.rotation.z=Math.sin(t*.0018)*.012;

    if(gesture==="think")mascot.rotation.z=-.035+Math.sin(t*.002)*.008;
    if(gesture==="curious")mascot.rotation.z=.028;
    if(emotion==="concerned"||emotion==="suspicious")mascot.rotation.z=-.018;

    const eyeY=s.blink?.055:1;
    eyeL.scale.y=.1+1.0*eyeY;eyeR.scale.y=.1+1.0*eyeY;
    pupilL.scale.y=1.22*eyeY;pupilR.scale.y=1.22*eyeY;
    lashes.scale.y=eyeY;

    const px=s.targetX*.12,py=s.targetY*.10;
    pupilL.position.x=-.43+px;pupilR.position.x=.43+px;
    pupilL.position.y=.27+py;pupilR.position.y=.27+py;

    const talking=s.mode==="speaking";
    mouth.scale.y=talking?(1+Math.abs(Math.sin(t*.026))*.32):1;
    mouth.scale.x=emotion==="amused"?1.08:1;

    const wave=(gesture==="wave"||gesture==="celebrate");
    armL.rotation.z=wave?Math.sin(t*.009)*.34:gesture==="think"?.22:Math.sin(t*.0022)*.018;
    armR.rotation.z=wave?Math.sin(t*.010+.8)*.34:gesture==="point"?-.18:gesture==="think"?-.08:(talking?Math.sin(t*.008)*.12:.018);

    const glow=emotion==="excited"?.20:emotion==="concerned"?.075:emotion==="amused"?.15:.11;
    halo.material.opacity=glow;
    halo.rotation.z=t*.00032;
    ring2.rotation.z=-t*.00020;
    scanGroup.position.y=Math.sin(t*.0014)*.04;
    particles.rotation.y=t*.00013;
    particles.rotation.z=Math.sin(t*.0007)*.04;

    renderer.render(scene,camera);raf=requestAnimationFrame(frame);
  };
  resize();raf=requestAnimationFrame(frame);
  return()=>{cancelAnimationFrame(raf);removeEventListener("resize",resize);el.removeEventListener("pointermove",move);el.removeEventListener("pointerleave",leave);renderer.dispose();el.innerHTML=""};
 },[]);

 return <div ref={mount} className="miss-3d"/>;
}
