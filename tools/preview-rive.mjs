import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'build/visual-review');
const cli=process.env.RIVE_CLI||path.join(os.homedir(),'.rive/bin',process.platform==='win32'?'rive.exe':'rive');
fs.mkdirSync(output,{recursive:true});
const commands=[],manifest=[];
const add=(name,frames,trigger)=>{
  commands.push('reload');
  if(trigger)commands.push(`data ${trigger}=1`);
  const captures=[];
  for(let frame=0;frame<=frames;frame+=6){
    if(frame)commands.push('advance 6');
    const file=`${name}-${String(frame).padStart(3,'0')}.png`;
    commands.push(`screenshot ${path.join(output,file).replaceAll('\\','/')}`);
    captures.push({frame,file});
  }
  manifest.push({name,fps:10,captures});
};
// 所有预览都由 Rive 原生引擎渲染，复用同一个无头进程。
for(const [name,frames] of Object.entries({Blink:66,Lick:204,Tail:180,Stretch:216,Yawn:180,Look:192,Crawl:288,Roll:270,LieCrawl:318}))add(name,frames,'play'+name);
commands.push('reload','advance 98s',`screenshot ${output.replaceAll('\\','/')}/Sleep.png`);
for(let costume=0;costume<6;costume++){
  commands.push('reload',`data costume=${costume}`,'advance 1',`screenshot ${output.replaceAll('\\','/')}/costume-${costume}.png`);
}
commands.push('reload','data playLieCrawl=1','advance 120','viewport 750x500','fit cover',`screenshot ${output.replaceAll('\\','/')}/lie-wide.png`,'quit');
const commandFile=path.join(output,'commands.txt');
fs.writeFileSync(commandFile,commands.join('\n'));
fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify(manifest,null,2));
const result=spawnSync(cli,['rive','--commands='+commandFile],{cwd:root,encoding:'utf8',maxBuffer:8*1024*1024});
fs.writeFileSync(path.join(output,'capture.log'),result.stdout+result.stderr);
if(result.status!==0||/^err /m.test(result.stdout))throw new Error(`Rive 截图失败，请查看 ${output}/capture.log`);
console.log(`已输出 ${manifest.reduce((n,a)=>n+a.captures.length,0)+8} 张 Rive 实际渲染帧：${output}`);
