import {node,shape,ellipse,paths} from './vector.mjs';
import {unifiedSilhouette} from './silhouette.mjs';

const ink='#49414B',pink='#F1A9BC';
const mix=(a,b,t)=>a+(b-a)*t;
const point=(a,b,t)=>a.map((value,i)=>mix(value,b[i],t));
const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
const unit=(a,b)=>{const d=Math.hypot(b[0]-a[0],b[1]-a[1])||1;return [(b[0]-a[0])/d,(b[1]-a[1])/d];};
const add=(a,b,s=1)=>[a[0]+b[0]*s,a[1]+b[1]*s];
const xy=p=>p.map(v=>Math.round(v*1000)/1000).join(' ');
const baseBody='M -80 -55 C -102 -33 -112 9 -106 52 C -100 88 -78 102 -48 102 L 48 102 C 78 102 100 88 106 52 C 112 9 102 -33 80 -55 C 50 -85 -50 -85 -80 -55 Z';
const baseTail='M 78 70 C 101 88 139 77 159 49 C 176 27 179 -3 169 -18 C 162 -29 146 -24 147 -12 C 152 7 142 27 126 38 C 112 48 93 46 81 45 C 70 47 70 62 78 70 Z';
const backTail='M 98 64 C 110 79 127 90 145 90 C 162 92 179 84 183 70 C 190 49 175 46 171 58 C 169 66 162 72 151 71 C 135 70 122 51 111 48 C 98 44 90 60 98 64 Z';
const proneTail='M 108 76 C 129 90 153 79 168 59 C 183 39 184 16 176 7 C 167 -2 158 7 162 18 C 166 33 153 51 139 56 C 127 60 119 56 112 55 C 100 55 99 70 108 76 Z';
const loafTail='M 72 83 C 90 98 125 101 142 88 C 153 80 148 69 139 68 C 128 67 125 77 114 79 C 103 83 95 80 88 77 C 82 74 76 73 72 75 C 67 77 67 80 72 83 Z';
const loafBody='M -68 -9 C -84 13 -86 53 -82 77 C -79 96 -60 104 -39 104 L 45 104 C 78 104 104 94 109 72 C 118 33 104 4 75 -9 C 39 -22 -35 -21 -68 -9 Z';
const baseArm='M -80 -28 C -91 -10 -86 22 -71 34 C -58 43 -39 31 -38 17 C -36 9 -51 -19 -50 -23';

function oval(cx,cy,rx,ry,rotation=0) {
  const angles=[-140,-200,-250,-290,-340,-400].map(v=>v*Math.PI/180);
  const rotate=([x,y])=>[cx+x*Math.cos(rotation)-y*Math.sin(rotation),cy+x*Math.sin(rotation)+y*Math.cos(rotation)];
  const p=t=>rotate([rx*Math.cos(t),ry*Math.sin(t)]);
  const tangent=t=>[-rx*Math.sin(t),ry*Math.cos(t)];
  let d='M '+xy(p(angles[0]));
  for(let i=0;i<6;i++){
    const a=angles[i],b=i===5?angles[0]-Math.PI*2:angles[i+1],k=4/3*Math.tan((b-a)/4);
    const c1=rotate(add([rx*Math.cos(a),ry*Math.sin(a)],tangent(a),k));
    const c2=rotate(add([rx*Math.cos(b),ry*Math.sin(b)],tangent(b),-k));
    d+=` C ${xy(c1)} ${xy(c2)} ${xy(p(b))}`;
  }
  return d+' Z';
}

// 两个轮廓使用相同顶点次序，生成每一帧的独立贝塞尔路径。
function morph(a,b,t) {
  if(t<=0)return a;if(t>=1)return b;
  const pa=paths(a)[0],pb=paths(b)[0];
  if(pa.vertices.length!==pb.vertices.length)throw new Error('姿势轮廓顶点数不一致');
  const vertices=pa.vertices.map((v,i)=>({p:point(v.p,pb.vertices[i].p,t),
    in:point(v.in||v.p,pb.vertices[i].in||pb.vertices[i].p,t),
    out:point(v.out||v.p,pb.vertices[i].out||pb.vertices[i].p,t)}));
  let d='M '+xy(vertices[0].p);
  const count=pa.closed?vertices.length:vertices.length-1;
  for(let i=0;i<count;i++){
    const v=vertices[i],next=vertices[(i+1)%vertices.length];
    d+=` C ${xy(v.out)} ${xy(next.in)} ${xy(next.p)}`;
  }
  return d+(pa.closed?' Z':'');
}

function armPath(root,elbow,paw,radius=18,width=13,reverse=false) {
  const d1=unit(root,elbow),d2=unit(elbow,paw),sign=reverse?-1:1;
  const n1=[-d1[1]*sign,d1[0]*sign],n2=[-d2[1]*sign,d2[0]*sign];
  const a=add(root,n1,width),b=add(paw,n2,radius),c=add(paw,n2,-radius),d=add(root,n1,-width);
  return `M ${xy(a)} C ${xy(add(elbow,n1,width))} ${xy(add(add(paw,n2,radius),d2,-12))} ${xy(b)}`+
    ` C ${xy(add(b,d2,radius*1.35))} ${xy(add(c,d2,radius*1.35))} ${xy(c)}`+
    ` C ${xy(add(elbow,n1,-width))} ${xy(add(root,d1,8))} ${xy(d)}`;
}

function groundArm(root,elbow,paw,width=1,reverse=false) {
  const [x,y]=paw;
  const path=`M ${root[0]-12*width} ${root[1]-2} C ${elbow[0]-15*width} ${elbow[1]-8} ${x-34*width} ${y-2} ${x-19*width} ${y+10}`+
    ` C ${x-6*width} ${y+17} ${x+22*width} ${y+13} ${x+24*width} ${y}`+
    ` C ${x+24*width} ${y-10} ${elbow[0]+12*width} ${elbow[1]-4} ${root[0]+12*width} ${root[1]}`;
  if(!reverse)return path;
  const vertices=paths(path)[0].vertices.reverse();
  let result='M '+xy(vertices[0].p);
  for(let i=1;i<vertices.length;i++)result+=` C ${xy(vertices[i-1].in)} ${xy(vertices[i].out)} ${xy(vertices[i].p)}`;
  return result;
}

function seatedFoot(cx) {
  // 从左上沿脚底走到右上，与抬腿轮廓使用相同的顶点顺序。
  const angles=[-135,-225,-315,-405].map(v=>v*Math.PI/180),k=-.552284749831;
  const p=a=>[cx+30*Math.cos(a),87+21*Math.sin(a)];
  const tangent=a=>[-30*Math.sin(a),21*Math.cos(a)];
  let d='M '+xy(p(angles[0]));
  for(let i=0;i<3;i++)d+=` C ${xy(add(p(angles[i]),tangent(angles[i]),k))} ${xy(add(p(angles[i+1]),tangent(angles[i+1]),-k))} ${xy(p(angles[i+1]))}`;
  return d;
}

export const sitPose={
  head:[0,-102,1,0],body:[0,11,106,91,0],back:0,low:0,art:0,
  fore:[[-64,-25,-66,-3,-56,15],[64,-25,66,-3,56,15]],
  hind:[[-58,59,-58,76,-58,87],[54,59,54,76,54,87]],
};
const edgePronePose={
  head:[-60,-1,.76,-.035],body:[33,49,98,48,-.015],back:0,low:1,art:1,
  fore:[[-62,45,-74,65,-86,80],[-13,50,-22,70,-35,89]],
  hind:[[48,58,51,74,46,81],[94,57,100,76,91,89]],
};
export const pronePose={
  head:[-65,15,.76,-.035],body:[28,55,102,41,-.015],back:0,low:1,art:1,
  fore:[[-66,58,-84,77,-102,86],[-18,55,-30,75,-48,91]],
  hind:[[48,58,51,74,46,81],[94,57,100,76,91,89]],
};
export const loafPose={
  head:[0,-54,.78,0],body:[0,39,94,65,0],back:0,low:0,art:1,loaf:1,
  fore:[[-50,10,-55,54,-29,76],[50,10,55,54,29,76]],
  hind:[[-59,45,-54,61,-43,69],[59,45,68,61,60,69]],
};
export const sidePose={
  head:[-67,26,.73,-1.12],body:[27,48,89,48,-.35],back:.45,low:0,art:1,
  fore:[[-18,16,-23,-15,-3,-31],[6,44,21,30,29,5]],
  hind:[[82,39,93,20,82,4],[96,61,119,50,116,28]],
};
export const backPose={
  head:[-81,38,.67,-1.98],body:[37,25,77,66,-.20],back:1,low:0,art:1,
  fore:[[-10,5,-33,-21,-17,-50],[8,42,-13,19,7,-9]],
  hind:[[89,25,101,-2,87,-47],[92,43,118,27,124,-9]],
};

export function blendPose(a,b,t) {
  return {head:point(a.head,b.head,t),body:point(a.body,b.body,t),back:mix(a.back,b.back,t),low:mix(a.low,b.low,t),art:mix(a.art,b.art,t),
    loaf:mix(a.loaf||0,b.loaf||0,t),
    fore:a.fore.map((v,i)=>point(v,b.fore[i],t)),hind:a.hind.map((v,i)=>point(v,b.hind[i],t))};
}

function buildPads(name,paw,angle,amount,radius=18) {
  const dot=(label,x,y,rx,ry)=>shape(name+label,ellipse(x,y,rx,ry),pink,null);
  return node(name+' Pads',paw[0],paw[1],[dot(' Bean',0,4,8,7),dot(' Toe A',-9,-5,3.5,4),
    dot(' Toe B',0,-10,3.8,4),dot(' Toe C',9,-5,3.5,4)],
    {rotation:angle,scaleX:radius/19,scaleY:radius/19,opacity:amount,static:true});
}

export function drawPose(pose,label='Frame') {
  const [cx,cy,rx,ry,angle]=pose.body;
  const standing=1-pose.art;
  let body=morph(baseBody,oval(cx,cy,rx,ry,angle),1-standing);
  if(pose.loaf){
    // 胸口竖起、后背向右延伸；呼吸只拉高躯干，底边仍贴地。
    let axis=0;
    const chest=loafBody.replace(/-?\d+(?:\.\d+)?/g,value=>String(axis++%2?(Number(value)-39)*ry/65+cy:Number(value)));
    body=morph(body,chest,pose.loaf);
  }
  const limbs=[],limbPaths=[],details=[];
  for(const type of ['fore','hind'])for(let i=0;i<2;i++){
    const v=pose[type][i],root=v.slice(0,2),elbow=v.slice(2,4),paw=v.slice(4,6),dir=unit(elbow,paw);
    const name=label+' '+type+' '+(i?'Near':'Far'),radius=type==='fore'?17:20;
    const curled=armPath(root,elbow,paw,radius,type==='fore'?12:17,type==='fore'&&i===1);
    const grounded=groundArm(root,elbow,paw,i?.94:.82,type==='fore'&&i===1);
    let path=morph(curled,grounded,pose.low);
    if(type==='fore'){
      let count=0;const base=i?baseArm.replace(/-?\d+(?:\.\d+)?/g,n=>String(Number(n)*(count++%2===0?-1:1))):baseArm;
      path=morph(base,path,1-standing);
    } else if(standing>.001){
      path=morph(seatedFoot(i?54:-58),path,1-standing);
    }
    const baseHand=type==='fore'?[i?56:-56,19]:[i?54:-58,87];
    const hand=point(baseHand,add(paw,dir,4*pose.back),pose.art);
    const children=[shape(name+' Fur',path)];
    limbPaths.push(path);
    if(pose.low>.01&&pose.art>.65){
      const [x,y]=point(baseHand,paw,pose.art);
      children.push(node(name+' Toes',0,0,[shape(name+' Toe lines',`M ${x-13} ${y+3} C ${x-13} ${y+7} ${x-10} ${y+10} ${x-8} ${y+11} M ${x-2} ${y+5} C ${x-2} ${y+8} ${x} ${y+11} ${x+2} ${y+12}`,null,ink,2.2)],{opacity:pose.low*(1-(pose.loaf||0)),static:true}));
    }
    const padAmount=type==='fore'?.48*standing+.9*pose.back:.9*pose.back;
    if(padAmount>.001)children.push(buildPads(name,hand,(Math.atan2(dir[1],dir[0])+Math.PI/2)*pose.back,padAmount,radius));
    if(!(pose.unified&&type==='hind'&&i===0))details.push(...children.slice(1));
    limbs.push(node(name,0,0,children,{static:true}));
  }
  const tail=morph(morph(morph(baseTail,morph(backTail,proneTail,pose.low),pose.art),backTail,pose.tailTuck||0),loafTail,pose.loaf||0);
  if(pose.unified){
    const contour=unifiedSilhouette([body,...limbPaths,tail],pose.head);
    if(pose.low<.65&&pose.art<.999){
      // 起身时逐渐补回爪子内侧线，避免肉垫像浮在肚子上。
      details.unshift(node(label+' Seated folds',0,0,limbPaths.map((path,i)=>shape(label+' Seated limb '+i,path,null,ink,3.8)),
        {opacity:(1-smooth((pose.low-.25)/.4))*(1-smooth(((pose.loaf||0)-.7)/.25)),static:true}));
    }
    if(pose.low>.65){
      const f=pose.fore[1],h=pose.hind[1];
      const creases=`M ${f[0]+2} ${f[1]+14} C ${f[2]+14} ${f[3]-1} ${f[4]+24} ${f[5]-6} ${f[4]+22} ${f[5]+4}`+
        ` M ${h[0]-3} ${h[1]+8} C ${h[0]-4} ${h[1]+20} ${h[4]-20} ${h[5]-10} ${h[4]-18} ${h[5]+5}`;
      details.push(node(label+' Folds',0,0,[shape(label+' Limb folds',creases,null,ink,3.3)],{opacity:smooth((pose.low-.65)/.35)*(1-(pose.loaf||0)),static:true}));
    }
    if(pose.loaf>.5){
      // 前爪折在胸口底下，只露出两段贴地的弧线。
      const folds='M -61 82 C -66 92 -62 101 -47 102 C -34 104 -18 103 -12 97 M 61 82 C 66 92 62 101 47 102 C 34 104 18 103 12 97';
      const opacity=smooth((pose.loaf-.5)*2);
      details.push(node(label+' Loaf folds',0,0,[shape(label+' Tucked paws',folds,null,ink,3.2),
        shape(label+' Resting flank','M 82 42 C 93 53 97 71 88 85',null,ink,2.8)],{opacity,static:true}));
      const [x,y,scale,rotation]=pose.head;
      details.push(node(label+' Loaf chin',x,y,[shape(label+' Chin crease','M -61 82 C -35 96 35 96 61 82',null,ink,2.8)],
        {scaleX:scale,scaleY:scale,rotation,opacity,static:true}));
    }
    return node(label,0,0,[shape(label+' Unified Fur',contour),...details],{static:true});
  }
  const belly=pose.back>.001?node(label+' Belly',cx+4,cy+2,[
    shape(label+' Belly warmth',ellipse(0,0,37,44),'#FFF1F2',null),
  ],{rotation:angle,opacity:pose.back*.6,scaleX:.35+.65*pose.back,static:true}):null;
  const tailNode=node(label+' Tail',0,0,[shape(label+' Tail fur',tail)],{static:true});
  const torso=shape(label+' Body fur',body),warmth=belly?[belly]:[];
  let layers;
  if(pose.art<.65)layers=[tailNode,torso,...warmth,limbs[2],limbs[3],limbs[0],limbs[1]];
  else if(pose.back<.4)layers=[tailNode,limbs[2],torso,...warmth,limbs[0],limbs[3],limbs[1]];
  else layers=[tailNode,limbs[0],limbs[2],torso,...warmth,limbs[3],limbs[1]];
  return node(label,0,0,layers,{static:true});
}

const crouchPose=blendPose(sitPose,edgePronePose,.3);
const leanPose={head:[-36,-24,.84,-.55],body:[12,43,94,58,-.45],back:.15,low:0,art:1,
  fore:[[-37,-2,-48,15,-47,33],[20,22,27,20,37,-1]],
  hind:[[-4,57,7,75,-2,87],[72,57,90,70,88,82]]};
const rollKeys=[[0,sitPose],[16,crouchPose],[40,leanPose],[68,sidePose],[96,backPose],
  [174,backPose],[202,sidePose],[230,leanPose],[254,crouchPose],[270,sitPose]];
const quantize=(value,steps)=>Math.round(value*steps)/steps;

function settle(amount) {return blendPose(sitPose,pronePose,quantize(smooth(amount),24));}

export function poseAt(name,frame,duration) {
  let pose;
  if(name==='BellyTickle') {
    if(frame>=48)return poseAt('Roll',174+(frame-48),270);
    pose=structuredClone(backPose);
    const kick=Math.sin(frame*Math.PI/12)*Math.sin(Math.PI*frame/48);
    pose.fore[0][4]+=5*kick;pose.fore[1][5]+=5*kick;
    pose.hind[0][5]-=17*kick;pose.hind[1][4]+=14*kick;
    pose.head[3]+=.045*kick;
  } else if(name==='EdgeTuck') {
    pose=structuredClone(edgePronePose);
    pose.head=point(edgePronePose.head,[0,118,.95,0],smooth(frame/duration));
    // 藏身前先收低尾巴，避免尾根出画后只剩一截尾尖。
    if(frame>0)pose.tailTuck=smooth(frame/(duration*.5));
  } else if(name==='Roll') {
    let i=0;while(i<rollKeys.length-2&&frame>rollKeys[i+1][0])i++;
    const [a,pa]=rollKeys[i],[b,pb]=rollKeys[i+1];
    pose=blendPose(pa,pb,quantize(smooth((frame-a)/(b-a)),8));
    if(frame>=96&&frame<=174){
      const wave=Math.sin(Math.round((frame-96)/4)*Math.PI/6);
      pose.head[3]+=.035*wave;pose.body[4]+=.035*wave;
      pose.fore[0][4]+=2*wave;pose.fore[1][5]-=3*wave;
      pose.hind[0][5]+=4*wave;pose.hind[1][4]-=3*wave;
    }
  } else if(name==='Sleep') {
    pose=structuredClone(loafPose);
    const breath=quantize((1-Math.cos(frame/duration*Math.PI*2))/2,12);
    pose.head[1]-=1.2*breath;pose.body[1]-=.8*breath;pose.body[3]+=.8*breath;
  } else if(['Lie','LieCrawlWait','LieCrawl','EdgeRun'].includes(name)) {
    pose=structuredClone(name==='EdgeRun'?edgePronePose:pronePose);
    if(name==='LieCrawl'||name==='EdgeRun') {
      const envelope=name==='EdgeRun'?1:smooth(frame/18)*(1-smooth((frame-duration+30)/30));
      const gaitFrame=name==='EdgeRun'?frame*2:frame;
      for(const type of ['fore','hind'])for(let i=0;i<2;i++){
        const phase=((gaitFrame/48+(type==='fore'?i:1-i)*.5)%1+1)%1;
        const stance=phase<.62,progress=stance?phase/.62:(phase-.62)/.38;
        const stride=(stance?mix(-1,1,progress):mix(1,-1,smooth(progress)))*envelope;
        const lift=stance?0:Math.sin(progress*Math.PI)*(type==='fore'?9:6)*envelope;
        pose[type][i][4]+=stride*(type==='fore'?15:10);pose[type][i][5]-=lift;
        pose[type][i][2]+=stride*5;pose[type][i][3]-=lift*.35;
      }
      pose.head[1]-=Math.sin(gaitFrame*Math.PI/24)*(name==='EdgeRun'?1.8:.8)*envelope;
    }
  } else if(name==='Sit_To_Crawl')pose=settle(frame/duration);
  else if(name==='LieCrawlWake')pose=settle(1-smooth((frame-6)/(duration-6)));
  else if(name==='Wake')pose=blendPose(sitPose,loafPose,quantize(1-smooth((frame-6)/(duration-6)),24));
  else if(name==='Drowsy')pose=blendPose(sitPose,loafPose,quantize(smooth((frame-70)/250),24));
  else if(name==='ProneDrowsy')pose=blendPose(pronePose,loafPose,quantize(smooth((frame-18)/302),24));
  else if(name==='Crawl'&&frame>=184)pose=settle((frame-184)/44);
  else return null;
  return pose;
}

// 各状态共用姿势库，过渡和慢动作复用同一组坐姿到趴姿画面。
export function buildFrameArt(definitions) {
  const empty=node('Frame Empty',0,0,[],{static:true}),cache=new Map(),animations=new Map();
  const solo=node('Frame Art',0,0,[empty],{type:'Solo',activeComponentId:empty.id,opacity:0});
  for(const def of definitions){
    const name=def.attributes.name,duration=Number(def.attributes.duration),rows=[];
    if(!['Roll','BellyTickle','EdgeRun','EdgeTuck','Lie','LieCrawlWait','Sleep','LieCrawl','Sit_To_Crawl','Wake','LieCrawlWake','Drowsy','ProneDrowsy','Crawl'].includes(name))continue;
    const times=[];for(let frame=0;frame<duration;frame+=4)times.push(frame);times.push(duration);
    for(const frame of times){
      const pose=poseAt(name,frame,duration);
      if(!pose||pose.art===0){rows.push({frame,id:empty.id,pose:null});continue;}
      pose.unified=!['Roll','BellyTickle','EdgeTuck'].includes(name);
      const key=JSON.stringify({...pose,head:pose.unified?pose.head:null},(_,value)=>typeof value==='number'?Math.round(value*1000)/1000:value);
      let item=cache.get(key);
      if(!item){item=drawPose(pose,'Frame '+String(cache.size).padStart(3,'0'));cache.set(key,item);solo.children.push(item);}
      rows.push({frame,id:item.id,pose});
    }
    animations.set(name,rows);
  }
  return {solo,empty,animations,count:cache.size};
}
