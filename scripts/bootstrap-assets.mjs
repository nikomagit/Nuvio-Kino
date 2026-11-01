import fs from 'node:fs';
import {createHash} from 'node:crypto';
const assets=JSON.parse(fs.readFileSync(new URL('../assets.lock.json',import.meta.url)));
for(const asset of assets) {
  if(fs.existsSync(asset.path) && createHash('sha256').update(fs.readFileSync(asset.path)).digest('hex')===asset.sha256)continue;
  console.log(`Downloading pinned upstream asset: ${asset.path}`);
  const response=await fetch(asset.url);
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  const bytes=Buffer.from(await response.arrayBuffer());
  if(bytes.length!==asset.bytes || createHash('sha256').update(bytes).digest('hex')!==asset.sha256)throw new Error('Asset checksum mismatch');
  fs.mkdirSync(asset.path.slice(0,asset.path.lastIndexOf('/')),{recursive:true});fs.writeFileSync(asset.path,bytes);
}
