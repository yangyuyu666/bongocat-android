import {node,shape,ellipse,tailSkin} from './vector.mjs';

const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
const rotate=(x,y,a)=>[x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a)];

export function buildForelimb(side,ink,pink) {
  const sign=side==='Left'?-1:1,name='Fore '+side;
  const joints=[[64,-25],[66,-3],[56,15],[52,30]].map(([x,y])=>[sign*x,y]);
  const skin=tailSkin(joints,[240,305],{firstId:side==='Left'?38000:38010,
    label:name,names:[name+' Shoulder',name+' Elbow',name+' Wrist']});
  let coordinate=0;
  const d='M -80 -28 C -91 -10 -86 22 -71 34 C -58 43 -39 31 -38 17 C -36 9 -51 -19 -50 -23'
    .replace(/-?\d+(?:\.\d+)?/g,n=>String(Number(n)*(coordinate++%2===0?-sign:1)));
  // 根部两端跟肩骨，掌部跟腕骨，中间控制柄分摊弯曲。
  const weights=[{p:1,in:1,out:[1,2,.25]},{p:3,in:3,out:3},
    {p:3,in:3,out:3},{p:1,in:1,out:1}];
  const padPosition=[sign*56,19];
  const dot=(suffix,x,y,rx,ry,color=pink)=>shape(name+suffix,ellipse(x,y,rx,ry),color,null);
  const pads=node(name+' Pads',...padPosition,[
    dot(' Main pad',0,4.5,9.4,10.2),
    dot(' Main highlight',-2.4,1.2,2.6,1.8,'#66FFFFFF'),
    dot(' Toe left',-10.2,-8.6,4.1,4.8),
    dot(' Toe middle',0,-12.8,4.3,4.8),
    dot(' Toe right',10.2,-8.6,4.1,4.8),
  ],{opacity:.56});
  const group=node(name,0,0,[shape(name+' Shape',d,'body',ink,4.4,{skinned:true,skin,weights}),pads]);
  group.extraXml=skin.boneXml();
  return {name,side,skin,node:group,joints,padPosition,control:[side==='Left'?-54:48,-21]};
}

export function addArmDefaults(defaults,arms) {
  for(const arm of arms)arm.skin.bones.forEach((bone,i)=>{
    defaults[bone.id]={rotation:bone.angle-(arm.skin.bones[i-1]?.angle||0)};
    if(i===0)Object.assign(defaults[bone.id],{x:bone.p[0],y:bone.p[1]});
    if(i===2)Object.assign(defaults[bone.id],{scaleX:1,scaleY:1});
  });
}

// 爪子目标只用于求肩肘角度；实际轮廓由连续蒙皮驱动。
export function solveForelimbs(pose,arms) {
  for(const arm of arms) {
    const control=pose[arm.name],torso=pose.Torso,head=pose.Head;
    const [upper,lower,wrist]=arm.skin.bones;
    const local=rotate(upper.p[0]*torso.scaleX,(upper.p[1]-11)*torso.scaleY,torso.rotation);
    const lying=smooth((.85-torso.scaleY)/.37);
    const shoulder=[torso.x+local[0],torso.y+local[1]];
    const chest=[head.x+(arm.side==='Left'?-39:5),head.y+(arm.side==='Left'?48:50)];
    shoulder.forEach((v,i)=>shoulder[i]=v+(chest[i]-v)*lying);
    const target=[control.x+wrist.p[0]-arm.control[0],control.y+wrist.p[1]-arm.control[1]];
    const dx=target[0]-shoulder[0],dy=target[1]-shoulder[1];
    const distance=Math.max(Math.abs(upper.length-lower.length)+.5,Math.min(upper.length+lower.length-.5,Math.hypot(dx,dy)));
    const bend=arm.side==='Left'?1:-1;
    const alpha=Math.acos(Math.max(-1,Math.min(1,(upper.length**2+distance**2-lower.length**2)/(2*upper.length*distance))));
    const shoulderAngle=Math.atan2(dy,dx)+bend*alpha;
    const elbowAngle=-bend*Math.acos(Math.max(-1,Math.min(1,(distance**2-upper.length**2-lower.length**2)/(2*upper.length*lower.length))));
    const foreAngle=shoulderAngle+elbowAngle,palmAngle=wrist.angle+control.rotation;
    const wristPosition=[shoulder[0]+Math.cos(shoulderAngle)*upper.length+Math.cos(foreAngle)*lower.length,
      shoulder[1]+Math.sin(shoulderAngle)*upper.length+Math.sin(foreAngle)*lower.length];
    Object.assign(pose[upper.id],{x:shoulder[0],y:shoulder[1],rotation:shoulderAngle});
    pose[lower.id].rotation=elbowAngle;
    Object.assign(pose[wrist.id],{rotation:palmAngle-foreAngle,scaleX:control.scaleX,scaleY:control.scaleY});
    const padLocal=rotate(arm.padPosition[0]-wrist.p[0],arm.padPosition[1]-wrist.p[1],-wrist.angle);
    const padOffset=rotate(padLocal[0]*control.scaleX,padLocal[1]*control.scaleY,palmAngle);
    Object.assign(pose[arm.name+' Pads'],{x:wristPosition[0]+padOffset[0],y:wristPosition[1]+padOffset[1],
      rotation:control.rotation,scaleX:control.scaleX,scaleY:control.scaleY});
    Object.assign(control,{x:0,y:0,rotation:0,scaleX:1,scaleY:1});
  }
}
