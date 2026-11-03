'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const legacyVisibleName = /Flux\s+(?:File\s+Share|Tiny\s+URL|Desktop|desktop\s+share|relay|global\s+share)/i;
const errors = [];

function inspect(directory) {
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    if (['node_modules', 'build', '.gradle', '.git', 'test'].includes(item.name)) continue;
    const file = path.join(directory, item.name);
    if (item.isDirectory()) inspect(file);
    else if (/\.(?:java|js|html|xml|json|svg)$/.test(item.name)) {
      if (legacyVisibleName.test(fs.readFileSync(file, 'utf8'))) errors.push(path.relative(root, file));
    }
  }
}
for (const folder of ['app/src', 'desktop', 'browser-extension', 'relay', 'tiny-url']) inspect(path.join(root, folder));
assert.deepEqual(errors, [], `Old user-visible branding remains: ${errors.join(', ')}`);
assert.ok(fs.readFileSync(path.join(root, 'app/src/main/res/values/strings.xml'), 'utf8')
  .includes('<string name="app_name">K-Share</string>'));
assert.ok(fs.readFileSync(path.join(root, 'desktop/public/index.html'), 'utf8').includes('<title>K-Share Desktop</title>'));
assert.ok(fs.readFileSync(path.join(root, 'browser-extension/popup.html'), 'utf8').includes('class="brand-mark" aria-hidden="true">K<'));
assert.equal(JSON.parse(fs.readFileSync(path.join(root, 'browser-extension/manifest.json'))).name, 'K-Share');
for (const folder of ['.', 'desktop', 'relay', 'tiny-url']) {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, folder, 'package.json')));
  assert.ok(manifest.name.startsWith('k-share'));
  assert.equal(manifest.license, 'MIT');
}
console.log('K-Share visible branding and license metadata checks passed');
