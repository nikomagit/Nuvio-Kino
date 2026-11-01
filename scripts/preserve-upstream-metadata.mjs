import fs from 'node:fs';
import {execFileSync} from 'node:child_process';

// Import files already tracked upstream even where an upstream .gitignore now ignores them.
// Preserve executable bits and gitlinks when assembling a Windows-hosted monorepo.
const excluded=new Set(['composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll']);
let modules='', imported=0, links=0;
const orphaned=[];
for(const [repo,platform] of [['NuvioDesktop','desktop'],['NuvioMobile','android-mobile'],['NuvioTV','android-tv']]) {
  const source=`.research/${repo}`;
  const stage=execFileSync('git',['-C',source,'ls-files','--stage','-z'],{encoding:'utf8'}).split('\0').filter(Boolean);
  const moduleFile=`${source}/.gitmodules`;
  const moduleText=fs.existsSync(moduleFile)?fs.readFileSync(moduleFile,'utf8'):'';
  const config=[...moduleText.matchAll(/\[submodule "([^"]+)"\]([\s\S]*?)(?=\[submodule |$)/g)].map(m=>({name:m[1],path:/^\s*path\s*=\s*(.+)$/m.exec(m[2])?.[1].trim(),url:/^\s*url\s*=\s*(.+)$/m.exec(m[2])?.[1].trim()}));
  const toAdd=[], executable=[];
  for(const record of stage) {
    const [metadata,file]=record.split('\t'); const [mode,sha]=metadata.split(' ');
    if(excluded.has(file) || file.startsWith('.kotlin/'))continue;
    const destination=`platforms/${platform}/${file}`;
    if(mode==='160000') {
      const module=config.find(x=>x.path===file);
      if(!module?.url) {
        orphaned.push({upstream:`NuvioMedia/${repo}`,path:destination,commit:sha,note:'Gitlink tracked upstream without an entry in upstream .gitmodules; not required for the prebuilt dependency build.'});
        continue;
      }
      if(!/^https:\/\/github.com\//.test(module.url))throw new Error(`Review submodule URL: ${repo}/${file}`);
      modules+=`[submodule "${platform}-${module.name.replace(/[^a-zA-Z0-9_-]/g,'_')}"]\n\tpath = ${destination}\n\turl = ${module.url}\n`;
      execFileSync('git',['update-index','--add','--cacheinfo',`160000,${sha},${destination}`]);links++;
    } else {
      if(!fs.existsSync(destination))throw new Error(`Missing upstream source: ${destination}`);
      toAdd.push(destination);imported++;
      if(mode==='100755')executable.push(destination);
    }
  }
  for(let i=0;i<toAdd.length;i+=40)execFileSync('git',['add','-f','--',...toAdd.slice(i,i+40)],{stdio:'pipe'});
  for(let i=0;i<executable.length;i+=40)execFileSync('git',['update-index','--chmod=+x','--',...executable.slice(i,i+40)]);
}
fs.writeFileSync('docs/upstream-orphaned-gitlinks.json',JSON.stringify(orphaned,null,2)+'\n');
fs.writeFileSync('.gitmodules',modules);
execFileSync('git',['add','.gitmodules']);
execFileSync('git',['update-index','--chmod=+x','--','gradlew']);
console.log(`Preserved ${imported} upstream files and ${links} submodule commits; excluded build-error logs and one checksummed oversized asset.`);
