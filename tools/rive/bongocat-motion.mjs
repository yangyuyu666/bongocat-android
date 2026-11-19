import {nodes,tag,number} from './vector.mjs';
import {addArmDefaults,solveForelimbs} from './forelimbs.mjs';
import {actionProgressTrack} from './interactions.mjs';
import {WARDROBE_NODE_NAMES} from './wardrobe.mjs';

const clamp01=x=>Math.max(0,Math.min(1,x));
const smooth=x=>{x=clamp01(x);return x*x*(3-2*x);};
const smoother=x=>{x=clamp01(x);return x*x*x*(x*(x*6-15)+10);};
const easeOutBack=(x,s=1.45)=>{x=clamp01(x);const t=x-1;return 1+t*t*((s+1)*t+s);};
const envelope=(f,a,b,c,d)=>smoother((f-a)/(b-a))*(1-smoother((f-c)/(d-c)));
const props=['x','y','rotation','scaleX','scaleY','opacity'];
const costumeNames=WARDROBE_NODE_NAMES;
const eyeOpacityNodes=new Set(['Eye Left','Eye Right','Lid Left','Lid Right','Happy Lid Left','Happy Lid Right']);
const lie={
  Torso:{x:20,y:55,scaleX:1.18,scaleY:.48},
  Head:{x:-50,y:13,scaleX:.78,scaleY:.78,rotation:-.035},
  'Fore Left':{x:-103,y:38,rotation:.15,scaleY:.8},
  'Fore Right':{x:-58,y:38,rotation:-.1,scaleY:.8},
  'Hind Left':{x:49,y:87,scaleX:1.05,scaleY:.78},
  'Hind Right':{x:100,y:87,scaleX:1.05,scaleY:.78},
  'Tail Rig':{x:12,y:7,rotation:.08},
};

export function buildAnimations(definitions,skin,arms,frameArt) {
  const defaults={};
  for(const [name,n] of nodes)if(!costumeNames.includes(name)&&!n.static) {
    defaults[name]=Object.fromEntries(props.map(p=>[p,n[p]??(p.startsWith('scale')||p==='opacity'?1:0)]));
  }
  skin.bones.forEach((b,i)=>{defaults[b.id]={rotation:b.angle-(skin.bones[i-1]?.angle||0)};});
  addArmDefaults(defaults,arms);
  for(const arm of arms)Object.assign(defaults[arm.name],{x:arm.control[0],y:arm.control[1]});

  function pose(name,f,duration) {
    const p=structuredClone(defaults);
    const blend=(target,t)=>{for(const [n,v] of Object.entries(target))for(const [k,value] of Object.entries(v))p[n][k]=defaults[n][k]+(value-defaults[n][k])*t;};
    // 支持安详闭眼与幸福月牙笑眼 ^ ^ 两种表情切换。
    const closed=(amount,happy=false)=>{
      const a=clamp01(amount);
      for(const side of ['Left','Right']){
        Object.assign(p['Eye '+side],{scaleY:Math.max(.04,1-a),opacity:a>.88?0:1});
        p['Lid '+side].opacity=(!happy&&a>.88)?1:0;
        p['Happy Lid '+side].opacity=(happy&&a>.88)?1:0;
      }
    };
    // 有机呼吸：体积守恒挤压拉伸 + 头部延迟跟随 + 双爪轻微呼吸律动。
    const breathe=(amount=1)=>{
      const phase=f/duration*Math.PI*2;
      const wave=Math.sin(phase);
      const follow=Math.sin(phase-.45);
      p.Torso.scaleY+=.012*wave*amount;
      p.Torso.scaleX-=.007*wave*amount;
      p.Torso.y-=.9*wave*amount;
      p.Head.y-=1.15*follow*amount;
      p.Head.rotation+=.008*Math.sin(phase-.8)*amount;
      p['Fore Left'].y-=.45*follow*amount;
      p['Fore Right'].y-=.45*follow*amount;
      p['Fore Left'].rotation+=.015*follow*amount;
      p['Fore Right'].rotation-=.015*follow*amount;
    };
    // 波浪传递尾摆：尾根带动中段，尾尖滞后大幅轻扫。
    const tail=(strength=1,speed=72)=>skin.bones.forEach((b,i)=>{
      p[b.id].rotation+=strength*Math.sin(f*Math.PI/speed-i*.72)*[.065,.10,.135][i];
    });
    const gait=(low=false)=>{
      const amp=envelope(f,0,18,duration-30,duration);
      for(const [i,n] of ['Fore Left','Fore Right','Hind Left','Hind Right'].entries()){
        const phase=f*Math.PI/24+(i===1||i===2?Math.PI:0);
        p[n].x+=Math.cos(phase)*7.2*amp;
        p[n].y-=Math.max(0,Math.sin(phase))*(low?9.5:12)*amp;
        p[n].rotation+=Math.sin(phase)*.13*amp;
      }
      p.Torso.y+=Math.sin(f*Math.PI/12)*1.5*amp;
      p.Torso.rotation+=Math.cos(f*Math.PI/24)*.02*amp;
      p.Head.y+=Math.sin(f*Math.PI/12+.65)*1.2*amp;
      p.Head.rotation+=Math.sin(f*Math.PI/24-.4)*.025*amp;
      tail(.95,48);
    };

    if(['Idle','WaitShort','WaitMedium','WaitLong'].includes(name)){breathe();tail(.62);}

    if(name==='Blink'){
      // 快闭慢开自然生物眨眼 + 轻微俏皮歪头呼吸。
      const blink1=smoother((f-7)/4)*(1-smoother((f-14)/10));
      const blink2=.92*smoother((f-35)/4)*(1-smoother((f-41)/10));
      closed(Math.max(blink1,blink2));
      const tilt=envelope(f,4,18,42,62);
      p.Head.rotation+=.026*tilt;
      p.Head.y+=.9*Math.max(blink1,blink2);
      breathe(.6);tail(.55);
    }

    if(name==='Tail'){
      const amp=envelope(f,0,22,144,180);
      const sway=Math.sin((f-14)*Math.PI/32);
      skin.bones.forEach((b,i)=>{
        p[b.id].rotation+=amp*Math.sin((f-14)*Math.PI/32-i*.78)*[.16,.24,.30][i];
      });
      p.Torso.rotation-=.022*sway*amp;
      p.Torso.x-=1.8*sway*amp;
      p.Head.rotation=-.045*amp+.02*sway*amp;
      p.Face.x=3*amp;
      breathe(.7);
      closed(envelope(f,72,77,80,89));
    }

    if(name==='Lick'){
      // 严格保持 Torso 静止以维持 Fore Left Shoulder 坐标恒定，仅驱动左臂骨骼、头部、表情与尾巴。
      const lift=envelope(f,0,46,156,204),lean=envelope(f,16,58,156,204);
      const preDip=envelope(f,0,10,12,24);
      const start=Math.atan2(40,8),end=Math.atan2(-14,32),angle=start+(end-start)*lift;
      const radius=Math.hypot(8,40)+(Math.hypot(32,-14)-Math.hypot(8,40))*lift;
      const lick1=envelope(f,58,64,73,81);
      const lick2=envelope(f,98,104,113,121);
      const lick3=envelope(f,138,144,153,161);
      const lick=lick1+lick2+lick3;
      p['Fore Left'].x=-64+Math.cos(angle)*radius+2+1.8*lick;
      p['Fore Left'].y=-25+Math.sin(angle)*radius-36+2.2*preDip-2.5*lick;
      p['Fore Left'].rotation=-2.45*smoother(lift)+.1*lick;
      p['Fore Left Pads'].opacity=.56*(1-.88*lift);
      blend({Head:{x:-14,y:-89,rotation:-.095},Face:{x:-2.5,y:1.5}},lean);
      p.Head.y+=2.6*lick;
      p.Head.rotation-=.03*lick;
      // 舔爪时满足半眯，放下爪子后自然轻眨眼。
      const endBlink=envelope(f,176,181,184,193);
      closed(Math.max(.72*lean+.2*lick,endBlink),false);
      Object.assign(p['Lick Tongue'],{opacity:lick,
        scaleY:.85+.28*lick,
        x:p.Head.x-8*Math.cos(p.Head.rotation)-43*Math.sin(p.Head.rotation),
        y:p.Head.y-8*Math.sin(p.Head.rotation)+43*Math.cos(p.Head.rotation),
        rotation:p.Head.rotation+.35-.08*lick});
      p.Smile.opacity=1-lick*.78;
      p['Fore Right'].rotation-=.04*lean;
      tail(.55);
    }

    if(name==='Stretch'){
      // 预备微缩 → 深度前伸拉长 + 幸福月牙笑眼 + 翘尾颤动 → 舒缓回弹。
      const prep=envelope(f,0,18,22,42);
      const amp=envelope(f,14,68,136,216);
      const shiver=Math.sin(f*.42)*.9*envelope(f,66,84,126,144);
      blend({
        Torso:{y:26,scaleX:1.12,scaleY:.83},
        Head:{y:-66,rotation:.055},
        'Fore Left':{x:-63,y:47,rotation:.16,scaleY:1.18},
        'Fore Right':{x:53,y:47,rotation:-.16,scaleY:1.18},
        'Tail Rig':{rotation:-.18},
        'Hind Left':{x:-67,y:89},
        'Hind Right':{x:63,y:89},
      },amp);
      p.Torso.scaleY+=.02*prep;
      p.Head.y-=3*prep-shiver;
      p.Head.rotation+=.012*shiver;
      p['Fore Left'].y+=.6*shiver;
      p['Fore Right'].y-=.6*shiver;
      closed(.98*amp,true);
      skin.bones.forEach((b,i)=>{p[b.id].rotation+=-.06*amp+Math.sin(f*Math.PI/32-i*.6)*[.06,.11,.16][i]*amp;});
    }

    if(name==='Yawn'){
      // 蓄力微低头 → 仰头大张嘴 + 吸气胸腔膨胀 + 双爪收拢微抬 + 巅峰轻颤 → 舒气回落。
      const prep=envelope(f,0,12,14,26);
      const amp=envelope(f,8,50,104,166);
      const peak=envelope(f,50,68,94,114);
      const tremor=Math.sin(f*.55)*peak;
      closed(Math.max(amp,envelope(f,154,159,162,171)));
      p.Torso.scaleY+=.022*amp-.008*prep;
      p.Torso.scaleX-=.012*amp;
      p.Head.y+=2*prep-4.8*amp+.6*tremor;
      p.Head.rotation+=.018*prep-.045*amp+.01*tremor;
      p['Mouth Open'].opacity=amp;
      p['Mouth Open'].scaleY=.28+1.02*amp+.06*tremor;
      p['Mouth Open'].scaleX=.62+.58*amp-.04*tremor;
      p.Smile.opacity=1-amp;
      p.Tongue.opacity=.88*amp;
      p.Tongue.y=48+4.5*amp;
      p.Tongue.scaleY=.85+.25*amp;
      p['Fore Left'].y-=6*amp;p['Fore Right'].y-=6*amp;
      p['Fore Left'].rotation=-.24*amp;p['Fore Right'].rotation=.24*amp;
      tail(.45);
    }

    if(name==='Look'){
      // 视线先行（Face 提前 6 帧偏移）+ 头部优雅弧线转望 + 微微探身重心转移 + 转头眨眼。
      const eyeLead=envelope(f,2,28,62,88)-envelope(f,92,120,154,184);
      const glance=envelope(f,8,38,66,94)-envelope(f,98,128,158,190);
      const arc=Math.abs(glance);
      p.Face.x=8.5*eyeLead;
      p.Face.y=-1.5*Math.abs(eyeLead);
      p.Head.rotation=.085*glance;
      p.Head.x=7.5*glance;
      p.Head.y=-2.2*arc;
      p.Torso.x+=2.5*glance;
      p.Torso.rotation+=.018*glance;
      p['Fore Left'].y-=2.5*Math.max(0,glance);
      p['Fore Right'].y-=2.5*Math.max(0,-glance);
      breathe(.6);tail(.75,54);
      closed(envelope(f,82,86,89,97));
    }

    if(name==='Crawl'){blend(lie,smoother((f-184)/44));gait();}

    if(['Lie','LieCrawlWait','Sleep','LieCrawl'].includes(name)){
      blend(lie,1);
      if(name==='LieCrawl')gait(true);
      else {breathe(.6);tail(.3);closed(name==='Sleep'?1:.18);}
      if(name==='Sleep'){
        // 三个 Z 字母错开相位波浪式漂浮、轻缩放、柔和显隐。
        p.Zzz.opacity=1;
        for(let i=0;i<3;i++){
          const ph=((f/duration+i/3)%1);
          const wave=Math.sin(ph*Math.PI);
          const z=p['Sleep Z '+i];
          z.opacity=.18+.68*wave;
          z.y-=14*ph;
          z.x+=Math.sin(ph*Math.PI*2)*4.5;
          const sc=.82+.24*wave;
          z.scaleX=sc;z.scaleY=sc;
          z.rotation=Math.sin(ph*Math.PI*2)*.08;
        }
      }
    }

    if(name==='Sit_To_Crawl'){blend(lie,smoother(f/duration));tail(.35);}

    if(name==='Drowsy'||name==='ProneDrowsy'){
      const settle=smoother((f-70)/250);blend(lie,settle);
      // 打盹时可爱的小鸡啄米式点头（缓慢低垂再微惊抬头）。
      const nod1=envelope(f,16,52,60,72);
      const nod2=envelope(f,82,118,124,136);
      const nod=(nod1+.85*nod2)*(1-settle);
      closed(Math.max(.65*envelope(f,0,40,115,155)+.3*nod,smoother((f-165)/165)));
      p.Head.y+=7.5*nod;
      p.Head.rotation+=.075*nod+Math.sin(f*Math.PI/105)*.032*(1-settle);
      tail(.22);
    }

    if(['Wake','LieCrawlWake'].includes(name)){
      const wakeT=smoother((f-6)/(duration-6));
      blend(lie,1-wakeT);
      closed(1-smoother(f/22));
      const perk=envelope(f,18,32,38,54);
      p.Head.y-=5.5*perk;
      p.Torso.scaleY+=.022*perk;
      p.Torso.scaleX-=.014*perk;
      p['Fore Left'].y-=3*perk;p['Fore Right'].y-=3*perk;
      tail(.65*wakeT,48);
    }

    if(name==='TapLeft'||name==='TapRight'){
      // 满帧 60fps Q 弹敲击：迅速过冲抬爪 + 身体微倾发力 + 轻快回弹！
      const side=name==='TapLeft'?'Left':'Right',sign=side==='Left'?-1:1;
      const other=side==='Left'?'Right':'Left';
      const up=f<=4?easeOutBack(f/4,1.3):1-smoother((f-4)/7);
      p['Fore '+side].y-=26*up;
      p['Fore '+side].x+=sign*3.5*up;
      p['Fore '+side].rotation+=sign*.24*up;
      p['Fore '+other].y+=2.5*up;
      p.Torso.rotation+=sign*.028*up;
      p.Torso.scaleY-=.018*up;p.Torso.scaleX+=.014*up;
      p.Head.y+=2.5*up;p.Head.rotation+=sign*.035*up;
      closed(.65*up,true);
    }

    if(name==='HeadPet'){
      // 摸头：柔软下压挤压 + 幸福左右蹭蹭 + 月牙笑眼 ^ ^ + 尾巴欢快轻摇。
      const amp=envelope(f,0,16,60,90);
      const rub=Math.sin((f-6)*Math.PI/24);
      p.Head.y+=11.5*amp;
      p.Head.x+=4.5*rub*amp;
      p.Head.rotation=.085*rub*amp;
      p.Face.x+=3.5*rub*amp;
      p.Torso.scaleY-=.032*amp;p.Torso.scaleX+=.024*amp;
      p.Torso.rotation+=.018*rub*amp;
      p['Fore Left'].y+=2.5*amp;p['Fore Right'].y+=2.5*amp;
      p['Fore Left'].rotation+=.08*amp;p['Fore Right'].rotation-=.08*amp;
      closed(.98*amp,true);
      tail(.95,38);
    }

    if(name==='NoseBoop'){
      // 戳鼻子：瞬间受惊后仰缩回 → 弹性回正 + 歪头疑惑张望 + 轻抖脑袋。
      const recoil=envelope(f,0,6,11,26);
      const tilt=envelope(f,18,36,54,76);
      const shake=Math.sin(f*Math.PI/5)*envelope(f,4,10,16,24);
      p.Head.y-=12*recoil;
      p.Head.scaleX-=.07*recoil;p.Head.scaleY-=.07*recoil;
      p.Head.rotation=.17*tilt+.04*shake;
      p.Face.x=5.5*tilt;
      p.Torso.scaleY-=.022*recoil;p.Torso.scaleX+=.018*recoil;
      p['Fore Left'].y-=6*recoil;p['Fore Right'].y-=6*recoil;
      closed(.98*recoil);
      tail(.65,44);
    }

    if(name==='Shy'){
      // 害羞躲闪：缩身歪头 + 前爪羞怯轻抬挡脸 → 偷偷睁眼回眸瞄一下。
      const hide=envelope(f,0,15,48,84),peek=envelope(f,50,68,82,108);
      p.Head.x-=20*hide;p.Head.y+=13*hide;
      p.Head.rotation=-.20*hide+.13*peek;
      p.Head.scaleY-=.055*hide;p.Head.scaleX+=.025*hide;
      p.Face.x=-8*hide+6.5*peek;
      p.Torso.scaleY-=.025*hide;p.Torso.x-=3.5*hide;
      p['Fore Left'].y-=18*hide*(1-.4*peek);
      p['Fore Left'].x+=12*hide*(1-.4*peek);
      p['Fore Left'].rotation-=.42*hide*(1-.4*peek);
      closed(.98*hide*(1-peek),true);
      tail(.35);
    }

    if(name==='RaiseSign'||name==='HoldSign'){
      const lift=name==='RaiseSign'?easeOutBack(f/duration,1.15):1;
      const clampLift=clamp01(lift);
      blend({'Fore Left':{x:-82,y:-28,rotation:.65},'Fore Right':{x:76,y:-28,rotation:-.65}},clampLift);
      p['Fore Left'].y-=3.5*(lift-clampLift);
      p['Fore Right'].y-=3.5*(lift-clampLift);
      p['Fore Left Pads'].opacity=.15;p['Fore Right Pads'].opacity=.15;
      p['Sign Board'].opacity=clampLift;
      p['Sign Board'].y=24*(1-lift);
      p['Sign Board'].scaleX=.92+.08*lift;
      p['Sign Board'].scaleY=.92+.08*lift;
      breathe(.55);tail(.35);
      if(name==='HoldSign'){
        const sWave=Math.sin(f/duration*Math.PI*2);
        p['Sign Board'].y-=.9*sWave;
        p['Sign Board'].rotation=.008*Math.sin(f/duration*Math.PI*2-.4);
        closed(envelope(f,74,79,83,93));
      }
    }

    solveForelimbs(p,arms);

    const drawn=frameArt.animations.get(name)?.find(row=>row.frame===f)?.pose;
    if(drawn) {
      for(const part of ['Torso','Belly','Tail Rig','Fore Left','Fore Right','Hind Left','Hind Right'])p[part].opacity=0;
      p['Frame Art'].opacity=1;
      const [x,y,scale,rotation]=drawn.head;
      Object.assign(p.Head,{x,y,scaleX:scale,scaleY:scale,rotation});
      p['Head Silhouette'].opacity=drawn.unified?0:drawn.art<.6?1:0;
      p['Frame Head'].opacity=drawn.unified?0:drawn.art<.6?0:1;
      if(name==='Roll'||name==='BellyTickle')closed(.98*drawn.back,true);
      // 身体下沉到画板底边外，同时保留下巴与胸口的重叠。
      if(name==='EdgeTuck')p['Frame Art'].y=200*smooth(f/duration);
    }

    if(name==='EdgePeek'||name==='PeekPet'){
      for(const part of ['Torso','Belly','Tail Rig','Fore Left','Fore Right','Hind Left','Hind Right','Frame Art'])p[part].opacity=0;
      Object.assign(p.Head,{x:0,y:118,scaleX:.95,scaleY:.95});
      p['Head Silhouette'].opacity=0;p['Frame Head'].opacity=1;
      if(name==='EdgePeek'){
        const ph=f/duration*Math.PI*2;
        p.Head.y-=Math.sin(ph)*1.8;
        p.Head.rotation=Math.sin(ph-.6)*.032;
        p.Face.x=Math.sin(ph-.3)*4.5;
        closed(envelope(f,50,55,59,68));
      }else{
        const amp=envelope(f,0,14,48,72);
        const rub=Math.sin(f*Math.PI/24);
        p.Head.rotation=.085*rub*amp;
        p.Head.y+=6*amp;
        p.Face.x+=3*rub*amp;
        closed(.98*amp,true);
      }
    }
    return p;
  }

  return definitions.map(def=>{
    const {name,duration}=def.attributes,frames=Number(duration);
    let controls=def.tracks.join('\n');
    if(name==='Sit_To_Crawl')controls=controls.replace('value="0"','value="-1"');
    if(name==='Inactivity'||name.startsWith('Costume')||name.startsWith('Pattern'))return tag('LinearAnimation',def.attributes,controls);
    const step=2;
    const times=frameArt.animations.get(name)?.map(row=>row.frame)||Array.from({length:Math.floor(frames/step)+1},(_,i)=>i*step);
    if(times.at(-1)!==frames)times.push(frames);
    const poses=times.map(f=>pose(name,f,frames));
    const tracks=Object.entries(defaults).map(([nodeName,values])=>{
      const properties=Object.keys(values).map(property=>{
        const keys=times.map((frame,i)=>[frame,number(poses[i][nodeName][property])]);
        const stepped=(property==='opacity'&&eyeOpacityNodes.has(nodeName))
          ||(frameArt.animations.has(def.attributes.name)&&(nodeName==='Head'||nodeName==='Frame Art'||property==='opacity'));
        // 逐帧姿势与眼皮切换保留精确跳变，连续动作只移除共线关键帧。
        const compact=stepped?keys.filter((k,i)=>i===0||k[1]!==keys[i-1][1]):keys.filter((k,i)=>i===0||(i===keys.length-1?keys.some(v=>v[1]!==keys[0][1]):Math.abs((k[1]-keys[i-1][1])/(k[0]-keys[i-1][0])-(keys[i+1][1]-k[1])/(keys[i+1][0]-k[0]))>1e-6));
        const interpolationType=stepped?'hold':'linear';
        return tag('KeyedProperty',{property},compact.map(([frame,value])=>tag('KeyFrameDouble',{frame,value,interpolationType})).join(''));
      }).join('');
      return tag('KeyedObject',{objectId:nodes.get(nodeName)?.id||nodeName},properties);
    }).join('\n');
    const rows=frameArt.animations.get(name)||[{frame:0,id:frameArt.empty.id}];
    const swaps=tag('KeyedObject',{objectId:frameArt.solo.id},tag('KeyedProperty',{property:'activeComponentId'},
      rows.filter((row,i)=>!i||row.id!==rows[i-1].id).map(row=>tag('KeyFrameId',{frame:row.frame,value:row.id,interpolationType:'hold'})).join('')));
    return tag('LinearAnimation',def.attributes,controls+tracks+swaps+actionProgressTrack(frames));
  }).join('\n');
}
