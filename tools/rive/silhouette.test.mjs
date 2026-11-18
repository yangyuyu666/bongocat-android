import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {buildFrameArt} from './frame-poses.mjs';
import {addInteractions} from './interactions.mjs';
import {paths} from './vector.mjs';

test('趴姿和过渡的头身四肢共用一个连续闭合轮廓',()=>{
  const behavior=JSON.parse(fs.readFileSync(new URL('./behavior.json',import.meta.url),'utf8'));
  addInteractions(behavior);
  const art=buildFrameArt(behavior.animations);
  const drawings=new Map(art.solo.children.map(item=>[item.id,item]));
  let checked=0;
  for(const name of ['Lie','Sleep','LieCrawlWait','LieCrawl','Sit_To_Crawl','Wake','LieCrawlWake','Drowsy','Crawl','EdgeRun']){
    const before=checked;
    for(const row of art.animations.get(name)){
      if(!row.pose)continue;
      assert.equal(row.pose.unified,true,name+' '+row.frame);
      const silhouette=drawings.get(row.id).children.find(item=>item.name.endsWith('Unified Fur'));
      assert.ok(silhouette,name+' '+row.frame);
      const contours=paths(silhouette.d);
      assert.equal(contours.length,1,name+' '+row.frame+' 出现分离区域或孔洞');
      assert.equal(contours[0].closed,true);
      for(const vertex of contours[0].vertices){
        assert.ok(vertex.p.every(Number.isFinite));
        assert.ok(vertex.p[0]+240>15&&vertex.p[0]+240<485,name+' 横向越界');
        assert.ok(vertex.p[1]+305>15&&vertex.p[1]+305<485,name+' 纵向越界');
      }
      checked++;
    }
    assert.ok(checked>before,name+' 缺少趴姿画面');
  }
  console.log('已检查 '+checked+' 个动画采样帧');
});

test('翻身和藏身继续使用原来的分层姿势',()=>{
  const behavior=JSON.parse(fs.readFileSync(new URL('./behavior.json',import.meta.url),'utf8'));
  addInteractions(behavior);
  const art=buildFrameArt(behavior.animations);
  for(const name of ['Roll','BellyTickle','EdgeTuck'])for(const row of art.animations.get(name)){
    if(row.pose)assert.equal(row.pose.unified,false);
  }
});
