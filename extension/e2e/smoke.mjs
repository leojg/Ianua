// End-to-end smoke test of the built extension (extension/build/dist) in Chromium.
// youtube.com is never contacted: requests are answered from rules/fixtures/web.
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dist = join(here, '..', 'build', 'dist');
const homeHtml = readFileSync(join(here, '..', '..', 'rules', 'fixtures', 'web', 'youtube_home.html'), 'utf8');
const manifest = JSON.parse(readFileSync(join(dist, 'manifest.json'), 'utf8'));
const blockedPack = JSON.parse(readFileSync(join(here, '..', '..', 'rules', 'blocked.json'), 'utf8'));
const ID = 'abcDEF12345';

const context = await chromium.launchPersistentContext(mkdtempSync(join(tmpdir(), 'ianua-')), {
  channel: 'chromium',
  headless: true,
  args: [`--disable-extensions-except=${dist}`, `--load-extension=${dist}`],
});
await context.route(/^https:\/\/(www\.|m\.)?youtube\.com\//, (route) => {
  const url = new URL(route.request().url());
  const body = url.pathname.startsWith('/watch')
    ? `<!doctype html><title>watch</title><p id="watch">regular player ${url.searchParams.get('v')}</p>`
    : url.pathname.startsWith('/shorts/')
      ? '<!doctype html><title>shorts</title><p id="shorts">THE SHORTS PLAYER</p>'
      : homeHtml;
  route.fulfill({ status: 200, contentType: 'text/html', body });
});

// Blocked platforms and a stand-in for a domain added by a rule update. If one of these pages
// renders, blocking failed.
await context.route(/^https?:\/\/([a-z0-9-]+\.)*(tiktok\.com|blocked-example\.test)\//, (route) =>
  route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>site</title><p id="site">SITE LOADED</p>' }));

let worker = context.serviceWorkers()[0] ?? (await context.waitForEvent('serviceworker'));
const extensionId = new URL(worker.url()).host;
const gatePrefix = `chrome-extension://${extensionId}/gate.html`;

const results = [];
async function test(name, fn) {
  try {
    await fn();
    results.push(`ok   ${name}`);
  } catch (e) {
    results.push(`FAIL ${name}\n     ${e.message.split('\n').join('\n     ')}`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const dynamicRuleCount = () =>
  // Extension APIs bind a moment after the worker starts.
  worker.evaluate(() => chrome.declarativeNetRequest?.getDynamicRules().then((r) => r.length) ?? -1);
async function waitForRuleCount(predicate, what) {
  for (let i = 0; i < 100; i++) {
    if (predicate(await dynamicRuleCount())) return;
    await sleep(100);
  }
  throw new Error(`timed out waiting for ${what}`);
}

// The background publishes rules and DNR redirects on install; wait for them.
await waitForRuleCount((n) => n > 0, 'DNR rules to be installed');

await test('hard load of a Short lands on the gate', async () => {
  const page = await context.newPage();
  await page.goto(`https://www.youtube.com/shorts/${ID}?feature=share`);
  await page.waitForURL(`${gatePrefix}?v=${ID}&s=youtube`);
  await page.getByRole('heading', { name: "You're about to enter Shorts" }).waitFor();
  await page.close();
});

await test('mobile web Shorts are gated too', async () => {
  const page = await context.newPage();
  await page.goto(`https://m.youtube.com/shorts/${ID}`);
  await page.waitForURL(`${gatePrefix}?v=${ID}&s=youtube`);
  await page.close();
});

await test('Continue is locked for 10 s, then opens the regular player', async () => {
  const page = await context.newPage();
  await page.goto(`https://www.youtube.com/shorts/${ID}`);
  await page.waitForURL(/gate\.html/);
  const proceed = page.locator('#continue');
  assert.equal(await proceed.isDisabled(), true);
  assert.match(await proceed.textContent(), /Continue in \d+ s/);
  assert.equal(await page.evaluate(() => document.activeElement.id), 'back', 'Go back has focus');
  await page.waitForTimeout(8_000);
  assert.equal(await proceed.isDisabled(), true, 'still locked at 8 s');
  await page.waitForFunction(() => !document.getElementById('continue').disabled, null, { timeout: 4_000 });
  await proceed.click();
  await page.waitForURL(`https://www.youtube.com/watch?v=${ID}`);
  await page.close();
});

await test('Shorts entry points are hidden, regular videos are not', async () => {
  const page = await context.newPage();
  await page.goto('https://www.youtube.com/');
  await page.waitForFunction(() => document.getElementById('ianua-hide')?.textContent.length > 0);
  const display = (id) => page.$eval(`#${id}`, (el) => getComputedStyle(el).display);
  for (const id of ['guide-shorts', 'mini-shorts', 'shorts-item', 'shorts-section', 'reel-shelf', 'tab-shorts']) {
    assert.equal(await display(id), 'none', `${id} should be hidden`);
  }
  for (const id of ['guide-home', 'regular-video', 'tab-videos']) {
    assert.notEqual(await display(id), 'none', `${id} should stay visible`);
  }
  await page.close();
});

await test('in-page navigation to a Short is gated, and Go back returns', async () => {
  const page = await context.newPage();
  await page.goto('https://www.youtube.com/');
  await page.waitForFunction(() => document.getElementById('ianua-hide')?.textContent.length > 0);
  await page.click('#spa-shorts');
  await page.waitForURL(`${gatePrefix}?v=spaDEF12345&s=youtube`);
  await page.click('#back');
  await page.waitForURL('https://www.youtube.com/');
  await page.close();
});

await test('manifest has host permission for every bundled blocked domain', async () => {
  const hosts = manifest.host_permissions;
  for (const platform of blockedPack.block.platforms) {
    for (const domain of platform.domains) {
      assert.ok(hosts.includes(`*://*.${domain}/*`), `missing host permission for ${domain}`);
    }
  }
});

await test('a blocked site lands on the blocked page, with no way through', async () => {
  const page = await context.newPage();
  await page.goto('https://www.tiktok.com/@someone/video/7400000000000000000');
  await page.waitForURL(`chrome-extension://${extensionId}/blocked.html?p=TikTok`);
  await page.getByRole('heading', { name: 'TikTok is blocked' }).waitFor();
  assert.equal(await page.locator('#continue').count(), 0);
  await page.close();
});

await test('short links and bare domains are blocked too', async () => {
  const page = await context.newPage();
  for (const url of ['https://vm.tiktok.com/ZMabc123/', 'https://tiktok.com/']) {
    await page.goto(url);
    await page.waitForURL(/blocked\.html\?p=TikTok/);
  }
  await page.close();
});

await test('Go back leaves the blocked page', async () => {
  const page = await context.newPage();
  await page.goto('https://www.youtube.com/');
  await page.goto('https://www.tiktok.com/');
  await page.waitForURL(/blocked\.html/);
  await page.click('#back');
  await page.waitForURL('https://www.youtube.com/');
  await page.close();
});

await test('a domain added by a rule update, without host permission, is still blocked', async () => {
  const popup = await context.newPage();
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);
  const before = await dynamicRuleCount();
  const patched = structuredClone(blockedPack);
  patched.version += 1;
  patched.block.platforms.push({ name: 'Example', domains: ['blocked-example.test'] });
  await popup.evaluate((text) => chrome.storage.local.set({ 'rules.blocked': text }), JSON.stringify(patched));
  await waitForRuleCount((n) => n === before + 1, 'the new platform\'s block rule');
  const page = await context.newPage();
  const error = await page.goto('https://blocked-example.test/').then(() => null, (e) => e.message);
  assert.match(error ?? `loaded ${page.url()}`, /ERR_BLOCKED_BY_CLIENT/);
  await page.close();
  await popup.close();
});

await test('switching Ianua off lets Shorts through', async () => {
  const popup = await context.newPage();
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);
  await popup.locator('#enabled').uncheck();
  await waitForRuleCount((n) => n === 0, 'DNR rules to be removed');
  const page = await context.newPage();
  await page.goto(`https://www.youtube.com/shorts/${ID}`);
  await page.locator('#shorts').waitFor();
  assert.equal(page.url(), `https://www.youtube.com/shorts/${ID}`);
  await page.goto('https://www.tiktok.com/');
  await page.locator('#site').waitFor();
  await popup.locator('#enabled').check();
  await page.close();
  await popup.close();
});

await context.close();
console.log(results.join('\n'));
if (results.some((r) => r.startsWith('FAIL'))) process.exit(1);
