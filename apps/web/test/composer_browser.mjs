// Real-browser regression checks against controlled HTTP fixtures; no owner login or live AI calls.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const { chromium } = await import(process.env.DUA_PLAYWRIGHT_MODULE || 'playwright-core');
const output = resolve(process.env.DUA_BROWSER_ARTIFACTS || 'build/composer-browser');
await mkdir(output, { recursive: true });
const browser = process.env.DUA_BROWSER_CDP_URL
  ? await chromium.connectOverCDP(process.env.DUA_BROWSER_CDP_URL)
  : await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 390, height: 858 }, isMobile: true, hasTouch: true });
const page = await context.newPage();
const report = { backend: 'controlled HTTP fixtures', checks: [], snapshots: {}, pageErrors: [], chatRequests: [] };
const user = { id: '11111111-1111-4111-8111-111111111111', email: 'reader@example.invalid', displayName: 'Reader Example', avatarUrl: '' };
let finishReply;
page.on('pageerror', error => report.pageErrors.push(error.message));
await page.route('**/api/**', async route => {
  const path = new URL(route.request().url()).pathname;
  if (path === '/api/account/session') return route.fulfill({ json: { configured: true, user } });
  if (path === '/api/cloud') return route.fulfill({ json: { ownerId: user.id, snapshot: {
    format: 'dua-cloud-snapshot', version: 1, chats: [{ id: 'cloud-note', model: 'Dua', title: 'Cloud note', pinned: true, updatedAt: 1,
      messages: [{ role: 'user', text: 'Saved question' }, { role: 'model', text: 'Saved answer' }] }]
  } } });
  if (path === '/api/web/chat') {
    report.chatRequests.push(route.request().postDataJSON());
    await new Promise(resolve => { finishReply = resolve; });
    return route.fulfill({ contentType: 'text/event-stream', body: 'data: {"type":"text","delta":"Fixture reply"}\n\ndata: {"type":"done"}\n\n' });
  }
  throw new Error(`Unexpected API call: ${route.request().method()} ${path}`);
});
const field = page.getByRole('textbox', { name: 'Message Dua AI' });
const check = (name, condition) => { assert.ok(condition, name); report.checks.push(name); };
async function dimensions() {
  return field.evaluate(element => ({ height: element.getBoundingClientRect().height, client: element.clientHeight,
    scroll: element.scrollHeight, max: parseFloat(getComputedStyle(element).maxHeight),
    focused: element === document.activeElement, value: element.value }));
}
async function snapshot(name) {
  report.snapshots[name] = await dimensions();
  await page.screenshot({ path: resolve(output, `${name}.png`) });
}
async function settle() { await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); }
try {
  await page.goto(process.env.DUA_BROWSER_TEST_URL || 'http://127.0.0.1:3000', { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  const empty = await dimensions();
  await page.getByRole('button', { name: 'Task', exact: true }).tap();
  const originalField = await field.elementHandle();
  for (let index = 0; index < 3; index++) {
    const starter = page.locator('.task-starters button').nth(index);
    const prompt = await starter.innerText();
    await starter.tap(); await settle();
    await snapshot(`task-starter-${index + 1}`);
    check(`Task starter ${index + 1} fills and focuses the existing textarea`, (await dimensions()).focused && await field.inputValue() === prompt);
  }
  check('Task starters do not send', report.chatRequests.length === 0);
  await field.fill('one\ntwo\nthree'); await settle();
  const multiline = await dimensions();
  check('multiline draft grows without internal overflow below the cap', multiline.height > empty.height && multiline.scroll <= multiline.client + 1);
  await field.fill('long draft line\n'.repeat(30)); await settle();
  await snapshot('bounded');
  const bounded = await dimensions();
  check('long draft stops at the CSS cap and scrolls internally', bounded.height <= bounded.max && bounded.scroll > bounded.client);
  await field.press('Control+End');
  check('last line can be scrolled into view', await field.evaluate(element => element.scrollTop > 0));
  await field.fill('short'); await settle();
  check('deleting content shrinks back to the idle height', Math.abs((await dimensions()).height - empty.height) <= 1);
  await field.press('Escape');
  await page.getByRole('button', { name: 'Chat', exact: true }).tap();
  check('dismissal and mode change preserve the unsent draft', await field.inputValue() === 'short');
  await page.getByRole('button', { name: 'Task', exact: true }).tap();
  await field.fill('A wrapping draft that should fit differently when the available composer width changes.');
  for (const width of [1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 858 }); await settle();
    const size = await dimensions();
    check(`width ${width}: no horizontal page overflow and text fits below cap`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)
      && (size.scroll <= size.client + 1 || size.height >= size.max));
    check(`width ${width}: Send stays within the viewport`, await page.getByRole('button', { name: 'Send', exact: true }).evaluate(element => {
      const rect = element.getBoundingClientRect(); return rect.left >= 0 && rect.right <= innerWidth && rect.bottom <= innerHeight;
    }));
  }
  await page.setViewportSize({ width: 390, height: 500 }); await settle();
  await snapshot('short-viewport');
  check('short mobile viewport keeps composer visible', await field.evaluate(element => element.getBoundingClientRect().bottom <= innerHeight));
  await field.fill('line one'); await field.press('Shift+Enter'); await field.press('x'); await settle();
  check('Shift+Enter inserts a newline without sending', await field.inputValue() === 'line one\nx' && report.chatRequests.length === 0);
  await Promise.all([page.waitForRequest(request => new URL(request.url()).pathname === '/api/web/chat'), field.press('Enter')]);
  await page.waitForFunction(() => Boolean(document.querySelector('button[aria-label="Stop"]')));
  check('Enter sends exactly once and clears/shrinks the composer', report.chatRequests.length === 1 && await field.inputValue() === '' && (await dimensions()).height === empty.height);
  await field.fill('next draft'); await field.press('Enter');
  check('Enter while busy does not send again or consume the next draft', report.chatRequests.length === 1 && await field.inputValue() === 'next draft');
  finishReply(); await page.getByText('Fixture reply', { exact: true }).waitFor();
  await Promise.all([page.waitForRequest(request => new URL(request.url()).pathname === '/api/web/chat'), page.getByRole('button', { name: 'Send', exact: true }).tap()]);
  await page.waitForFunction(() => Boolean(document.querySelector('button[aria-label="Stop"]')));
  check('Send button still submits the draft', report.chatRequests.length === 2 && report.chatRequests[1].messages.at(-1).text === 'next draft');
  finishReply(); await page.getByRole('button', { name: 'Stop', exact: true }).waitFor({ state: 'hidden' });
  await field.fill('   '); await field.press('Enter');
  check('whitespace draft does not send', report.chatRequests.length === 2);
  await field.fill('');
  check('empty voice/attachment controls retain their disabled states', await page.getByRole('button', { name: 'Live voice is available in the Android app' }).isDisabled()
    && await page.getByRole('button', { name: 'Attachments are available in the Android app' }).isDisabled());
  await page.getByRole('button', { name: 'Open history', exact: true }).tap();
  await page.getByRole('button', { name: 'Cloud note', exact: false }).tap(); await settle();
  await snapshot('read-only');
  check('cloud history keeps the textarea disabled and cannot send', await field.isDisabled() && report.chatRequests.length === 2);
  check('model remains disabled for read-only history', await page.getByRole('combobox', { name: 'Model', exact: true }).isDisabled());
  await page.getByRole('button', { name: 'Start a new chat' }).tap();
  check('new chat restores an empty editable composer', await field.isEnabled() && await field.inputValue() === '');
  check('textarea node survives resizing and state transitions', await originalField.evaluate(element => element === document.querySelector('.composer textarea')));
  check('only one composer exists', await page.locator('.composer textarea').count() === 1);
  check('no browser page errors', report.pageErrors.length === 0);
  report.result = 'passed';
} catch (error) {
  report.result = 'failed'; report.failure = error.message; process.exitCode = 1;
  await page.screenshot({ path: resolve(output, 'failure.png') }).catch(() => {});
} finally {
  finishReply?.();
  await writeFile(resolve(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
  await context.close(); await browser.close();
}
