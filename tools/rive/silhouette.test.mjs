import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {buildFrameArt,poseAt} from './frame-poses.mjs';
import {addInteractions} from './interactions.mjs';
import {addSleepPoses} from './sleep.mjs';
import {paths} from './vector.mjs';

test('趴姿和过渡的头身四肢共用一个连续闭合轮廓',()=>{
  const behavior=JSON.parse(fs.readFileSync(new URL('./behavior.json',import.meta.url),'utf8'));
  addSleepPoses(behavior);
  addInteractions(behavior);
  const art=buildFrameArt(behavior.animations);
  const drawings=new Map(art.solo.children.map(item=>[item.id,item]));
  let checked=0;
  for(const name of ['Lie','Sleep','LieCrawlWait','LieCrawl','Sit_To_Crawl','Wake','LieCrawlWake','Drowsy','ProneDrowsy','Crawl','EdgeRun']){
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

test('母鸡蹲睡姿正面朝向，四爪收在胸腹内，呼吸循环闭合',()=>{
  for(let frame=0;frame<=180;frame+=4){
    const pose=poseAt('Sleep',frame,180),[cx,cy,rx,ry]=pose.body;
    assert.ok(Math.abs(pose.head[0]-cx)<1,'头和身体应正面居中');
    assert.equal(pose.head[3],0,'睡觉时不侧歪头');
    for(const type of ['fore','hind'])for(const limb of pose[type]){
      assert.ok(Math.abs(limb[4]-cx)+27<rx,'爪子不能伸出身体两侧');
      assert.ok(limb[5]+27<cy+ry,'爪子不能伸出身体底部');
    }
  }
  assert.deepEqual(poseAt('Sleep',0,180),poseAt('Sleep',180,180));
  assert.notDeepEqual(poseAt('Sleep',0,180).head,poseAt('Sleep',90,180).head,'睡着时保留轻微呼吸');
});

test('两种入睡衔接同一个母鸡蹲，清醒趴姿保留侧身起身',()=>{
  const sleep=poseAt('Sleep',0,180);
  for(const name of ['Drowsy','ProneDrowsy'])assert.deepEqual(poseAt(name,360,360),sleep,name);
  assert.deepEqual(poseAt('Wake',0,54),sleep);
  assert.ok(poseAt('LieCrawlWake',0,54).head[0]<-40);
  assert.deepEqual(poseAt('ProneDrowsy',0,360).head,poseAt('Lie',0,120).head);
});
