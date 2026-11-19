import {tag} from './vector.mjs';

const MODEL = '0:126';
const COSTUME_PROP = '0:306';
const PATTERN_PROP = '0:92100';

export const EXTRA_COSTUMES = [
  ['Sunglasses', 6, '0:92001', '0:92021', '0:92041'],
  ['Headphones', 7, '0:92002', '0:92022', '0:92042'],
  ['Bell', 8, '0:92003', '0:92023', '0:92043'],
  ['Nightcap', 9, '0:92004', '0:92024', '0:92044'],
  ['Rainhat', 10, '0:92005', '0:92025', '0:92045'],
];

export const PATTERNS = [
  ['None', 0, null, null, '0:92130', '0:92150'],
  ['Tabby', 1, '0:92111', '0:92121', '0:92131', '0:92151'],
  ['Tuxedo', 2, '0:92112', '0:92122', '0:92132', '0:92152'],
  ['Calico', 3, '0:92113', '0:92123', '0:92133', '0:92153'],
  ['Siamese', 4, '0:92114', '0:92124', '0:92134', '0:92154'],
];

export const WARDROBE_NODE_NAMES = [
  'Bow', 'Hat', 'Crown', 'Flower', 'Scarf',
  ...EXTRA_COSTUMES.map(([name]) => name),
  ...PATTERNS.filter(([, , h]) => h).flatMap(([name]) => ['PatternHead' + name, 'PatternBody' + name]),
];

function opacityTrack(objectId, value) {
  return tag('KeyedObject', {objectId},
    tag('KeyedProperty', {property: 'opacity'},
      tag('KeyFrameDouble', {frame: 0, value, interpolationType: 'linear'})));
}

function numberTransition(propId, value, stateToId) {
  return tag('StateTransition', {stateToId, duration: 0, enableEarlyExit: true},
    tag('TransitionViewModelCondition', {opValue: 'equal'},
      tag('TransitionPropertyViewModelComparator', {},
        tag('BindablePropertyNumber', {},
          tag('DataBindContext', {sourcePathIds: `${MODEL}-${propId}`, propertyKey: 636}))) +
      tag('TransitionValueNumberComparator', {value})));
}

export function addWardrobe(behavior) {
  // 1. 扩展配饰 ID 映射与原有 Costume 动画（保证切换回旧配饰时隐藏新配饰）。
  const baseIds = Object.values(behavior.costumeIds);
  for (const [name, , nodeId] of EXTRA_COSTUMES) behavior.costumeIds[name] = nodeId;
  const allCostumeNodeIds = [...baseIds, ...EXTRA_COSTUMES.map(([, , id]) => id)];

  for (const anim of behavior.animations) {
    if (anim.attributes.name.startsWith('Costume')) {
      for (const [, , nodeId] of EXTRA_COSTUMES) {
        anim.tracks.push(opacityTrack(nodeId, 0));
      }
    }
  }
  for (const [name, , activeNodeId, animId] of EXTRA_COSTUMES) {
    behavior.animations.push({
      attributes: {name: 'Costume' + name, id: animId, fps: 60, duration: 1, loopValue: 'oneShot'},
      tracks: allCostumeNodeIds.map(id => opacityTrack(id, id === activeNodeId ? 1 : 0)),
    });
  }
  behavior.stateMachine = behavior.stateMachine.replace(
    /<StateMachineLayer\b[^>]*name="Costume"[^>]*>[\s\S]*?<\/StateMachineLayer>/,
    layer => {
      const extraTransitions = EXTRA_COSTUMES.map(([, index, , , stateId]) =>
        numberTransition(COSTUME_PROP, index, stateId)).join('');
      const extraStates = EXTRA_COSTUMES.map(([, index, , animId, stateId]) =>
        tag('AnimationState', {id: stateId, animationId: animId, x: 180 + ((index - 6) % 3) * 220, y: 320 + Math.floor((index - 6) / 3) * 160})).join('');
      return layer
        .replace('</AnyState>', extraTransitions + '</AnyState>')
        .replace('</StateMachineLayer>', extraStates + '</StateMachineLayer>');
    }
  );

  // 2. 新增 pattern ViewModel 属性与独立 Pattern 状态机层（控制 4 种猫咪花色纹理）。
  behavior.viewModel = behavior.viewModel
    .replace('<ViewModelInstance ', tag('ViewModelPropertyNumber', {name: 'pattern', id: PATTERN_PROP}) + '<ViewModelInstance ')
    .replace('</ViewModelInstance>', tag('ViewModelInstanceNumber', {viewModelPropertyId: PATTERN_PROP, propertyValue: 0}) + '</ViewModelInstance>');

  const allPatternNodeIds = PATTERNS.filter(([, , h]) => h).flatMap(([, , h, b]) => [h, b]);
  for (const [name, , headId, bodyId, animId] of PATTERNS) {
    behavior.animations.push({
      attributes: {name: 'Pattern' + name, id: animId, fps: 60, duration: 1, loopValue: 'oneShot'},
      tracks: allPatternNodeIds.map(id => opacityTrack(id, id === headId || id === bodyId ? 1 : 0)),
    });
  }

  const patternLayer = tag('StateMachineLayer', {name: 'Pattern'},
    tag('EntryState', {x: 0, y: 0},
      tag('StateTransition', {stateToId: PATTERNS[0][5], duration: 0})) +
    tag('AnyState', {x: 0, y: 150},
      PATTERNS.map(([, index, , , , stateId]) => numberTransition(PATTERN_PROP, index, stateId)).join('')) +
    tag('ExitState', {x: 0, y: -300}) +
    PATTERNS.map(([, index, , , animId, stateId]) =>
      tag('AnimationState', {id: stateId, animationId: animId, x: 180 + (index % 3) * 220, y: Math.floor(index / 3) * 160})).join('')
  );
  behavior.stateMachine = behavior.stateMachine.replace('</StateMachine>', patternLayer + '</StateMachine>');
  behavior.patternIds = Object.fromEntries(PATTERNS.filter(([, , h]) => h).map(([name, , h, b]) => [name, {head: h, body: b}]));
}
