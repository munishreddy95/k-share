const assert = require("assert");
const http = require("http");
const os = require("os");
const fsp = require("fs/promises");
const path = require("path");
const { NearbyReceiver, NearbySender, DesktopHttpApp, DesktopWebShare, DesktopLiveShare } = require("../src/main");
const { createRelayServer } = require("../../relay/server");

function waitForSseState(url, predicate) {
  return new Promise((resolve, reject) => {
    const request = http.get(`${url}/events`, (response) => {
      let buffer = "";
      const timer = setTimeout(() => {
        request.destroy();
        reject(new Error("Timed out waiting for live state event"));
      }, 5000);
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        buffer += chunk;
        const messages = buffer.split("\n\n");
        buffer = messages.pop();
        for (const message of messages) {
          const dataLine = message.split("\n").find((line) => line.startsWith("data: "));
          if (!dataLine) {
            continue;
          }
          const state = JSON.parse(dataLine.slice(6));
          if (predicate(state)) {
            clearTimeout(timer);
            request.destroy();
            resolve(state);
          }
        }
      });
    });
    request.on("error", reject);
  });
}

async function run() {
  const saveDir = await fsp.mkdtemp(path.join(os.tmpdir(), "flux-desktop-ui-"));
  const receiver = new NearbyReceiver({ host: "127.0.0.1", port: 0, saveDir });
  const sender = new NearbySender({ host: "127.0.0.1", port: 0 });
  const relay = createRelayServer({
    publicHost: "127.0.0.1",
    publicPort: 0,
    publicBase: "https://test.invalid/{slug}",
    secureCookies: false
  });
  relay.listen();
  await new Promise((resolve) => relay.publicServer.once("listening", resolve));
  const relayPort = relay.publicServer.address().port;
  const localShare = new DesktopWebShare({
    scope: "local",
    title: "Local Web Share",
    loopbackOnly: true,
    approvalRequired: false
  });
  const liveShare = new DesktopLiveShare(new DesktopWebShare({
    scope: "live",
    title: "Live Share",
    loopbackOnly: true,
    approvalRequired: true
  }), {
    config: {
      host: "127.0.0.1",
      port: relayPort,
      tls: false,
      agentPath: "/agent"
    }
  });
  await receiver.start();
  const app = new DesktopHttpApp(receiver, sender, localShare, liveShare);
  const url = await app.start(0);

  const stateResponse = await fetch(`${url}/api/state`);
  assert.strictEqual(stateResponse.status, 200);
  const state = await stateResponse.json();
  assert.strictEqual(state.receiver.running, true);
  assert.ok(state.receiver.payload.startsWith("fluxnearby://receiver?"));
  assert.ok(state.receiver.payload.includes("host=127.0.0.1"));
  assert.ok(state.sender.payload.startsWith("fluxnearby://receive?"));
  assert.strictEqual(state.localShare.running, false);
  assert.strictEqual(state.liveShare.running, false);

  const htmlResponse = await fetch(url);
  assert.strictEqual(htmlResponse.status, 200);
  const html = await htmlResponse.text();
  assert.ok(html.includes("K-Share Desktop"));
  assert.ok(html.includes("Transfer workspace"));
  assert.ok(html.includes("Phone sends to desktop"));
  assert.ok(html.includes("Local Web"));
  assert.ok(html.includes("Live Share"));
  assert.ok(!html.includes("mode-panel active"));
  assert.ok(!html.includes("mode-tab active"));

  const iconResponse = await fetch(`${url}/icon.png`);
  assert.strictEqual(iconResponse.status, 200);
  assert.strictEqual(iconResponse.headers.get("content-type"), "image/png");
  const icon = Buffer.from(await iconResponse.arrayBuffer());
  assert.deepStrictEqual([...icon.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);

  const sharedFile = path.join(saveDir, "browser-video.mp4");
  await fsp.writeFile(sharedFile, Buffer.from("desktop local web share"));
  const chooseResponse = await fetch(`${url}/api/local/files`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ paths: [sharedFile] })
  });
  assert.strictEqual(chooseResponse.status, 200);
  const startResponse = await fetch(`${url}/api/local/start`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({})
  });
  assert.strictEqual(startResponse.status, 200);
  const started = await startResponse.json();
  assert.strictEqual(started.localShare.running, true);
  assert.ok(started.localShare.url.startsWith("http://127.0.0.1:"));

  const localFiles = await fetch(`${started.localShare.url}api/files`).then((response) => response.json());
  assert.strictEqual(localFiles.files.length, 1);
  assert.strictEqual(localFiles.files[0].category, "videos");
  const fileBody = await fetch(`${started.localShare.url}file/${encodeURIComponent(localFiles.files[0].id)}`).then((response) => response.text());
  assert.strictEqual(fileBody, "desktop local web share");

  const liveFile = path.join(saveDir, "live-document.pdf");
  await fsp.writeFile(liveFile, Buffer.from("desktop live web share"));
  const liveChooseResponse = await fetch(`${url}/api/live/files`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ paths: [liveFile] })
  });
  assert.strictEqual(liveChooseResponse.status, 200);
  const liveReadyPromise = waitForSseState(url, (next) => next.liveShare.connected && next.liveShare.url);
  const liveStartResponse = await fetch(`${url}/api/live/start`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({})
  });
  assert.strictEqual(liveStartResponse.status, 200);
  await liveReadyPromise;
  const [relaySession] = Array.from(relay.sessions.values());
  assert.ok(relaySession, "Live share should register with relay");
  const relayBase = `http://127.0.0.1:${relayPort}/${relaySession.slug}`;
  const approvalPage = await fetch(`${relayBase}/?token=${relaySession.token}`).then((response) => response.text());
  assert.ok(approvalPage.includes("Request access"));
  const accessResponse = await fetch(`${relayBase}/access/request?token=${relaySession.token}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "visitor@example.com",
      location: "Test lab",
      device: "Node browser",
      userAgent: "K-Share desktop test"
    })
  });
  assert.strictEqual(accessResponse.status, 200);
  const accessPayload = await accessResponse.json();
  assert.ok(accessPayload.id);
  const approveResponse = await fetch(`${url}/api/web/approve`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ scope: "live", id: accessPayload.id })
  });
  assert.strictEqual(approveResponse.status, 200);
  const sessionPayload = await fetch(`${relayBase}/access/session?id=${accessPayload.id}&token=${relaySession.token}`).then((response) => response.json());
  assert.strictEqual(sessionPayload.status, "approved");
  const liveFilesUrl = `${relayBase}/api/files?token=${relaySession.token}&access=${sessionPayload.token}`;
  const liveFiles = await fetch(liveFilesUrl).then((response) => response.json());
  assert.strictEqual(liveFiles.files.length, 1);
  assert.strictEqual(liveFiles.files[0].category, "files");
  const liveBody = await fetch(`${relayBase}/file/${encodeURIComponent(liveFiles.files[0].id)}?token=${relaySession.token}&access=${sessionPayload.token}`).then((response) => response.text());
  assert.strictEqual(liveBody, "desktop live web share");

  const liveStatePromise = waitForSseState(url, (next) => next.receiver.status === "Live update check.");
  receiver.setStatus("Live update check.");
  const liveState = await liveStatePromise;
  assert.strictEqual(liveState.receiver.status, "Live update check.");

  await receiver.stop();
  await localShare.stop();
  await liveShare.stop();
  await new Promise((resolve) => relay.close(resolve));
  await new Promise((resolve) => app.server.close(resolve));
  await fsp.rm(saveDir, { recursive: true, force: true });
  console.log("desktop http ui test passed");
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
