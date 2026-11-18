import {tag} from './vector.mjs';

const model='0:126',stateProperty='0:263';
const fields={edgeMode:['Boolean','0:90001'],edgePeek:['Boolean','0:90002'],actionProgress:['Number','0:90003'],holdSign:['Boolean','0:90004'],
  petHead:['Trigger','0:90100'],boopNose:['Trigger','0:90101'],tickleBelly:['Trigger','0:90102'],
  teaseHead:['Trigger','0:90103'],petPeek:['Trigger','0:90104'],touchActivity:['Trigger','0:90105']};
const keys={Boolean:634,Number:636,Trigger:686};
const definitions=[['HeadPet',17,90],['NoseBoop',18,78],['BellyTickle',19,144],['Shy',20,108],
  ['EdgeRun',21,48,'loop'],['EdgeTuck',22,24],['EdgePeek',23,180,'loop'],['PeekPet',24,72],
  ['RaiseSign',25,24],['HoldSign',26,180,'loop']];
const stateId=code=>'0:'+(90040+code-17),animationId=code=>'0:'+(90020+code-17);

function condition(name,value=true,opValue='equal') {
  const [type,id]=name==='state'?['Number',stateProperty]:fields[name];
  return tag('TransitionViewModelCondition',{opValue},tag('TransitionPropertyViewModelComparator',{},
    tag('BindableProperty'+type,{},tag('DataBindContext',{sourcePathIds:model+'-'+id,propertyKey:keys[type]})))+
    tag('TransitionValue'+type+'Comparator',type==='Trigger'?{}:{value}));
}
const change=(property,value)=>tag('ListenerViewModelChange',{},tag('BindablePropertyNumber',{propertyValue:value},
  tag('DataBindContext',{sourcePathIds:model+'-'+property,propertyKey:636,direction:true})));
const transition=(target,conditions='',finished=false)=>tag('StateTransition',{
  stateToId:target,duration:0,...(finished?{enableExitTime:true,exitTimeIsPercetange:true,exitTime:100}:{enableEarlyExit:true})},conditions);
const exitEdge=()=>transition('0:264',condition('edgeMode',false));

export function addInteractions(behavior) {
  let declarations='',values='';
  for(const [name,[type,id]] of Object.entries(fields)){
    declarations+=tag('ViewModelProperty'+type,{name,id});
    values+=tag('ViewModelInstance'+type,{viewModelPropertyId:id,propertyValue:type==='Boolean'?false:0});
  }
  behavior.viewModel=behavior.viewModel.replace('<ViewModelInstance ',declarations+'<ViewModelInstance ')
    .replace('</ViewModelInstance>',values+'</ViewModelInstance>');
  behavior.support.push(tag('CustomPropertyGroup',{name:'Interaction output'},tag('CustomPropertyNumber',{
    id:'0:90010',name:'Action progress',propertyValue:0},tag('DataBindContext',{
    sourcePathIds:model+'-'+fields.actionProgress[1],propertyKey:243,direction:true}))));
  for(const [name,code,duration,loopValue='oneShot'] of definitions)behavior.animations.push({
    attributes:{name,id:animationId(code),fps:60,duration,loopValue},tracks:[]});
  // 原有动作和随机轮换只在普通模式推进，横屏使用独立的跑动与探头状态。
  behavior.stateMachine=behavior.stateMachine.replace(/<StateMachineLayer\b[^>]*name="Behavior"[^>]*>[\s\S]*?<\/StateMachineLayer>/,layer=>{
    layer=layer.replace(/<AnyState\b([^>]*?)\s*\/>/,'<AnyState$1></AnyState>');
    layer=layer.replace(/<(AnyState|AnimationState)\b[^>]*>[\s\S]*?<\/\1>/g,block=>block
      .replace(/<StateTransition\b([^>]*?)(\/?>)/g,(_,attrs,end)=>'<StateTransition'+attrs+'>'+condition('edgeMode',false)+(end==='/>'?'</StateTransition>':'')));
    const extra=transition(stateId(21),condition('edgeMode')+condition('state',21,'lessThan'))+
      [['petHead',17],['boopNose',18],['tickleBelly',19],['teaseHead',20]].map(([trigger,code])=>
        transition(stateId(code),condition('edgeMode',false)+condition(trigger)+(code===19?
          condition('state',13)+condition('actionProgress',.34,'greaterThanOrEqual')+condition('actionProgress',.66,'lessThanOrEqual'):''))).join('');
    layer=layer.replace('</AnyState>',extra+'</AnyState>');
    const states=definitions.map(([name,code])=>{
      let transitions;
      if(code<21)transitions=transition('0:264','',true);
      else if(code===21)transitions=exitEdge()+transition(stateId(22),condition('edgePeek'));
      else if(code===22)transitions=exitEdge()+transition(stateId(21),condition('edgePeek',false))+transition(stateId(23),'',true);
      else if(code===23)transitions=exitEdge()+transition(stateId(21),condition('edgePeek',false))+transition(stateId(24),condition('petPeek'));
      else if(code===24)transitions=exitEdge()+transition(stateId(21),condition('edgePeek',false))+transition(stateId(23),'',true);
      else transitions='';
      return tag('AnimationState',{id:stateId(code),animationId:animationId(code),reset:true,x:1100,y:(code-17)*120},change(stateProperty,code)+transitions);
    }).join('');
    layer=layer.replace('</StateMachineLayer>',states+'</StateMachineLayer>');
    // 举牌时暂停其他动作，收牌后回到原来的普通或横屏行为。
    layer=layer.replace(/<StateTransition\b([^>]*?)(\/?>)/g,(_,attrs,end)=>'<StateTransition'+attrs+'>'+condition('holdSign',false)+(end==='/>'?'</StateTransition>':''));
    layer=layer.replace('</AnyState>',transition(stateId(25),condition('holdSign')+condition('state',25,'lessThan'))+'</AnyState>');
    for(const code of [25,26]) {
      const start='<AnimationState id="'+stateId(code)+'"';
      const index=layer.indexOf(start),end=layer.indexOf('</AnimationState>',index);
      const exits=transition('0:264',condition('holdSign',false))+(code===25?transition(stateId(26),'',true):'');
      layer=layer.slice(0,end)+exits+layer.slice(end);
    }
    return layer;
  });
  // 部位点击只重置闲置计时，避免同时触发旧的抬爪动作。
  behavior.stateMachine=behavior.stateMachine.replace(/<StateMachineLayer\b[^>]*name="Inactivity clock"[^>]*>[\s\S]*?<\/StateMachineLayer>/,layer=>layer.replace(
    /<StateTransition\b[^>]*(?<!\/)>[\s\S]*?<\/StateTransition>/g,transition=>transition.includes('0:126-0:256')?
      transition+transition.replaceAll('0:126-0:256','0:126-'+fields.touchActivity[1]).replace(/ id="[^"]*"/g,''):transition));
}

export function actionProgressTrack(duration) {
  return tag('KeyedObject',{objectId:'0:90010'},tag('KeyedProperty',{property:'propertyValue'},
    tag('KeyFrameDouble',{frame:0,value:0,interpolationType:duration<=1?'hold':'linear'})+
    tag('KeyFrameDouble',{frame:duration,value:1,interpolationType:'linear'})));
}
