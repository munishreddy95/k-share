const assert = require("assert");
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const html = fs.readFileSync(path.join(root, "public", "index.html"), "utf8");
const script = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");

const elementNames = new Set();
const elementPattern = /(\w+):\s*document\.(?:getElementById\("([^"]+)"\)|querySelector\("([^"]+)"\)|querySelectorAll\("([^"]+)"\))/g;
for (const match of script.matchAll(elementPattern)) {
  elementNames.add(match[1]);
  const id = match[2];
  if (id) {
    assert.ok(html.includes(`id="${id}"`), `Missing #${id} in desktop HTML`);
  }
}

const listenerPattern = /elements\.(\w+)\.addEventListener/g;
for (const match of script.matchAll(listenerPattern)) {
  assert.ok(elementNames.has(match[1]), `Missing elements.${match[1]} binding`);
}

assert.ok(html.includes("modeSwitchDialog"), "Desktop mode switch dialog is missing");
assert.ok(script.includes("showModeSwitchDialog"), "Desktop mode switch guard is missing");
assert.ok(script.includes("isConnectedSession"), "Connected session lock is missing");
assert.ok(
  script.includes("return isConnectedSession() && !!activeRunningMode && activeRunningMode !== targetMode;"),
  "Mode switch warning must only appear after another device is connected"
);

console.log("desktop static ui test passed");
