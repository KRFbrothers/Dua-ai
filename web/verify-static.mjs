import { access } from 'node:fs/promises';
for (const file of ['index.html', 'chat.html', 'settings.html', 'profile.html', 'cloud-sync.html', 'voice.html', 'dua-logo.svg']) {
  await access(new URL('dist/' + file, import.meta.url));
}
console.log('Dua static site ready; Vercel builds api/ functions separately.');
