import {node,shape,ellipse,tailSkin} from './vector.mjs';
import {buildForelimb} from './forelimbs.mjs';
import {buildFrameArt} from './frame-poses.mjs';

export function buildArt(costumeIds,definitions=[],patternIds={}) {
  const ink='#463C49',pink='#F3A3BA',shade='#174A3548',shadeSoft='#104A3548',glow='#48FFFFFF';
  const part=(name,x,y,children,extra={})=>node(name,x,y,children,extra);
  const line=(name,d,w=3.3,c=ink)=>shape(name,d,null,c,w);
  const dot=(name,x,y,rx,ry,c=pink)=>shape(name,ellipse(x,y,rx,ry),c,null);

  // 尾巴保持原有骨骼蒙皮结构，增加柔和的内侧体积阴影细节。
  const tail=part('Tail Rig',0,0,[
    shape('Tail Silhouette','M 78 70 C 101 88 139 77 159 49 C 176 27 179 -3 169 -18 C 162 -29 146 -24 147 -12 C 152 7 142 27 126 38 C 112 48 93 46 81 45 C 70 47 70 62 78 70 Z','body',ink,4.4,{skinned:true}),
  ]);
  const skin=tailSkin([[84,61],[119,54],[145,29],[153,-8]],[240,305]);

  // 躯干花纹组（位于 Body 填充之上，通过 Pattern 状态机层切换）
  const bodyPatterns=[
    part('PatternBodyTabby',0,0,[
      shape('Tabby Side L1','M -98 -10 C -85 -12 -74 -8 -70 -2 C -79 1 -90 -1 -100 -4 Z','#58A66535',null),
      shape('Tabby Side L2','M -102 14 C -88 12 -76 16 -72 22 C -82 25 -93 23 -103 20 Z','#58A66535',null),
      shape('Tabby Side R1','M 98 -10 C 85 -12 74 -8 70 -2 C 79 1 90 -1 100 -4 Z','#58A66535',null),
      shape('Tabby Side R2','M 102 14 C 88 12 76 16 72 22 C 82 25 93 23 103 20 Z','#58A66535',null),
    ],{id:patternIds.Tabby?.body,opacity:0}),
    part('PatternBodyTuxedo',0,0,[
      shape('Tuxedo Back R','M 52 -66 C 85 -48 103 -14 103 28 C 82 32 62 14 55 -12 C 48 -34 45 -52 52 -66 Z','#CC3E3843',null),
      shape('Tuxedo Patch L','M -96 14 C -84 8 -70 16 -68 34 C -66 52 -80 62 -95 56 C -102 44 -102 26 -96 14 Z','#CC3E3843',null),
    ],{id:patternIds.Tuxedo?.body,opacity:0}),
    part('PatternBodyCalico',0,0,[
      shape('Calico Orange R','M 54 -64 C 86 -46 102 -12 101 24 C 82 24 68 10 62 -14 C 57 -34 50 -50 54 -64 Z','#DDE89756',null),
      shape('Calico Dark L','M -97 8 C -82 4 -68 15 -68 32 C -68 50 -84 58 -96 50 C -103 38 -102 22 -97 8 Z','#C84A3E43',null),
    ],{id:patternIds.Calico?.body,opacity:0}),
    part('PatternBodySiamese',0,0,[
      shape('Siamese Flank Shade','M -86 -44 C -98 -10 -98 34 -80 66 C -54 80 54 80 80 66 C 98 34 98 -10 86 -44 C 72 -10 72 44 50 62 C 20 72 -20 72 -50 62 C -72 44 -72 -10 -86 -44 Z','#26735542',null),
    ],{id:patternIds.Siamese?.body,opacity:0}),
  ];

  // 躯干：叠加半透明体积底影、下颌柔影与胸腹柔光，兼容全套 8 种毛色。
  const body=part('Torso',0,11,[
    shape('Body','M -80 -66 C -102 -44 -112 -2 -106 41 C -100 77 -78 91 -48 91 L 48 91 C 78 91 100 77 106 41 C 112 -2 102 -44 80 -66 C 50 -96 -50 -96 -80 -66 Z','body',ink,4.4),
    ...bodyPatterns,
    shape('Body Bottom Shade','M -98 54 C -88 79 -67 89 -42 89 L 42 89 C 67 89 88 79 98 54 C 82 72 59 77 35 77 L -35 77 C -59 77 -82 72 -98 54 Z',shade,null),
    shape('Neck Soft Shade','M -56 -48 C -25 -32 25 -32 56 -48 C 34 -40 -34 -40 -56 -48 Z',shadeSoft,null),
    dot('Chest Warmth',0,22,42,46,'#18FFEBF1'),
  ]);
  const belly=part('Belly',0,23,[dot('Belly warmth',0,0,39,46,'#FCE8EE')],{opacity:0});

  function paw(name,x,y,back=false) {
    const outline=back
      ?'M -30 2 C -31 -13 -16 -21 0 -21 C 16 -21 31 -13 30 2 C 29 15 17 21 0 21 C -17 21 -29 15 -30 2 Z'
      :'M -14 -4 C -23 6 -25 29 -16 41 C -7 53 13 47 18 35 C 21 25 18 11 11 6';
    const children=[shape(name+' Shape',outline,'body',ink,4.4)];
    if(back){
      children.push(
        shape(name+' Shade','M -24 7 C -16 17 16 17 24 7 C 16 13 -16 13 -24 7 Z',shadeSoft,null),
        line(name+' Toe Split A','M -9 18 C -9 13 -8 10 -6 8',2.5,ink),
        line(name+' Toe Split B','M 9 18 C 9 13 8 10 6 8',2.5,ink),
      );
    }
    const pad=part(name+' Pads',0,back?0:28,[
      dot(name+' Main pad',0,4,back?11:9,back?10:10),
      dot(name+' Toe left',-10,-9,4,4.7),
      dot(name+' Toe middle',0,-13,4.2,4.7),
      dot(name+' Toe right',10,-9,4,4.7),
    ],{opacity:back?0:.52});
    children.push(pad);
    return part(name,x,y,children);
  }
  const hindLeft=paw('Hind Left',-58,87,true),hindRight=paw('Hind Right',54,87,true);
  const arms=['Left','Right'].map(side=>buildForelimb(side,ink,pink));
  const [foreLeft,foreRight]=arms.map(arm=>arm.node);

  // 头部花纹组（位于头底色之上、内耳与五官及描边之下，随头身自然运动）
  const headPatterns=[
    part('PatternHeadTabby',0,0,[
      shape('Tabby Forehead C','M -4 -52 L 4 -52 L 3 -25 C 2 -21 -2 -21 -3 -25 Z','#6EA66535',null),
      shape('Tabby Forehead L','M -21 -50 L -13 -51 L -15 -28 C -16 -24 -21 -25 -22 -29 Z','#6EA66535',null),
      shape('Tabby Forehead R','M 13 -51 L 21 -50 L 22 -29 C 21 -25 16 -24 15 -28 Z','#6EA66535',null),
      shape('Tabby Cheek L1','M -96 4 C -86 3 -78 5 -76 9 C -84 11 -92 10 -99 8 Z','#5CA66535',null),
      shape('Tabby Cheek R1','M 96 4 C 86 3 78 5 76 9 C 84 11 92 10 99 8 Z','#5CA66535',null),
    ],{id:patternIds.Tabby?.head,opacity:0}),
    part('PatternHeadTuxedo',0,0,[
      shape('Tuxedo Cap L','M -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -32 -54 -14 -54 -2 -51 C -16 -28 -36 -14 -58 -12 C -74 -10 -86 -13 -94 -20 Z','#CC3E3843',null),
      shape('Tuxedo Ear R','M 46 -46 C 61 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 76 -22 60 -32 46 -46 Z','#CC3E3843',null),
    ],{id:patternIds.Tuxedo?.head,opacity:0}),
    part('PatternHeadCalico',0,0,[
      shape('Calico Orange L','M -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -30 -55 -10 -54 2 -50 C -14 -24 -38 -12 -64 -10 C -78 -9 -88 -13 -94 -20 Z','#E0EB9B58',null),
      shape('Calico Dark R','M 42 -47 C 59 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 98 -4 102 10 99 24 C 82 14 66 -6 42 -47 Z','#CC4A3E43',null),
    ],{id:patternIds.Calico?.head,opacity:0}),
    part('PatternHeadSiamese',0,0,[
      shape('Siamese Ear L','M -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -62 -36 -78 -25 -94 -20 Z','#C45C463B',null),
      shape('Siamese Ear R','M 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 74 -25 58 -35 44 -46 Z','#C45C463B',null),
      dot('Siamese Mask Outer',0,18,54,37,'#44634A3E'),
      dot('Siamese Mask Inner',0,22,34,23,'#58594237'),
    ],{id:patternIds.Siamese?.head,opacity:0}),
  ];

  const head=part('Head',0,-102,[
    part('Head Silhouette',0,0,[
      shape('Head Fill','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69 Z','body',null),
      shape('Head Crown Glow','M -42 -42 C -16 -50 18 -49 42 -39 C 16 -44 -16 -45 -42 -42 Z',glow,null),
      shape('Head Cheek Shade Left','M -95 44 C -91 58 -82 67 -68 72 C -83 62 -90 53 -95 44 Z',shadeSoft,null),
      shape('Head Cheek Shade Right','M 95 44 C 91 58 82 67 68 72 C 83 62 90 53 95 44 Z',shadeSoft,null),
      line('Head Outline','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69',4.4),
    ]),
    part('Frame Head',0,0,[
      shape('Frame Head Fur','M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69 C 74 96 -72 101 -94 69 Z','body',ink,4.4),
      shape('Frame Head Glow','M -42 -42 C -16 -50 18 -49 42 -39 C 16 -44 -16 -45 -42 -42 Z',glow,null),
    ],{opacity:0}),
    ...headPatterns,
    // 双层粉嫩内耳：浅粉外廓 + 暖粉耳窝
    shape('Inner Ear Left','M -86 -59 C -82 -63 -65 -46 -61 -42 C -74 -38 -82 -33 -87 -27 C -90 -38 -89 -53 -86 -59 Z','#F8CBD7',null),
    shape('Inner Ear Left Deep','M -84 -51 C -81 -54 -72 -44 -69 -40 C -77 -38 -81 -35 -85 -31 C -86 -38 -86 -47 -84 -51 Z','#EFA0B8',null),
    shape('Inner Ear Right','M 76 -51 C 82 -57 85 -57 86 -49 L 86 -26 C 78 -33 70 -38 61 -40 Z','#F8CBD7',null),
    shape('Inner Ear Right Deep','M 77 -45 C 81 -49 83 -49 84 -43 L 84 -29 C 78 -34 73 -37 68 -39 Z','#EFA0B8',null),
    // 柔光双层腮红 + 萌系小高光
    dot('Cheek Left Aura',-74,30,18,10,'#44F69DB7'),
    dot('Cheek Left',-74,30,13.5,7,'#D8F5A6BE'),
    line('Cheek Left Shine','M -79 28 L -76 26 M -72 28 L -69 26',1.6,'#88FFF0F5'),
    dot('Cheek Right Aura',74,30,18,10,'#44F69DB7'),
    dot('Cheek Right',74,30,13.5,7,'#D8F5A6BE'),
    line('Cheek Right Shine','M 69 28 L 72 26 M 76 28 L 79 26',1.6,'#88FFF0F5'),
    // 微弯灵动双胡须
    line('Whisker Left Upper','M -86 17 C -96 15 -103 14 -111 12',2.4,'#6E6274'),
    line('Whisker Left Lower','M -87 28 C -96 29 -103 31 -110 34',2.2,'#6E6274'),
    line('Whisker Right Upper','M 86 17 C 96 15 103 14 111 12',2.4,'#6E6274'),
    line('Whisker Right Lower','M 87 28 C 96 29 103 31 110 34',2.2,'#6E6274'),
  ]);

  const face=part('Face',0,0,[]);head.children.push(face);
  for(const [side,x] of [['Left',-39],['Right',39]]) {
    // 水汪汪的灵气猫眼：深可可瞳仁 + 主星点高光 + 角落副高光 + 暖色底反光
    face.children.push(part('Eye '+side,x,8,[
      dot('Eye '+side+' Pupil',0,0,7.2,9.8,'#39303C'),
      dot('Eye '+side+' Bottom Glow',0,4.8,4.2,2.1,'#38F5C2D4'),
      dot('Eye '+side+' Main Shine',-2.2,-3.4,2.6,2.8,'#FFFFFF'),
      dot('Eye '+side+' Sub Shine',2.3,2.8,1.2,1.2,'#D8FFFFFF'),
    ]));
    // 安详下弯闭眼（睡觉/打盹/眨眼）
    face.children.push(part('Lid '+side,x,10,[
      line('Closed '+side,'M -8 -1 C -4 4.5 4 4.5 8 -1',3.1,ink),
    ],{opacity:0}));
    // 幸福上弯月牙笑眼 ^ ^（摸头/伸懒腰/翻肚皮/挠肚皮）
    face.children.push(part('Happy Lid '+side,x,11,[
      line('Happy '+side,'M -9 2 C -4 -5.5 4 -5.5 9 2',3.2,ink),
    ],{opacity:0}));
  }

  const smile=part('Smile',0,0,[
    line('W Smile','M -20 26 C -19 40 -5 41.5 0 30.5 C 5 41.5 19 40 20 26',3.6,ink),
  ]);
  face.children.push(
    shape('Tiny Nose','M -5 20.5 C -5 18.8 5 18.8 5 20.5 C 5 23 2 25.5 0 25.5 C -2 25.5 -5 23 -5 20.5 Z','#E38AA6',null),
    dot('Nose Shine',-1.4,20.3,1.4,.9,'#99FFFFFF'),
    smile,
    part('Mouth Open',0,39,[
      dot('Mouth inside',0,0,10.5,14.5,'#724354'),
      dot('Mouth deep',0,-3,7.5,8.5,'#57303F'),
      shape('Mouth rim',ellipse(0,0,10.5,14.5),null,ink,2.6),
    ],{opacity:0}),
    part('Tongue',0,48,[
      dot('Tongue shape',0,0,6.6,7.4,'#F593B0'),
      dot('Tongue shine',-1.8,-1.8,2.1,1.5,'#66FFE6EE'),
    ],{opacity:0}),
  );

  // 5 套精修配饰
  const accessories=part('Accessories',0,0,[]);head.children.push(accessories);
  function costume(name,children){const c=part(name,0,0,children,{id:costumeIds[name],opacity:0});accessories.children.push(c);return c;}
  costume('Bow',[
    shape('Bow ribbon left','M -6 -45 L -23 -27 L -13 -24 L -3 -41 Z','#DF6691','#8D425E',2.3),
    shape('Bow ribbon right','M 6 -45 L 23 -27 L 13 -24 L 3 -41 Z','#DF6691','#8D425E',2.3),
    shape('Bow left','M -2 -51 C -26 -75 -43 -64 -34 -42 C -27 -30 -12 -38 -2 -51 Z','#F078A2','#8D425E',2.7),
    shape('Bow right','M 2 -51 C 26 -75 43 -64 34 -42 C 27 -30 12 -38 2 -51 Z','#F078A2','#8D425E',2.7),
    line('Bow crease left','M -9 -51 C -17 -54 -22 -52 -25 -48',2.1,'#B84F75'),
    line('Bow crease right','M 9 -51 C 17 -54 22 -52 25 -48',2.1,'#B84F75'),
    dot('Bow shine left',-18,-58,5,2.4,'#55FFFFFF'),
    dot('Bow shine right',18,-58,5,2.4,'#55FFFFFF'),
    shape('Bow knot',ellipse(0,-50,8,9),'#FFBBD0','#8D425E',2.4),
    dot('Bow knot shine',-2.2,-53,2.4,1.8,'#77FFFFFF'),
  ]);
  costume('Hat',[
    shape('Hat brim',ellipse(0,-51,58,8.5),'#45517A','#313959',2.6),
    shape('Hat top','M -36 -53 L -34 -88 C -33 -101 -20 -103 0 -103 C 20 -103 33 -101 34 -88 L 36 -53 Z','#616E9E','#3E476E',2.7),
    shape('Hat crown shine','M -24 -94 C -10 -98 10 -98 22 -94 C 10 -93 -10 -93 -24 -94 Z','#38FFFFFF',null),
    shape('Hat band','M -35 -67 L 35 -67 L 35 -54 L -35 -54 Z','#F0B27A','#9E6F45',2.1),
    shape('Hat buckle','M 8 -65 L 22 -65 L 22 -56 L 8 -56 Z','#FFE08A','#9E6F45',2),
  ]);
  costume('Crown',[
    shape('Crown points','M -41 -53 L -47 -89 L -21 -74 L 0 -105 L 21 -74 L 47 -89 L 41 -53 Z','#FFD25E','#A3762D',2.7),
    shape('Crown shine','M -31 -61 L -24 -79 L -16 -72 L -23 -61 Z','#55FFF7CC',null),
    shape('Crown base','M -41 -58 L 41 -58 L 41 -51 L -41 -51 Z','#F2B83B','#A3762D',2.2),
    dot('Crown tip left',-47,-90,4.5,4.5,'#FFE38A'),
    dot('Crown tip center',0,-106,5.5,5.5,'#FFE38A'),
    dot('Crown tip right',47,-90,4.5,4.5,'#FFE38A'),
    shape('Crown gem',ellipse(0,-72,6.5,8.2),'#EE6F96','#9B3E5C',2),
    dot('Crown gem shine',-1.8,-74.5,2,2.3,'#88FFFFFF'),
    dot('Crown pearl left',-22,-64,3.5,3.5,'#89C7F5'),
    dot('Crown pearl right',22,-64,3.5,3.5,'#89C7F5'),
  ]);
  costume('Flower',[
    shape('Flower leaf A','M 50 -22 C 40 -20 35 -10 43 -4 C 52 -5 56 -14 50 -22 Z','#87C78D','#4D8053',2),
    shape('Flower leaf B','M 82 -19 C 92 -14 96 -4 87 1 C 79 -1 76 -11 82 -19 Z','#87C78D','#4D8053',2),
    ...Array.from({length:5},(_,i)=>{
      const a=i*2*Math.PI/5-Math.PI/2;
      return shape('Flower petal '+i,ellipse(68+13*Math.cos(a),-30+13*Math.sin(a),9.5,9.5),'#F797B8','#B8587B',2);
    }),
    shape('Flower center',ellipse(68,-30,7.8,7.8),'#FFD96E','#C29434',2),
    dot('Flower center shine',65.5,-32.5,2.4,2,'#88FFFFFF'),
  ]);
  costume('Scarf',[
    shape('Scarf tail back','M 18 74 L 38 78 L 48 112 L 29 109 Z','#D97458','#964B39',2.5),
    shape('Scarf tail','M 2 75 L 25 80 L 35 119 L 13 114 Z','#EB886B','#964B39',2.6),
    line('Scarf tail stripe','M 10 101 L 29 105',3.2,'#FFF0DC'),
    line('Scarf fringe A','M 16 115 L 14 124',2.2,'#964B39'),
    line('Scarf fringe B','M 24 117 L 23 126',2.2,'#964B39'),
    line('Scarf fringe C','M 31 118 L 32 127',2.2,'#964B39'),
    shape('Scarf band','M -78 62 C -46 83 42 84 78 61 L 76 78 C 43 99 -47 98 -77 79 Z','#F39C7B','#964B39',2.7),
    line('Scarf knit A','M -48 76 L -50 88',2.4,'#FFF0DC'),
    line('Scarf knit B','M 0 83 L 0 94',2.4,'#FFF0DC'),
    line('Scarf knit C','M 46 77 L 48 88',2.4,'#FFF0DC'),
  ]);
  // 新增 5 款高颜值配饰：墨镜、耳机、小铃铛、睡帽、雨帽
  costume('Sunglasses',[
    line('Glass arm L','M -74 3 L -96 -2',3.2,'#443A46'),
    line('Glass arm R','M 74 3 L 96 -2',3.2,'#443A46'),
    line('Glass bridge','M -12 5 C -5 1 5 1 12 5',3.4,'#D6A560'),
    shape('Glass lens L',ellipse(-39,8,27,19),'#D938303D','#443A46',3.2),
    shape('Glass lens R',ellipse(39,8,27,19),'#D938303D','#443A46',3.2),
    line('Glass shine L','M -52 1 L -39 -5 M -45 8 L -30 0',2.8,'#88FFFFFF'),
    line('Glass shine R','M 26 1 L 39 -5 M 33 8 L 48 0',2.8,'#88FFFFFF'),
  ]);
  costume('Headphones',[
    line('Headband outer','M -104 -2 C -106 -76 -54 -102 0 -102 C 54 -102 106 -76 104 -2',10.5,'#463C49'),
    line('Headband main','M -104 -2 C -106 -76 -54 -102 0 -102 C 54 -102 106 -76 104 -2',6.5,'#F5A4BE'),
    line('Headband shine','M -48 -93 C -18 -99 18 -99 48 -93',2.6,'#88FFFFFF'),
    shape('Ear pad L',ellipse(-99,6,11,24),'#FFF6F8','#463C49',2.8),
    shape('Ear cup L',ellipse(-109,6,12,21),'#EB7EA3','#463C49',2.8),
    dot('Ear shine L',-111,-2,3.2,7,'#66FFFFFF'),
    shape('Ear pad R',ellipse(99,6,11,24),'#FFF6F8','#463C49',2.8),
    shape('Ear cup R',ellipse(109,6,12,21),'#EB7EA3','#463C49',2.8),
    dot('Ear shine R',107,-2,3.2,7,'#66FFFFFF'),
  ]);
  costume('Bell',[
    shape('Collar band','M -74 64 C -42 84 42 84 74 64 L 72 75 C 40 94 -40 94 -72 75 Z','#E56684','#8C364D',2.6),
    shape('Bell body',ellipse(0,92,15,15),'#FFD662','#A6782B',2.6),
    line('Bell ridge','M -14 91 C -5 95 5 95 14 91',2.2,'#A6782B'),
    dot('Bell hole',0,98,3.2,3.2,'#6E4E1B'),
    line('Bell slit','M 0 98 L 0 106',2.2,'#6E4E1B'),
    dot('Bell shine',-5,86,4,3,'#99FFFFFF'),
  ]);
  costume('Nightcap',[
    shape('Cap body','M -46 -48 C -36 -96 14 -116 62 -92 C 80 -83 92 -66 94 -52 C 74 -62 56 -64 46 -50 Z','#7D89C6','#4A538A',2.8),
    shape('Cap brim','M -52 -46 C -22 -60 24 -60 52 -46 C 54 -36 46 -32 34 -34 C 12 -39 -12 -39 -34 -34 C -46 -32 -54 -36 -52 -46 Z','#FFFBF7','#8A8296',2.5),
    dot('Cap pom',95,-48,12,12,'#FFFBF7'),
    shape('Cap pom ring',ellipse(95,-48,12,12),null,'#8A8296',2.4),
    dot('Cap star A',-8,-76,4,4,'#FFE382'),
    dot('Cap star B',26,-84,3.2,3.2,'#FFE382'),
    dot('Cap star C',56,-74,2.8,2.8,'#FFE382'),
  ]);
  costume('Rainhat',[
    shape('Rain brim','M -76 -43 C -42 -60 42 -60 76 -43 C 68 -30 42 -28 0 -30 C -42 -28 -68 -30 -76 -43 Z','#F8CA46','#A87B22',2.7),
    shape('Rain dome','M -48 -46 C -46 -88 -24 -100 0 -100 C 24 -100 46 -88 48 -46 Z','#FFDC6B','#A87B22',2.7),
    shape('Rain band','M -48 -54 C -20 -60 20 -60 48 -54 L 49 -45 C 20 -51 -20 -51 -49 -45 Z','#62B0E8','#3A76A8',2.1),
    shape('Rain shine','M -30 -86 C -16 -94 4 -94 18 -88 C 2 -88 -14 -86 -30 -86 Z','#66FFFFFF',null),
    shape('Rain drop','M 28 -78 C 28 -78 35 -68 35 -64 C 35 -60 32 -57 28 -57 C 24 -57 21 -60 21 -64 C 21 -68 28 -78 28 -78 Z','#89CEF8','#3A76A8',1.8),
  ]);

  const lickTongue=part('Lick Tongue',-3,-57,[
    dot('Licking tip',0,0,5,6.6,'#F593B0'),
    dot('Licking shine',-1.2,-1.6,1.6,1.8,'#77FFEAF0'),
  ],{opacity:0,rotation:.35});

  const frameArt=buildFrameArt(definitions);

  // 暖奶油手账风举牌（保持外框区域一致，确保 SignTextView 完美对齐）
  const sign=part('Sign Board',0,0,[
    shape('Sign shadow','M -110 14 L 110 14 C 117 14 122 18 122 25 L 122 115 C 122 122 117 126 110 126 L -110 126 C -117 126 -122 122 -122 115 L -122 25 C -122 18 -117 14 -110 14 Z','#EBD9BB',null),
    shape('Sign card','M -112 8 L 112 8 C 119 8 122 12 122 19 L 122 110 C 122 117 119 121 112 121 L -112 121 C -119 121 -122 117 -122 110 L -122 19 C -122 12 -119 8 -112 8 Z','#FFF8E8',ink,3.8),
    shape('Sign inner frame','M -102 18 L 102 18 C 107 18 110 21 110 26 L 110 103 C 110 108 107 111 102 111 L -102 111 C -107 111 -110 108 -110 103 L -110 26 C -110 21 -107 18 -102 18 Z',null,'#EFDFC0',1.8),
    dot('Sign pin left',-94,25,3.2,3.2,'#E9A2B8'),
    dot('Sign pin right',94,25,3.2,3.2,'#E9A2B8'),
    line('Sign underline','M -82 99 L 82 99',2,'#E8D3B0'),
  ],{opacity:0});

  const pet=part('Pet',240,305,[tail,body,belly,hindLeft,hindRight,frameArt.solo,head,sign,foreLeft,foreRight,lickTongue]);

  // 三个独立可错峰漂浮的 Z 节点
  const zzz=part('Zzz',0,0,[
    part('Sleep Z 0',314,218,[line('Sleep Z0 path','M -6 -7 L 6 -7 L -6 7 L 6 7',2.8,'#8F93BA')]),
    part('Sleep Z 1',340,192,[line('Sleep Z1 path','M -7.5 -9 L 7.5 -9 L -7.5 9 L 7.5 9',3.1,'#8F93BA')]),
    part('Sleep Z 2',368,165,[line('Sleep Z2 path','M -9 -11 L 9 -11 L -9 11 L 9 11',3.3,'#8F93BA')]),
  ],{opacity:0});

  return {pet,zzz,skin,tail,head,body,arms,frameArt};
}
