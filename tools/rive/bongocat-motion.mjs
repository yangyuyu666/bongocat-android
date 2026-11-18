import {nodes,tag,number} from './vector.mjs';
import {addArmDefaults,solveForelimbs} from './forelimbs.mjs';
import {actionProgressTrack} from './interactions.mjs';

const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
const envelope=(f,a,b,c,d)=>smooth((f-a)/(b-a))*(1-smooth((f-c)/(d-c)));
const props=['x','y','rotation','scaleX','scaleY','opacity'];
const costumeNames=['Bow','Hat','Crown','Flower','Scarf'];
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
    const closed=amount=>{for(const side of ['Left','Right']){Object.assign(p['Eye '+side],{scaleY:Math.max(.04,1-amount),opacity:amount>.92?0:1});p['Lid '+side].opacity=amount>.92?1:0;}};
    const breathe=(amount=1)=>{const wave=Math.sin(f/duration*Math.PI*2);p.Torso.scaleY+=.007*wave*amount;p.Torso.y-=.6*wave*amount;p.Head.y-=.7*wave*amount;};
    const tail=(strength=1)=>skin.bones.forEach((b,i)=>{p[b.id].rotation+=strength*Math.sin(f*Math.PI/72-i*.65)*[.055,.08,.1][i];});
    const gait=(low=false)=>{
      const amp=envelope(f,0,18,duration-30,duration);
      for(const [i,n] of ['Fore Left','Fore Right','Hind Left','Hind Right'].entries()){
        const phase=f*Math.PI/24+(i===1||i===2?Math.PI:0);
        p[n].x+=Math.cos(phase)*6*amp;
        p[n].y-=Math.max(0,Math.sin(phase))*(low?8:10)*amp;
        p[n].rotation+=Math.sin(phase)*.10*amp;
      }
      p.Torso.y+=Math.sin(f*Math.PI/12)*1.1*amp;
      p.Head.y+=Math.sin(f*Math.PI/12+.7)*.8*amp;
      tail(.8);
    };
    if(['Idle','WaitShort','WaitMedium','WaitLong'].includes(name)){breathe();tail(.5);}
    if(name==='Blink')closed(Math.max(envelope(f,8,14,18,27),.85*envelope(f,37,42,44,52)));
    if(name==='Tail'){
      const amp=envelope(f,0,25,140,180);
      skin.bones.forEach((b,i)=>{p[b.id].rotation+=amp*Math.sin((f-18)*Math.PI/38-i*.7)*[.13,.19,.22][i];});
      breathe();p.Head.rotation=-.025*amp;
    }
    if(name==='Lick'){
      const lift=envelope(f,0,48,155,204),lean=envelope(f,18,60,155,204);
      const start=Math.atan2(40,8),end=Math.atan2(-12,31),angle=start+(end-start)*lift;
      const radius=Math.hypot(8,40)+(Math.hypot(31,-12)-Math.hypot(8,40))*lift;
      p['Fore Left'].x=-64+Math.cos(angle)*radius+2;
      p['Fore Left'].y=-25+Math.sin(angle)*radius-36;
      p['Fore Left'].rotation=-2.4*smooth(lift);
      p['Fore Left Pads'].opacity=.48*(1-.85*lift);
      blend({Head:{x:-13,y:-90,rotation:-.08},Face:{x:-2}},lean);
      const lick=envelope(f,61,67,74,80)+envelope(f,101,107,114,120)+envelope(f,141,147,154,160);
      closed(.62*lean);p.Head.y+=1.5*lick;
      Object.assign(p['Lick Tongue'],{opacity:lick,x:p.Head.x-8*Math.cos(p.Head.rotation)-43*Math.sin(p.Head.rotation),
        y:p.Head.y-8*Math.sin(p.Head.rotation)+43*Math.cos(p.Head.rotation),rotation:p.Head.rotation+.35});
      p.Smile.opacity=1-lick*.7;tail(.4);
    }
    if(name==='Stretch'){
      const amp=envelope(f,0,65,134,216);
      blend({Torso:{y:22,scaleX:1.08,scaleY:.88},Head:{y:-71,rotation:.03},
        'Fore Left':{x:-59,y:43,rotation:.10,scaleY:1.15},'Fore Right':{x:49,y:43,rotation:-.10,scaleY:1.15},
        'Tail Rig':{rotation:-.12},'Hind Left':{x:-65},'Hind Right':{x:61}},amp);
      closed(.97*amp);p.Head.y+=Math.sin(f*.14)*.7*amp;tail(.5);
    }
    if(name==='Yawn'){
      const amp=envelope(f,5,50,101,164);
      closed(amp);p.Head.y-=5*amp;p.Head.rotation-=.045*amp;
      p['Mouth Open'].opacity=amp;p['Mouth Open'].scaleY=.3+.95*amp;
      p['Mouth Open'].scaleX=.65+.55*amp;p.Smile.opacity=1-amp;
      p.Tongue.opacity=.8*amp;p.Tongue.y=48+4*amp;
      p['Fore Left'].rotation=-.17*amp;p['Fore Right'].rotation=.17*amp;breathe();
    }
    if(name==='Look'){
      const glance=envelope(f,6,35,65,92)-envelope(f,96,126,157,188);
      p.Head.rotation=.055*glance;p.Head.x=5*glance;p.Face.x=5*glance;
      tail(.5);closed(envelope(f,82,87,89,96));
    }
    if(name==='Crawl'){blend(lie,smooth((f-184)/44));gait();}
    if(['Lie','LieCrawlWait','Sleep','LieCrawl'].includes(name)){
      blend(lie,1);
      if(name==='LieCrawl')gait(true);
      else {breathe(.55);tail(.25);closed(name==='Sleep'?1:.18);}
      if(name==='Sleep'){
        p.Zzz.opacity=.42+.3*(.5+.5*Math.sin(f/duration*Math.PI*2));
        p.Zzz.y=-5*(.5+.5*Math.sin(f/duration*Math.PI*2));
      }
    }
    if(name==='Sit_To_Crawl'){blend(lie,smooth(f/duration));tail(.3);}
    if(name==='Drowsy'||name==='ProneDrowsy'){
      const settle=smooth((f-70)/250);blend(lie,settle);
      closed(Math.max(.55*envelope(f,0,45,110,155),smooth((f-170)/165)));
      p.Head.rotation+=Math.sin(f*Math.PI/105)*.035*(1-settle);tail(.2);
    }
    if(['Wake','LieCrawlWake'].includes(name)){
      blend(lie,1-smooth((f-6)/(duration-6)));closed(1-smooth(f/25));
      const perk=envelope(f,21,34,38,54);p.Head.y-=3*perk;
    }
    if(name==='TapLeft'||name==='TapRight'){
      const side=name==='TapLeft'?'Left':'Right',amp=envelope(f,0,3,4,11);
      p['Fore '+side].y-=22*amp;p['Fore '+side].rotation+=(side==='Left'?-.18:.18)*amp;
      p.Head.y+=1.5*amp;closed(.55*amp);
    }
    if(name==='HeadPet'){
      const amp=envelope(f,0,18,58,90);
      p.Head.y+=10*amp;p.Head.rotation=.065*Math.sin(f*Math.PI/36)*amp;
      closed(.98*amp);p.Torso.scaleY-=.02*amp;tail(.7);
    }
    if(name==='NoseBoop'){
      const recoil=envelope(f,0,7,12,27),tilt=envelope(f,18,38,53,78);
      p.Head.y-=10*recoil;p.Head.scaleX-=.06*recoil;p.Head.scaleY-=.06*recoil;
      p.Head.rotation=.15*tilt;p.Face.x=4*tilt;closed(.98*recoil);
    }
    if(name==='Shy'){
      const hide=envelope(f,0,16,45,83),peek=envelope(f,48,68,80,108);
      p.Head.x-=18*hide;p.Head.y+=12*hide;p.Head.rotation=-.18*hide+.12*peek;
      p.Head.scaleY-=.05*hide;p.Face.x=-7*hide+5*peek;closed(.98*hide*(1-peek));
      tail(.3);
    }
    if(name==='RaiseSign'||name==='HoldSign'){
      const lift=name==='RaiseSign'?smooth(f/duration):1;
      blend({'Fore Left':{x:-82,y:-28,rotation:.65},'Fore Right':{x:76,y:-28,rotation:-.65}},lift);
      p['Fore Left Pads'].opacity=.15;p['Fore Right Pads'].opacity=.15;
      p['Sign Board'].opacity=lift;p['Sign Board'].y=22*(1-lift);
      breathe(.5);tail(.25);
      if(name==='HoldSign')closed(envelope(f,76,82,86,95));
    }
    solveForelimbs(p,arms);
    const drawn=frameArt.animations.get(name)?.find(row=>row.frame===f)?.pose;
    if(drawn) {
      for(const part of ['Torso','Belly','Tail Rig','Fore Left','Fore Right','Hind Left','Hind Right'])p[part].opacity=0;
      p['Frame Art'].opacity=1;
      const [x,y,scale,rotation]=drawn.head;
      Object.assign(p.Head,{x,y,scaleX:scale,scaleY:scale,rotation});
      p['Head Silhouette'].opacity=drawn.unified?0:drawn.art<.6?1:0;p['Frame Head'].opacity=drawn.unified?0:drawn.art<.6?0:1;
      if(name==='Roll'||name==='BellyTickle')closed(.98*drawn.back);
      // 身体下沉到画板底边外，同时保留下巴与胸口的重叠。
      if(name==='EdgeTuck')p['Frame Art'].y=200*smooth(f/duration);
    }
    if(name==='EdgePeek'||name==='PeekPet'){
      for(const part of ['Torso','Belly','Tail Rig','Fore Left','Fore Right','Hind Left','Hind Right','Frame Art'])p[part].opacity=0;
      Object.assign(p.Head,{x:0,y:118,scaleX:.95,scaleY:.95});
      p['Head Silhouette'].opacity=0;p['Frame Head'].opacity=1;
      if(name==='EdgePeek'){
        p.Head.y-=Math.sin(f/duration*Math.PI*2)*1.2;
        closed(envelope(f,50,57,61,70));
      }else{
        const amp=envelope(f,0,15,46,72);
        p.Head.rotation=.07*Math.sin(f*Math.PI/36)*amp;p.Head.y+=5*amp;closed(.98*amp);
      }
    }
    return p;
  }
  return definitions.map(def=>{
    const {name,duration}=def.attributes,frames=Number(duration);
    let controls=def.tracks.join('\n');
    if(name==='Sit_To_Crawl')controls=controls.replace('value="0"','value="-1"');
    if(name==='Inactivity'||name.startsWith('Costume'))return tag('LinearAnimation',def.attributes,controls);
    const times=frameArt.animations.get(name)?.map(row=>row.frame)||Array.from({length:Math.floor(frames/3)+1},(_,i)=>i*3);
    if(times.at(-1)!==frames)times.push(frames);
    const poses=times.map(f=>pose(name,f,frames));
    const tracks=Object.entries(defaults).map(([name,values])=>{
      const properties=Object.keys(values).map(property=>{
        const keys=times.map((frame,i)=>[frame,number(poses[i][name][property])]);
        const stepped=frameArt.animations.has(def.attributes.name)&&(name==='Head'||name==='Frame Art'||property==='opacity');
        // 逐帧姿势保留每次换画，连续动作只移除共线关键帧。
        const compact=stepped?keys.filter((k,i)=>i===0||k[1]!==keys[i-1][1]):keys.filter((k,i)=>i===0||(i===keys.length-1?keys.some(v=>v[1]!==keys[0][1]):Math.abs((k[1]-keys[i-1][1])/(k[0]-keys[i-1][0])-(keys[i+1][1]-k[1])/(keys[i+1][0]-k[0]))>1e-7));
        const interpolationType=stepped?'hold':'linear';
        return tag('KeyedProperty',{property},compact.map(([frame,value])=>tag('KeyFrameDouble',{frame,value,interpolationType})).join(''));
      }).join('');
      return tag('KeyedObject',{objectId:nodes.get(name)?.id||name},properties);
    }).join('\n');
    const rows=frameArt.animations.get(name)||[{frame:0,id:frameArt.empty.id}];
    const swaps=tag('KeyedObject',{objectId:frameArt.solo.id},tag('KeyedProperty',{property:'activeComponentId'},
      rows.filter((row,i)=>!i||row.id!==rows[i-1].id).map(row=>tag('KeyFrameId',{frame:row.frame,value:row.id,interpolationType:'hold'})).join('')));
    return tag('LinearAnimation',def.attributes,controls+tracks+swaps+actionProgressTrack(frames));
  }).join('\n');
}
