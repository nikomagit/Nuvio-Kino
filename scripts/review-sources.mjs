import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';

const repos=JSON.parse(fs.readFileSync('.research/kino-repositories.json','utf8').replace(/^\uFEFF/,''));
const review=[];
for(const r of repos) {
  const dir=path.resolve('.research',r.name);
  if(!fs.existsSync(dir)) { review.push({repo:r.full_name,role:'Distribución winget; no es un runtime ni un plugin',revision:null,description:r.description}); continue; }
  const files=execFileSync('git',['-C',dir,'ls-files'],{encoding:'utf8'}).trim().split('\n');
  const revision=execFileSync('git',['-C',dir,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
  const readmes=files.filter(x=>/^README[^/]*\.md$/.test(x)).map(file=>({file,text:fs.readFileSync(path.join(dir,file),'utf8')}));
  const manifests=files.filter(x=>/^(?:plugin\/)?kino-plugin\.json$/.test(x)).map(file=>({file,...JSON.parse(fs.readFileSync(path.join(dir,file),'utf8'))}));
  const headings=readmes.flatMap(x=>x.text.split(/\r?\n/).filter(line=>/^#{1,3} /.test(line))).slice(0,15);
  const role=manifests.length?'Plugin Kino':r.name==='kino-light'?'Demo de UI; plugins y TV son pantallas de muestra':r.name==='kino-tv'?'Licencia GPL y oferta de código por solicitud':r.name==='kino-plugin-sdk'?'SDK Node, contrato, firmas, sandbox y pruebas':r.name==='kino-plugins'?'Guía pública, contratos y ejemplos':r.name==='kino-cloudstream-bridge'?'Host Android IPC aislado para extensiones CloudStream':r.name==='kino-social-backend'?'Servidor de Social; autenticación con tokens/atestación de Kino':r.name==='kino-resolver-example'?'Resolvedor Android AIDL y servidor HTTP de referencia':r.name==='cloudflare-quick-tunnel-kotlin'?'Transporte de túneles JVM/Android':r.name==='kinotvapp.github.io'?'Sitio y documentación de distribución':'Infraestructura de plugins';
  review.push({repo:r.full_name,revision,license:r.license?.spdx_id??'Consultar repositorio',role,description:r.description,readmes:readmes.map(x=>x.file),headings,plugins:manifests.map(m=>({path:m.file,id:m.id,version:m.version,apiVersion:m.apiVersion,capabilities:m.capabilities??[],hasThemes:!!m.appThemes,hasSettings:!!m.settings}))});
}
fs.mkdirSync('docs',{recursive:true});fs.writeFileSync('docs/repositories.json',JSON.stringify({checkedOn:'2026-11-01',repositories:review},null,2)+'\n');
const bases=[['desktop','NuvioDesktop','0.1.33-alpha'],['android-tv','NuvioTV','1.1.0-beta.9'],['android-mobile','NuvioMobile','0.6.6-beta']].map(([directory,repo,tag])=>({directory:`platforms/${directory}`,repository:`NuvioMedia/${repo}`,tag,commit:execFileSync('git',['-C',`.research/${repo}`,'rev-parse','HEAD'],{encoding:'utf8'}).trim()}));
fs.writeFileSync('upstreams.lock.json',JSON.stringify({checkedOn:'2026-11-01',nuvio:bases,kino:review.filter(x=>x.revision).map(x=>({repository:x.repo,commit:x.revision}))},null,2)+'\n');
let md='# Revisión de los repositorios de Kino\n\nInventario de 39 repositorios públicos consultado el 1 de noviembre de 2026. Los commits y contratos examinados están en [repositories.json](repositories.json). Los repositorios de plugins se revisaron por su README, manifiesto, entry y, donde existían, sus pruebas y SDK. No se ejecutaron sus servicios de producción ni scripts de despliegue.\n\n| Repositorio | Función | API / capacidades |\n|---|---|---|\n';
for(const r of review)md+=`| [${r.repo.split('/')[1]}](https://github.com/${r.repo}${r.revision?'/tree/'+r.revision:''}) | ${r.role} | ${(r.plugins??[]).map(p=>`${p.apiVersion}: ${p.capabilities.join(', ')||'tema declarativo'}`).join('; ')||'—'} |\n`;
md+='\n## Diferencias que condicionan el fork\n\n- La demo no contiene el motor productivo de TV ni de plugins. La integración usa contratos públicos y no se presenta como copia de código privado.\n- El SDK consultado anuncia API 15, mientras Social exige 16 y los temas 17. La instalación verifica cada manifiesto.\n- CloudStream y los resolvedores son apps Android con contratos IPC, no archivos JavaScript portables a Desktop. Se conservan las capacidades originales de Nuvio, pero no se afirma compatibilidad con el IPC de Kino.\n- Social exige un gate token y atestaciones firmadas por Kino: un cliente independiente no puede emitirlas. Su servicio necesita una vía oficial para clientes compatibles.\n- Los proveedores sync transportan estado de Kino; no se deben aplicar sus blobs sin una conversión al esquema de Nuvio.\n- La tienda mezcla plugins Kino, addons Stremio y comunidad. Una entrada de catálogo no sustituye la validación del manifiesto real.\n- Los temas de Kino describen colores, formas y tipografías que no tienen una correspondencia exacta con los temas de Nuvio.\n';
fs.writeFileSync('docs/REPOSITORY-REVIEW.md',md);
const asset='platforms/desktop/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll';
const data=fs.readFileSync(asset), desktop=bases[0];
fs.writeFileSync('assets.lock.json',JSON.stringify([{path:asset,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex'),url:`https://raw.githubusercontent.com/${desktop.repository}/${desktop.commit}/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll`}],null,2)+'\n');
console.log(`Reviewed ${review.length} repositories; locked ${bases.length} Nuvio bases.`);
