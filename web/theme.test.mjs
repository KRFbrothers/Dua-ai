import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');
const map = JSON.parse(await read('../theme/dua-theme.json'));
const camel = (name) => name.replace(/-(\w)/g, (_, c) => c.toUpperCase());
const argb = (hex) => '0xFF' + hex.slice(1).toUpperCase();

test('theme.css matches the shared theme map', async () => {
  const css = await read('dist/theme.css');
  const block = (selector) => css.split('\n').find((line) => line.startsWith(selector));
  for (const [mode, selector] of [['dark', ':root{'], ['light', ':root[data-theme=light]{']]) {
    const line = block(selector);
    for (const [key, value] of Object.entries(map[mode])) assert.ok(line.includes(`--${key}:${value}`), `${mode} ${key}`);
  }
});

test('Android palette matches the shared theme map', async () => {
  const kt = await read('../app/src/main/java/com/dua/ai/ui/theme/DuaPalette.kt');
  const aliases = { surface: ['surface', 'background'], 'on-surface': ['onSurface', 'onBackground'], danger: ['error'], 'surface-container': ['surfaceContainer'], 'surface-high': ['surfaceContainerHigh'], 'surface-highest': ['surfaceContainerHighest'] };
  for (const [mode, name] of [['dark', 'darkScheme'], ['light', 'lightScheme']]) {
    const body = kt.slice(kt.indexOf(`val ${name}`));
    const section = body.slice(0, body.indexOf('\n    )'));
    for (const [key, value] of Object.entries(map[mode])) {
      for (const prop of aliases[key] || [camel(key)]) assert.ok(section.includes(`${prop} = Color(${argb(value)})`), `${mode} ${prop}`);
    }
  }
});

test('every page loads theme.css before other stylesheets', async () => {
  const { readdir } = await import('node:fs/promises');
  for (const file of (await readdir(new URL('dist/', import.meta.url))).filter((f) => f.endsWith('.html'))) {
    const html = await read('dist/' + file);
    const links = [...html.matchAll(/<link rel="stylesheet" href="([^"]+)">/g)].map((m) => m[1]);
    if (links.length) assert.equal(links[0], 'theme.css', file);
  }
});
