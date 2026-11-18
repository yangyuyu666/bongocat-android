import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {tag,renderRml,renderSvg} from './rive/vector.mjs';
import {buildArt} from './rive/bongocat-art.mjs';
import {buildAnimations} from './rive/bongocat-motion.mjs';
import {addInteractions} from './rive/interactions.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
// 沿用原有状态图和绑定 ID，只替换角色部件与动作轨道。
const behavior=JSON.parse(fs.readFileSync(path.join(root,'tools/rive/behavior.json'),'utf8'));
addInteractions(behavior);
const art=buildArt(behavior.costumeIds,behavior.animations);
art.tail.extraXml=art.skin.boneXml();
const scene=tag('Rive',{version:1,kind:'fragment'},tag('Artboard',behavior.artboard,
  behavior.support.join('\n')+renderRml(art.zzz)+renderRml(art.pet,art.skin)+
  buildAnimations(behavior.animations,art.skin,art.arms,art.frameArt)+behavior.stateMachine)+behavior.viewModel);
fs.writeFileSync(path.join(root,'rive/scene.rml'),'<?xml version="1.0" encoding="utf-8"?>\n'+scene+'\n');
fs.writeFileSync(path.join(root,'rive/bongocat-layered.svg'),
  '<svg xmlns="http://www.w3.org/2000/svg" width="500" height="500" viewBox="0 0 500 500">\n'+
  '<title>BongoCat — layered vector source</title>\n'+renderSvg(art.pet)+renderSvg(art.zzz)+'\n</svg>\n');
console.log(`已生成 ${art.frameArt.count} 张矢量姿势及 ${behavior.animations.length} 条动画；保留原有动作并加入部位交互与横屏探头。`);
