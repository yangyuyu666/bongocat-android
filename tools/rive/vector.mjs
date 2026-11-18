export const number = n => Number(n.toFixed(6));
export const escape = s => String(s).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
export const tag = (type, attrs = {}, children = '') => `<${type}${Object.entries(attrs).map(([k,v]) => ` ${k}="${escape(v)}"`).join('')}${children ? `>${children}</${type}>` : '/>'}`;
let nextId = 40000;
export const nodes = new Map();
export function node(name, x = 0, y = 0, children = [], extra = {}) {
  const result = {name, id:`0:${nextId++}`, x, y, children, ...extra};
  nodes.set(name, result);
  return result;
}
export function shape(name, d, fill = 'body', stroke = '#49414B', width = 4.5, extra = {}) {
  return {name, id:`0:${nextId++}`, d, fill, stroke, width, ...extra};
}
export function ellipse(x,y,rx,ry) {
  const k=.552284749831;
  return `M ${x+rx} ${y} C ${x+rx} ${y+k*ry} ${x+k*rx} ${y+ry} ${x} ${y+ry} C ${x-k*rx} ${y+ry} ${x-rx} ${y+k*ry} ${x-rx} ${y} C ${x-rx} ${y-k*ry} ${x-k*rx} ${y-ry} ${x} ${y-ry} C ${x+k*rx} ${y-ry} ${x+rx} ${y-k*ry} ${x+rx} ${y} Z`;
}
export function paths(d) {
  const tokens=d.match(/[MCLZ]|[-+]?(?:\d*\.)?\d+(?:e[-+]?\d+)?/gi);
  let i=0,current;const result=[];const point=()=>[Number(tokens[i++]),Number(tokens[i++])];
  while(i<tokens.length) {
    const cmd=tokens[i++];
    if(cmd==='M'){current={vertices:[{p:point()}],closed:false};result.push(current);}
    else if(cmd==='L')current.vertices.push({p:point()});
    else if(cmd==='C'){
      const a=point(),b=point(),p=point();current.vertices.at(-1).out=a;
      if(tokens[i]==='Z' && Math.hypot(p[0]-current.vertices[0].p[0],p[1]-current.vertices[0].p[1])<1e-6)current.vertices[0].in=b;
      else current.vertices.push({p,in:b});
    } else if(cmd==='Z')current.closed=true;
    else throw new Error(`Unsupported SVG command: ${cmd}`);
  }
  return result;
}
const argb=c=>c==='body'?'FFFFFFFF':(c.length===7?'FF':'')+c.slice(1);
function paint(fill,stroke,width) {
  let result='';
  if(fill)result+=tag('Fill',{},tag('SolidColor',{colorValue:argb(fill)},fill==='body'?tag('DataBindContext',{sourcePathIds:'0:126-0:127',propertyKey:37}):''));
  if(stroke)result+=tag('Stroke',{thickness:width,cap:'round',join:'round'},tag('SolidColor',{colorValue:argb(stroke)}));
  return result;
}
export function renderRml(item, skin = null) {
  if(item.children) {
    const attrs={name:item.name,id:item.id,x:item.x,y:item.y};
    for(const key of ['rotation','scaleX','scaleY','opacity','activeComponentId'])if(item[key]!==undefined)attrs[key]=item[key];
    return tag(item.type||'Node',attrs,(item.extraXml||'')+[...item.children].reverse().map(s=>renderRml(s,skin)).join('\n'));
  }
  const geometries=paths(item.d).map(p=>{
    const area=p.vertices.reduce((s,v,i)=>{const next=p.vertices[(i+1)%p.vertices.length].p;return s+v.p[0]*next[1]-next[0]*v.p[1];},0);
    const vertices=p.vertices.map((v,index)=>{
      const a=v.in||v.p,b=v.out||v.p;
      const attrs={x:v.p[0],y:v.p[1],inRotation:number(Math.atan2(a[1]-v.p[1],a[0]-v.p[0])),inDistance:number(Math.hypot(a[0]-v.p[0],a[1]-v.p[1])),outRotation:number(Math.atan2(b[1]-v.p[1],b[0]-v.p[0])),outDistance:number(Math.hypot(b[0]-v.p[0],b[1]-v.p[1]))};
      return tag('CubicDetachedVertex',attrs,item.skinned?(item.skin||skin).weightXml(v.p,a,b,item.weights?.[index]):'');
    }).join('');
    return tag('PointsPath',{name:item.name+' Path',isClosed:p.closed,isClockwise:area>=0},vertices+(item.skinned?(item.skin||skin).xml():''));
  }).join('');
  return tag('Shape',{name:item.name,id:item.id},geometries+paint(item.fill,item.stroke,item.width));
}
export function renderSvg(item) {
  if(item.children) {
    const transform=`translate(${item.x} ${item.y}) rotate(${(item.rotation||0)*180/Math.PI}) scale(${item.scaleX??1} ${item.scaleY??1})`;
    return `<g id="${escape(item.name.replaceAll(' ','-'))}" transform="${transform}" opacity="${item.opacity??1}">${item.children.map(renderSvg).join('\n')}</g>`;
  }
  return `<path id="${escape(item.name.replaceAll(' ','-'))}" d="${item.d}" fill="${item.fill==='body'?'#FFFFFF':item.fill||'none'}" stroke="${item.stroke||'none'}" stroke-width="${item.width}" stroke-linejoin="round" stroke-linecap="round"/>`;
}

// 按骨骼中心线计算混合权重，也允许单独指定顶点和控制柄的绑定。
export function tailSkin(joints, origin, options={}) {
  const names=options.names||['Tail Base','Tail Middle','Tail Tip'];
  const label=options.label||'Tail';
  const bones=joints.slice(0,-1).map((p,i)=>({id:`0:${(options.firstId||39000)+i}`,p,angle:Math.atan2(joints[i+1][1]-p[1],joints[i+1][0]-p[0]),length:Math.hypot(joints[i+1][0]-p[0],joints[i+1][1]-p[1])}));
  const cumulative=[0];bones.forEach(b=>cumulative.push(cumulative.at(-1)+b.length));
  const centers=bones.map((b,i)=>cumulative[i]+b.length/2);
  function weight(point){
    let best={d:Infinity,s:0};
    bones.forEach((b,i)=>{const dx=joints[i+1][0]-b.p[0],dy=joints[i+1][1]-b.p[1];const t=Math.max(0,Math.min(1,((point[0]-b.p[0])*dx+(point[1]-b.p[1])*dy)/b.length**2));const d=Math.hypot(point[0]-b.p[0]-t*dx,point[1]-b.p[1]-t*dy);if(d<best.d)best={d,s:cumulative[i]+t*b.length};});
    if(best.s<=centers[0])return {values:255,indices:1};
    if(best.s>=centers[2])return {values:255,indices:3};
    const i=best.s<centers[1]?0:1;const w=Math.round((best.s-centers[i])/(centers[i+1]-centers[i])*255);
    return {values:(255-w)|(w<<8),indices:(i+1)|((i+2)<<8)};
  }
  const assigned=(point,selector)=>{
    if(selector===undefined)return weight(point);
    if(typeof selector==='number')return {values:255,indices:selector};
    const w=Math.round(selector[2]*255);
    return {values:(255-w)|(w<<8),indices:selector[0]|(selector[1]<<8)};
  };
  function boneXml(i=0){const b=bones[i];return tag(i?'Bone':'RootBone',{id:b.id,name:names[i],...(i?{}:{x:b.p[0],y:b.p[1]}),length:number(b.length),rotation:number(b.angle-(bones[i-1]?.angle||0))},i<2?boneXml(i+1):'');}
  return {bones,boneXml,weightXml(p,a,b,assignment={}){const w=assigned(p,assignment.p),wi=assigned(a,assignment.in),wo=assigned(b,assignment.out);return tag('CubicWeight',{...w,inValues:wi.values,inIndices:wi.indices,outValues:wo.values,outIndices:wo.indices});},xml(){return tag('Skin',{name:label+' Skin',tx:origin[0],ty:origin[1]},bones.map((b,i)=>tag('Tendon',{boneId:b.id,name:`${label} Tendon ${i+1}`,xx:number(Math.cos(b.angle)),xy:number(Math.sin(b.angle)),yx:number(-Math.sin(b.angle)),yy:number(Math.cos(b.angle)),tx:b.p[0]+origin[0],ty:b.p[1]+origin[1]})).join(''));}};
}
