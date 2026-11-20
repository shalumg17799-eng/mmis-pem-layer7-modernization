// Generates one TypeScript file of API types per OpenAPI contract (frontend-stack 2: never hand-write API types).
// --check regenerates into memory and fails if a generated file differs (for example after a manual edit).
import { readdirSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, basename } from 'node:path';
import openapiTS, { astToString } from 'openapi-typescript';

const contracts = new URL('../../contracts/openapi/', import.meta.url);
const outDir = new URL('../src/shared/api/generated/', import.meta.url);
const check = process.argv.includes('--check');
mkdirSync(outDir, { recursive: true });
let drift = 0;
for (const file of readdirSync(contracts).filter((f) => f.endsWith('.yaml')).sort()) {
  const ast = await openapiTS(new URL(file, contracts));
  const text = `// GENERATED from modern/contracts/openapi/${file} by scripts/gen-api.mjs. Do not edit.\n${astToString(ast)}`;
  const target = join(outDir.pathname.replace(/^\/([A-Za-z]:)/, '$1'), `${basename(file, '.yaml')}.ts`);
  if (check) {
    let current = '';
    try { current = readFileSync(target, 'utf8'); } catch { current = ''; }
    if (current !== text) { console.error(`DRIFT: ${target}`); drift++; }
  } else {
    writeFileSync(target, text);
    console.log(`generated ${target}`);
  }
}
if (check && drift) { console.error(`${drift} generated file(s) differ from the contracts; run npm run api:gen`); process.exit(1); }
if (check) console.log('generated API types match the contracts');
