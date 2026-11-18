import {node,shape,ellipse,tailSkin} from './vector.mjs';
import {buildForelimb} from './forelimbs.mjs';
import {buildFrameArt} from './frame-poses.mjs';

export function buildArt(costumeIds,definitions=[]) {
  const ink='#49414B',pink='#F1A9BC';
  const part=(name,x,y,children,extra={})=>node(name,x,y,children,extra);
  const line=(name,d,w=3.3,c=ink)=>shape(name,d,null,c,w);
  const dot=(name,x,y,rx,ry,c=pink)=>shape(name,ellipse(x,y,rx,ry),c,null);
  const tail=part('Tail Rig',0,0,[shape('Tail Silhouette','M 78 70 C 101 88 139 77 159 49 C 176 27 179 -3 169 -18 C 162 -29 146 -24 147 -12 C 152 7 142 27 126 38 C 112 48 93 46 81 45 C 70 47 70 62 78 70 Z','body',ink,4.5,{skinned:true})]);
  const skin=tailSkin([[84,61],[119,54],[145,29],[153,-8]],[240,305]);
  const body=part('Torso',0,11,[shape('Body','M -80 -66 C -102 -44 -112 -2 -106 41 C -100 77 -78 91 -48 91 L 48 91 C 78 91 100 77 106 41 C 112 -2 102 -44 80 -66 C 50 -96 -50 -96 -80 -66 Z')]);
  const belly=part('Belly',0,23,[dot('Belly warmth',0,0,37,45,'#FBE8EE')],{opacity:0});
  function paw(name,x,y,back=false) {
    const outline=back?ellipse(0,0,30,21):'M -14 -4 C -23 6 -25 29 -16 41 C -7 53 13 47 18 35 C 21 25 18 11 11 6';
    const pad=part(name+' Pads',0,back?0:28,[dot(name+' Main pad',0,4,back?11:9,back?10:10),dot(name+' Toe left',-10,-9,4,4.7),dot(name+' Toe middle',0,-13,4.2,4.7),dot(name+' Toe right',10,-9,4,4.7)],{opacity:back?0:.48});
    return part(name,x,y,[shape(name+' Shape',outline),pad]);
  }
  const hindLeft=paw('Hind Left',-58,87,true),hindRight=paw('Hind Right',54,87,true);
  const arms=['Left','Right'].map(side=>buildForelimb(side,ink,pink));
  const [foreLeft,foreRight]=arms.map(arm=>arm.node);
  const head=part('Head',0,-102,[
    part('Head Silhouette',0,0,[
    shape('Head Fill','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69 Z','body',null),
    line('Head Outline','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69',4.5),
    ]),
    part('Frame Head',0,0,[shape('Frame Head Fur','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69 C 74 96 -72 101 -94 69 Z')],{opacity:0}),
    shape('Inner Ear Left','M -86 -59 C -82 -63 -67 -47 -63 -43 C -75 -40 -82 -35 -87 -29 C -90 -39 -89 -53 -86 -59 Z','#F4C4D1',null),
    shape('Inner Ear Right','M 76 -51 C 82 -57 85 -57 86 -49 L 86 -28 C 78 -34 71 -39 63 -41 Z','#F4C4D1',null),
    dot('Cheek Left',-76,29,13,6.5,'#F5C0CE'),dot('Cheek Right',76,29,13,6.5,'#F5C0CE'),
    line('Whisker Left','M -88 20 L -108 16',2.8,'#7A707D'),line('Whisker Right','M 88 20 L 108 16',2.8,'#7A707D'),
  ]);
  const face=part('Face',0,0,[]);head.children.push(face);
  for(const [side,x] of [['Left',-39],['Right',39]]) {
    face.children.push(part('Eye '+side,x,9,[dot('Eye '+side+' Pupil',0,0,5.7,9,ink)]));
    face.children.push(part('Lid '+side,x,9,[line('Closed '+side,'M -6 0 C -3 4 3 4 6 0',2.8)],{opacity:0}));
  }
  const smile=part('Smile',0,0,[line('W Smile','M -19 26 C -19 39 -5 41 0 30 C 5 41 19 39 19 26',3.7)]);
  face.children.push(dot('Tiny Nose',0,22,4.5,2.8,'#CE8AA0'),smile,
    part('Mouth Open',0,39,[dot('Mouth inside',0,0,10,14,'#78505F')],{opacity:0}),
    part('Tongue',0,48,[dot('Tongue shape',0,0,6,7,'#F391AE')],{opacity:0}));

  const accessories=part('Accessories',0,0,[]);head.children.push(accessories);
  function costume(name,children){const c=part(name,0,0,children,{id:costumeIds[name],opacity:0});accessories.children.push(c);return c;}
  costume('Bow',[
    shape('Bow left','M -2 -50 C -24 -72 -39 -62 -31 -42 C -25 -31 -11 -39 -2 -50 Z','#EB729C','#914863',2.7),
    shape('Bow right','M 2 -50 C 24 -72 39 -62 31 -42 C 25 -31 11 -39 2 -50 Z','#EB729C','#914863',2.7),dot('Bow knot',0,-50,7,8,'#FFC1D3')]);
  costume('Hat',[shape('Hat top','M -35 -53 L -35 -87 C -35 -99 -24 -99 0 -99 C 24 -99 35 -99 35 -87 L 35 -53 Z','#626D9C','#444C75',2.7),shape('Hat band','M -34 -66 L 34 -66 L 34 -55 L -34 -55 Z','#E9AC75',null),dot('Hat brim',0,-52,55,7,'#48557F')]);
  costume('Crown',[shape('Crown points','M -40 -53 L -45 -88 L -20 -76 L 0 -104 L 20 -76 L 45 -88 L 40 -53 Z','#FFD15E','#AB7E37',2.7),dot('Crown gem',0,-70,6,7,'#EB7A98')]);
  costume('Flower',[...Array.from({length:5},(_,i)=>dot('Flower petal '+i,68+12*Math.cos(i*2*Math.PI/5),-30+12*Math.sin(i*2*Math.PI/5),9,9,'#F18DAF')),dot('Flower center',68,-30,7,7,'#FFD774')]);
  costume('Scarf',[shape('Scarf tail','M 5 76 L 27 81 L 37 116 L 18 111 Z','#DF8064','#AA5E4C',2.7),shape('Scarf band','M -77 64 C -46 83 42 84 77 63 L 75 77 C 43 98 -47 97 -76 79 Z','#F09A77','#AA5E4C',2.7)]);
  const lickTongue=part('Lick Tongue',-3,-57,[dot('Licking tip',0,0,4.5,6,'#F391AE')],{opacity:0,rotation:.35});
  const frameArt=buildFrameArt(definitions);
  const sign=part('Sign Board',0,0,[
    shape('Sign card','M -112 8 L 112 8 C 119 8 122 12 122 19 L 122 111 C 122 118 119 122 112 122 L -112 122 C -119 122 -122 118 -122 111 L -122 19 C -122 12 -119 8 -112 8 Z','#FFF4D9',ink,4),
    line('Sign underline','M -88 98 L 88 98',2,'#E9D5B2')
  ],{opacity:0});
  const pet=part('Pet',240,305,[tail,body,belly,hindLeft,hindRight,frameArt.solo,head,sign,foreLeft,foreRight,lickTongue]);
  const zzz=part('Zzz',0,0,[0,1,2].map(i=>line('Sleep Z '+i,`M ${307+i*26} ${209-i*25} L ${320+i*26} ${209-i*25} L ${307+i*26} ${225-i*25} L ${320+i*26} ${225-i*25}`,3-i*.3,'#9295B8')),{opacity:0});
  return {pet,zzz,skin,tail,head,body,arms,frameArt};
}
