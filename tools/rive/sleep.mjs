// 趴着入睡使用独立过渡，睡醒与清醒趴姿起身分别保留自己的朝向。
export function addSleepPoses(behavior) {
  const drowsy=behavior.animations.find(item=>item.attributes.name==='Drowsy');
  behavior.animations.push({attributes:{...drowsy.attributes,name:'ProneDrowsy',id:'0:91000'},
    tracks:drowsy.tracks.map(track=>track.replace(/ id="[^"]*"/g,''))});
  const proneIds=new Set(behavior.animations.filter(item=>['Lie','LieCrawl','LieCrawlWait'].includes(item.attributes.name)).map(item=>item.attributes.id));
  let proneState;
  behavior.stateMachine=behavior.stateMachine.replace(/<AnimationState\b[^>]*>[\s\S]*?<\/AnimationState>/g,block=>{
    const id=block.match(/animationId="([^"]+)"/)?.[1];
    if(id===drowsy.attributes.id){
      proneState=block.replace(/ id="[^"]*"/g,'').replace('animationId="'+id+'"','animationId="0:91000"')
        .replace('<AnimationState ','<AnimationState id="0:91001" ')
        .replace(/ x="[^"]*"/,' x="1450"').replace(/ y="[^"]*"/,' y="1200"');
    }
    if(proneIds.has(id))block=block.replaceAll('stateToId="0:261"','stateToId="0:91001"').replaceAll('stateToId="0:287"','stateToId="0:25076"');
    return block;
  });
  if(!proneState)throw new Error('缺少入睡状态');
  behavior.stateMachine=behavior.stateMachine.replace('</StateMachineLayer>',proneState+'</StateMachineLayer>');
}
