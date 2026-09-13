(function () {
  "use strict";

  const DB_NAME = "flux-file-share-extension";
  const DB_VERSION = 1;
  const FILE_STORE = "files";

  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(FILE_STORE)) {
          db.createObjectStore(FILE_STORE, { keyPath: "id" });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Could not open extension storage."));
    });
  }

  function transaction(storeName, mode, callback) {
    return openDb().then((db) => new Promise((resolve, reject) => {
      const tx = db.transaction(storeName, mode);
      const store = tx.objectStore(storeName);
      let result;
      tx.oncomplete = () => {
        db.close();
        resolve(result);
      };
      tx.onerror = () => {
        db.close();
        reject(tx.error || new Error("Extension storage failed."));
      };
      result = callback(store);
    }));
  }

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Extension storage request failed."));
    });
  }

  async function hashText(value) {
    const bytes = new TextEncoder().encode(value);
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest))
      .map((byte) => byte.toString(16).padStart(2, "0"))
      .join("");
  }

  function categoryForFile(name, type) {
    const lowerName = String(name || "").toLowerCase();
    const lowerType = String(type || "").toLowerCase();
    if (lowerType.startsWith("video/") || /\.(mkv|mp4|mov|webm|avi)$/i.test(lowerName)) {
      return "videos";
    }
    if (lowerType.startsWith("audio/") || /\.(mp3|m4a|aac|flac|wav|ogg)$/i.test(lowerName)) {
      return "songs";
    }
    if (/\.(zip|rar|7z|tar|gz|tgz|bz2|xz|jar|apk)$/i.test(lowerName)) {
      return "compressed";
    }
    return "files";
  }

  function playable(name, type) {
    const category = categoryForFile(name, type);
    return category === "videos" || category === "songs";
  }

  function safeFile(record) {
    return {
      id: record.id,
      name: record.name,
      size: record.size,
      type: record.type,
      mimeType: record.type,
      lastModified: record.lastModified,
      category: record.category,
      playable: record.playable
    };
  }

  async function putFiles(files) {
    const records = [];
    for (const file of Array.from(files || [])) {
      if (!file || typeof file.arrayBuffer !== "function") {
        continue;
      }
      const id = await hashText([
        file.name || "shared-file",
        file.size || 0,
        file.type || "application/octet-stream",
        file.lastModified || Date.now(),
        Math.random().toString(16).slice(2)
      ].join("|"));
      records.push({
        id,
        name: file.name || "shared-file",
        size: file.size || 0,
        type: file.type || "application/octet-stream",
        lastModified: file.lastModified || Date.now(),
        category: categoryForFile(file.name, file.type),
        playable: playable(file.name, file.type),
        blob: file
      });
    }
    if (!records.length) {
      return [];
    }
    await transaction(FILE_STORE, "readwrite", (store) => {
      for (const record of records) {
        store.put(record);
      }
    });
    return records.map(safeFile);
  }

  async function listFiles() {
    return transaction(FILE_STORE, "readonly", async (store) => {
      const records = await requestToPromise(store.getAll());
      return records.map(safeFile);
    });
  }

  async function getFile(id) {
    return transaction(FILE_STORE, "readonly", (store) => requestToPromise(store.get(id)));
  }

  async function removeFile(id) {
    await transaction(FILE_STORE, "readwrite", (store) => {
      store.delete(id);
    });
  }

  async function clearFiles() {
    await transaction(FILE_STORE, "readwrite", (store) => {
      store.clear();
    });
  }

  globalThis.FluxDb = {
    clearFiles,
    getFile,
    listFiles,
    putFiles,
    removeFile
  };
})();
