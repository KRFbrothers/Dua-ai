// Use the Android app's own DUA logo as the single source (no copy committed to website/).
import { copyFileSync, mkdirSync } from 'node:fs';
const from = new URL('../../app/src/main/res/drawable-nodpi/dua_logo_home.webp', import.meta.url);
const dir = new URL('../public/', import.meta.url);
mkdirSync(dir, { recursive: true });
copyFileSync(from, new URL('dua-logo.webp', dir));
