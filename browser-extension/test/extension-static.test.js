const assert = require("assert");
const fs = require("fs");
const path = require("path");
const childProcess = require("child_process");

const root = path.join(__dirname, "..");
const manifest = JSON.parse(fs.readFileSync(path.join(root, "manifest.json"), "utf8"));
const background = fs.readFileSync(path.join(root, "background.js"), "utf8");
const popup = fs.readFileSync(path.join(root, "popup.js"), "utf8");
const popupHtml = fs.readFileSync(path.join(root, "popup.html"), "utf8");
const db = fs.readFileSync(path.join(root, "db.js"), "utf8");

assert.strictEqual(manifest.manifest_version, 3);
assert.strictEqual(manifest.background.service_worker, "background.js");
assert.ok(manifest.permissions.includes("storage"));
assert.ok(manifest.permissions.includes("clipboardWrite"));
assert.ok(manifest.permissions.includes("notifications"));
assert.ok(manifest.host_permissions.includes("https://share.example.com/*"));
assert.ok(manifest.content_security_policy.extension_pages.includes("wss://share.example.com"));
assert.strictEqual(manifest.icons["128"], "icons/icon-128.png");

assert.ok(background.includes("DEFAULT_RELAY_WS_URL = \"wss://share.example.com/agent\""));
assert.ok(background.includes("type: \"register\""));
assert.ok(background.includes("/access/request"));
assert.ok(background.includes("/access/session"));
assert.ok(background.includes("/api/files"));
assert.ok(background.includes("/file/"));
assert.ok(background.includes("FluxDb.getFile"));
assert.ok(background.includes("chrome.notifications.create"));
assert.ok(background.includes("chrome.action.setBadgeText"));

assert.ok(popupHtml.includes("type=\"file\" multiple"));
assert.ok(popupHtml.includes("popup.js"));
assert.ok(popup.includes("share.start"));
assert.ok(popup.includes("access.approve"));
assert.ok(popup.includes("addCurrentTab"));
assert.ok(popup.includes("preferredPopupTab"));
assert.ok(db.includes("indexedDB.open"));

const combined = [background, popup, popupHtml, db].join("\n");
assert.ok(!combined.includes("DesktopHttpApp"), "Extension must not depend on desktop app code");
assert.ok(!combined.includes("127.0.0.1:47833"), "Extension must not depend on desktop companion port");

for (const file of ["background.js", "popup.js", "db.js", "qr.js"]) {
  childProcess.execFileSync(process.execPath, ["--check", path.join(root, file)], {
    stdio: "pipe"
  });
}

for (const size of [16, 32, 48, 128]) {
  assert.ok(fs.existsSync(path.join(root, "icons", `icon-${size}.png`)));
}

console.log("browser extension static test passed");
