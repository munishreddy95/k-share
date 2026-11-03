'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const documents = [];
function collect(directory) {
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    if (['node_modules', 'build', '.gradle', '.git'].includes(item.name)) continue;
    const file = path.join(directory, item.name);
    if (item.isDirectory()) collect(file);
    else if (item.name.endsWith('.md')) documents.push(file);
  }
}
function headings(text) {
  const result = new Set();
  const counts = new Map();
  let fenced = false;
  for (const line of text.split('\n')) {
    if (/^```/.test(line)) fenced = !fenced;
    if (fenced) continue;
    const match = /^#{1,6}\s+(.+?)\s*#*$/.exec(line);
    if (!match) continue;
    const slug = match[1].toLowerCase().replace(/[^\w\s-]/g, '').replace(/ /g, '-');
    const count = counts.get(slug) || 0;
    result.add(count ? `${slug}-${count}` : slug);
    counts.set(slug, count + 1);
  }
  return result;
}
collect(root);
let count = 0;
for (const file of documents) {
  const text = fs.readFileSync(file, 'utf8');
  for (const match of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1];
    if (/^[a-z][a-z\d+.-]*:/i.test(target)) continue;
    const [relative, anchor] = target.split('#');
    const destination = relative ? path.resolve(path.dirname(file), relative) : file;
    assert.ok(fs.existsSync(destination), `${path.relative(root, file)}: missing ${target}`);
    if (anchor && destination.endsWith('.md')) {
      assert.ok(headings(fs.readFileSync(destination, 'utf8')).has(anchor), `${file}: missing heading ${target}`);
    }
    count++;
  }
}
console.log(`Documentation checks passed: ${documents.length} files, ${count} local links`);
