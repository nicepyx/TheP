import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
// fileURLToPath handles Windows drive letters (D:\...) correctly.
const dist = fileURLToPath(new URL('../dist/', import.meta.url));
function collect(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap(item => {
    const p = join(dir, item.name);
    return item.isDirectory() ? collect(p) : [p];
  });
}
const extras = collect(join(dist, 'assets')).map(p => '/' + relative(dist, p).replaceAll('\\','/'));
const swPath = join(dist, 'sw.js');
const sw = readFileSync(swPath, 'utf8').replace('/* __PRECACHE_INJECT__ */', `PRECACHE.push(...${JSON.stringify(extras)});`);
writeFileSync(swPath, sw);
console.log('[FitWave] PWA offline resources:', extras.length);
