import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const lock=JSON.parse(fs.readFileSync(new URL('../upstreams.lock.json',import.meta.url)));
fs.mkdirSync('.research',{recursive:true});
for(const reference of lock.kino) {
  const dir='.research/'+reference.repository.split('/')[1];
  if(!fs.existsSync(dir)) {
    execFileSync('git',['init',dir],{stdio:'inherit'});
    execFileSync('git',['-C',dir,'remote','add','origin',`https://github.com/${reference.repository}.git`]);
    execFileSync('git',['-C',dir,'-c','core.autocrlf=false','fetch','--depth','1','origin',reference.commit],{stdio:'inherit'});
    execFileSync('git',['-C',dir,'-c','core.autocrlf=false','checkout','--detach','FETCH_HEAD'],{stdio:'inherit'});
  }
  const actual=execFileSync('git',['-C',dir,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
  if(actual!==reference.commit)throw new Error(`Reference revision differs: ${dir}`);
}
