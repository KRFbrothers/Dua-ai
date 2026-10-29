// Use the Android app's own DUA logo as the single source (no copy committed to apps/web/).
import { copyFileSync, mkdirSync } from 'node:fs';
const from = new URL('../../android/app/src/main/res/drawable-nodpi/dua_logo_home.webp', import.meta.url);
const dir = new URL('../public/', import.meta.url);
mkdirSync(dir, { recursive: true });
copyFileSync(from, new URL('dua-logo.webp', dir));
for (const theme of ['light', 'dark']) {
  copyFileSync(new URL('../../../design/assets/dua-' + theme + '.svg', import.meta.url), new URL('dua-' + theme + '.svg', dir));
}
copyFileSync(new URL('../../android/app/src/main/res/font/inter_regular.ttf', import.meta.url), new URL('inter-regular.ttf', dir));
copyFileSync(new URL('../../android/app/src/main/assets/licenses/Inter-OFL.txt', import.meta.url), new URL('Inter-OFL.txt', dir));
