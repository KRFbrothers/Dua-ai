import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { sdkOnlyApi } from './sdk-api.mjs';

const web = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, web), 'utf8');

/** Generate host source from the one canonical client. No generated copy is committed. */
export async function prepareHostedFiles() {
  const light = 'data:image/svg+xml;base64,' + (await readFile(new URL('../../design/assets/dua-light.svg', web))).toString('base64');
  const dark = 'data:image/svg+xml;base64,' + (await readFile(new URL('../../design/assets/dua-dark.svg', web))).toString('base64');
  const font = 'data:font/ttf;base64,' + (await readFile(new URL('../android/app/src/main/res/font/inter_regular.ttf', web))).toString('base64');
  const assets = source => source.replaceAll('/dua-logo.webp', light).replaceAll('/dua-light.svg', light).replaceAll('/dua-dark.svg', dark);
  const files = [];
  for (const name of ['App.tsx', 'ChatShell.tsx', 'Icon.tsx', 'Login.tsx', 'ApprovalCard.tsx', 'appdeploy-connection.ts']) {
    files.push({ filename: 'src/' + name, content: assets(await read('src/' + name)) });
  }
  files.push({ filename: 'src/api.ts', content: sdkOnlyApi(await read('src/api.ts')) });
  files.push({ filename: 'src/application.tsx', content: await read('src/main.tsx') });
  files.push({ filename: 'src/main.ts', content: (await read('hosting/entry.ts')).replace('../src/main', './application').replaceAll('../src/', './') });
  files.push({ filename: 'src/styles.css', content: (await read('src/styles.css'))
    .replace(/^@import .*;\n/gm, '').replace(/@theme inline \{[^}]*\}\n/, '').replace('/inter-regular.ttf', font) });
  files.push({ filename: 'backend/index.ts', content: await read('hosting/backend.ts') });
  files.push({ filename: 'backend/dua-bridge.mjs', content: await read('hosting/dua-bridge.mjs') });
  files.push({ filename: 'public/Inter-OFL.txt', content: await read('../android/app/src/main/assets/licenses/Inter-OFL.txt') });
  return files;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.stdout.write(JSON.stringify(await prepareHostedFiles(), null, 2) + '\n');
}
