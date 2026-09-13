const assert = require("assert");
const net = require("net");
const os = require("os");
const fsp = require("fs/promises");
const path = require("path");
const {
  NearbyReceiver,
  NearbySender,
  BufferedSocket,
  constants,
  nearbyConnectedEvent,
  writeJavaUtf,
  writeInt
} = require("../src/main");

async function connect(port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: "127.0.0.1", port }, () => resolve(socket));
    socket.once("error", reject);
  });
}

function waitForState(emitter, label, predicate) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      emitter.off("state", onState);
      reject(new Error(`Timed out waiting for ${label}`));
    }, 5000);
    const onState = (state) => {
      const result = predicate(state);
      if (result) {
        clearTimeout(timer);
        emitter.off("state", onState);
        resolve(result);
      }
    };
    emitter.on("state", onState);
    onState(emitter.state());
  });
}

function waitForTransferStatus(receiver, id, status) {
  return waitForState(receiver, `${id} to become ${status}`, (state) => {
    const item = state.transfers.find((transfer) => transfer.id === id);
    return item && item.status === status ? item : null;
  });
}

function writeLong(socket, value) {
  const buffer = Buffer.allocUnsafe(8);
  buffer.writeBigInt64BE(BigInt(value), 0);
  socket.write(buffer);
}

async function run() {
  const saveDir = await fsp.mkdtemp(path.join(os.tmpdir(), "flux-desktop-receiver-"));
  const receiver = new NearbyReceiver({ host: "127.0.0.1", port: 0, saveDir });
  await receiver.start();

  const payload = Buffer.from("hello from fake android sender");
  const fileName = "desktop-receiver-test.txt";
  const socket = await connect(receiver.port);
  const reader = new BufferedSocket(socket);

  writeJavaUtf(socket, nearbyConnectedEvent(receiver.sessionToken));
  writeJavaUtf(socket, "Fake Android");
  writeJavaUtf(socket, constants.NEARBY_TRANSFER_MAGIC);
  writeInt(socket, 1);
  writeJavaUtf(socket, "fake-file-id");
  writeJavaUtf(socket, fileName);
  writeJavaUtf(socket, "text/plain");
  writeLong(socket, payload.length);

  const requestedOffset = Number(await reader.readLong());
  assert.strictEqual(requestedOffset, 0);

  const completed = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("Timed out waiting for transfer completion")), 5000);
    receiver.once("transfer-complete", () => {
      clearTimeout(timer);
      resolve();
    });
  });

  writeInt(socket, payload.length);
  socket.write(payload);
  writeInt(socket, constants.NEARBY_CHUNK_DONE);

  await completed;

  const saved = await fsp.readFile(path.join(saveDir, fileName));
  assert.strictEqual(saved.toString("utf8"), payload.toString("utf8"));

  const interruptedId = "fake-interrupted-file-id";
  const interruptedPayload = Buffer.from("this chunk arrived before the phone disappeared");
  const interruptedSocket = await connect(receiver.port);
  const interruptedReader = new BufferedSocket(interruptedSocket);
  writeJavaUtf(interruptedSocket, nearbyConnectedEvent(receiver.sessionToken));
  writeJavaUtf(interruptedSocket, "Fake Android");
  writeJavaUtf(interruptedSocket, constants.NEARBY_TRANSFER_MAGIC);
  writeInt(interruptedSocket, 1);
  writeJavaUtf(interruptedSocket, interruptedId);
  writeJavaUtf(interruptedSocket, "interrupted-video.mkv");
  writeJavaUtf(interruptedSocket, "video/x-matroska");
  writeLong(interruptedSocket, interruptedPayload.length + 64);

  const interruptedOffset = Number(await interruptedReader.readLong());
  assert.strictEqual(interruptedOffset, 0);

  writeInt(interruptedSocket, interruptedPayload.length);
  interruptedSocket.write(interruptedPayload);
  interruptedSocket.end();

  const interrupted = await waitForTransferStatus(receiver, interruptedId, "Interrupted");
  assert.strictEqual(interrupted.transferred, interruptedPayload.length);
  assert.match(receiver.state().status, /Sender disconnected|Receive failed/);

  await receiver.stop();

  const desktopSendPayload = Buffer.from("hello from desktop sender");
  const desktopFile = path.join(saveDir, "desktop-send-test.txt");
  await fsp.writeFile(desktopFile, desktopSendPayload);
  const sender = new NearbySender({ host: "127.0.0.1", port: 0 });
  await sender.start();

  const receiveSocket = await connect(sender.port);
  const receiveReader = new BufferedSocket(receiveSocket);
  await waitForState(sender, "phone connection before files", (next) => next.connectedPeer);
  await sender.setFiles([desktopFile]);

  assert.strictEqual(await receiveReader.readJavaUtf(), nearbyConnectedEvent(sender.sessionToken));
  assert.ok((await receiveReader.readJavaUtf()).length > 0);
  assert.strictEqual(await receiveReader.readJavaUtf(), constants.NEARBY_TRANSFER_MAGIC);
  assert.strictEqual(await receiveReader.readInt(), 1);
  assert.ok((await receiveReader.readJavaUtf()).startsWith("desktop-"));
  assert.strictEqual(await receiveReader.readJavaUtf(), "desktop-send-test.txt");
  assert.strictEqual(await receiveReader.readJavaUtf(), "text/plain");
  assert.strictEqual(Number(await receiveReader.readLong()), desktopSendPayload.length);

  writeLong(receiveSocket, 0);
  const receivedChunks = [];
  while (true) {
    const chunkLength = await receiveReader.readInt();
    if (chunkLength === constants.NEARBY_CHUNK_DONE) {
      break;
    }
    receivedChunks.push(await receiveReader.read(chunkLength));
  }
  assert.strictEqual(Buffer.concat(receivedChunks).toString("utf8"), desktopSendPayload.toString("utf8"));
  await waitForTransferStatus(sender, sender.files[0].id, "Completed");
  await sender.stop();

  await fsp.rm(saveDir, { recursive: true, force: true });
  console.log("nearby desktop protocol test passed");
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
