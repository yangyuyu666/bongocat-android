import paper from 'paper';
import {paths} from './vector.mjs';

new paper.Project();
const xy=p=>[p.x,p.y].map(v=>Math.round(v*10000)/10000).join(' ');
const head='M -94 69 C -112 43 -115 8 -94 -20 C -101 -43 -98 -66 -89 -80 C -81 -84 -60 -59 -48 -49 C -22 -57 15 -55 44 -46 C 60 -55 77 -70 87 -72 C 96 -67 97 -39 90 -18 C 109 11 114 43 95 69 C 74 96 -72 101 -94 69 Z';

function path(d,transform=[0,0,1,0]) {
  const [x,y,scale,angle]=transform;
  const mapped=p=>[x+scale*(p[0]*Math.cos(angle)-p[1]*Math.sin(angle)),y+scale*(p[0]*Math.sin(angle)+p[1]*Math.cos(angle))];
  return new paper.Path({insert:false,closed:true,segments:paths(d)[0].vertices.map(v=>{
    const point=mapped(v.p),before=mapped(v.in||v.p),after=mapped(v.out||v.p);
    return new paper.Segment(point,[before[0]-point[0],before[1]-point[1]],[after[0]-point[0],after[1]-point[1]]);
  })});
}

// 合并填色区域，再沿合并后的外边界描线，去掉部件相交处的重复边线。
export function unifiedSilhouette(parts,headPose) {
  let united=path(head,headPose);
  for(const d of parts){
    const next=path(d),previous=united;
    united=previous.unite(next,{insert:false});
    previous.remove();next.remove();
  }
  const contours=united.children||[united];
  const outer=contours.reduce((largest,item)=>Math.abs(item.area)>Math.abs(largest.area)?item:largest);
  // 填实皮毛内部的封闭小缝，外侧各个轮廓仍分别保留。
  const result=contours.filter(item=>Math.abs(item.area)>.01&&Math.sign(item.area)===Math.sign(outer.area)).map(item=>{
    let d='M '+xy(item.segments[0].point);
    for(let i=0;i<item.segments.length;i++){
      const a=item.segments[i],b=item.segments[(i+1)%item.segments.length];
      d+=` C ${xy(a.point.add(a.handleOut))} ${xy(b.point.add(b.handleIn))} ${xy(b.point)}`;
    }
    return d+' Z';
  }).join(' ');
  united.remove();
  return result;
}
