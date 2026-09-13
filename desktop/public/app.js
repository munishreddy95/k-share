const state = {
  current: null,
  mode: "home",
  pendingMode: null
};

const elements = {
  heroTitle: document.getElementById("heroTitle"),
  heroSubtitle: document.getElementById("heroSubtitle"),
  statusPill: document.getElementById("statusPill"),
  modeTabsContainer: document.querySelector(".mode-tabs"),
  modeSwitchDialog: document.getElementById("modeSwitchDialog"),
  modeSwitchCopy: document.getElementById("modeSwitchCopy"),
  stayModeButton: document.getElementById("stayModeButton"),
  confirmModeSwitchButton: document.getElementById("confirmModeSwitchButton"),
  modeTabs: [...document.querySelectorAll("[data-mode]")],
  modePanels: [...document.querySelectorAll("[data-panel]")],
  receivePanel: document.querySelector("[data-panel=\"receive\"]"),
  sendPanel: document.querySelector("[data-panel=\"send\"]"),
  localSharePanel: document.querySelector("[data-panel=\"localShare\"]"),
  liveSharePanel: document.querySelector("[data-panel=\"liveShare\"]"),
  transferPanels: [...document.querySelectorAll("[data-transfer-panel]")],
  hostSelect: document.getElementById("hostSelect"),
  saveDir: document.getElementById("saveDir"),
  receiveQrWrap: document.getElementById("receiveQrWrap"),
  receivePayloadText: document.getElementById("receivePayloadText"),
  startReceiveButton: document.getElementById("startReceiveButton"),
  stopReceiveButton: document.getElementById("stopReceiveButton"),
  refreshReceiveQrButton: document.getElementById("refreshReceiveQrButton"),
  openFolderButton: document.getElementById("openFolderButton"),
  cancelReceiveSessionButton: document.getElementById("cancelReceiveSessionButton"),
  receiveOptionsCard: document.getElementById("receiveOptionsCard"),
  receivePeer: document.getElementById("receivePeer"),
  receiveTransferPanel: document.querySelector("[data-transfer-panel=\"receive\"]"),
  receiveTransferList: document.getElementById("receiveTransferList"),
  receiveTransferCounter: document.getElementById("receiveTransferCounter"),
  sendQrWrap: document.getElementById("sendQrWrap"),
  sendPayloadText: document.getElementById("sendPayloadText"),
  chooseSendFilesButton: document.getElementById("chooseSendFilesButton"),
  startSendButton: document.getElementById("startSendButton"),
  stopSendButton: document.getElementById("stopSendButton"),
  refreshSendQrButton: document.getElementById("refreshSendQrButton"),
  clearSendFilesButton: document.getElementById("clearSendFilesButton"),
  cancelSendSessionButton: document.getElementById("cancelSendSessionButton"),
  sendOptionsCard: document.getElementById("sendOptionsCard"),
  sendPeer: document.getElementById("sendPeer"),
  sendTransferPanel: document.querySelector("[data-transfer-panel=\"send\"]"),
  selectedFileList: document.getElementById("selectedFileList"),
  selectedFileCounter: document.getElementById("selectedFileCounter"),
  sendTransferList: document.getElementById("sendTransferList"),
  sendTransferCounter: document.getElementById("sendTransferCounter"),
  localHostSelect: document.getElementById("localHostSelect"),
  localQrWrap: document.getElementById("localQrWrap"),
  localPayloadText: document.getElementById("localPayloadText"),
  chooseLocalFilesButton: document.getElementById("chooseLocalFilesButton"),
  clearLocalFilesButton: document.getElementById("clearLocalFilesButton"),
  startLocalButton: document.getElementById("startLocalButton"),
  stopLocalButton: document.getElementById("stopLocalButton"),
  refreshLocalQrButton: document.getElementById("refreshLocalQrButton"),
  localSelectedFileList: document.getElementById("localSelectedFileList"),
  localSelectedFileCounter: document.getElementById("localSelectedFileCounter"),
  localVisitorList: document.getElementById("localVisitorList"),
  localVisitorCounter: document.getElementById("localVisitorCounter"),
  liveQrWrap: document.getElementById("liveQrWrap"),
  livePayloadText: document.getElementById("livePayloadText"),
  liveFullUrlText: document.getElementById("liveFullUrlText"),
  liveRelayStatus: document.getElementById("liveRelayStatus"),
  chooseLiveFilesButton: document.getElementById("chooseLiveFilesButton"),
  clearLiveFilesButton: document.getElementById("clearLiveFilesButton"),
  startLiveButton: document.getElementById("startLiveButton"),
  stopLiveButton: document.getElementById("stopLiveButton"),
  refreshLiveButton: document.getElementById("refreshLiveButton"),
  liveSelectedFileList: document.getElementById("liveSelectedFileList"),
  liveSelectedFileCounter: document.getElementById("liveSelectedFileCounter"),
  liveApprovalList: document.getElementById("liveApprovalList"),
  liveApprovalCounter: document.getElementById("liveApprovalCounter"),
  liveVisitorList: document.getElementById("liveVisitorList"),
  liveVisitorCounter: document.getElementById("liveVisitorCounter")
};

const copy = {
  home: {
    title: "Transfer workspace",
    subtitle: "Choose Nearby, Local Web Share, or Live Share. Each mode keeps its own files."
  },
  receive: {
    title: "Receive workspace",
    subtitle: "Show the QR. On Android, choose Sender, scan this desktop, then select files."
  },
  send: {
    title: "Send workspace",
    subtitle: "Show the QR. On Android, choose Desktop QR to connect, then choose files here."
  },
  localShare: {
    title: "Local Web Share",
    subtitle: "Choose desktop files and serve them to browsers on the same Wi-Fi network."
  },
  liveShare: {
    title: "Live Share",
    subtitle: "Choose a separate file list, create a relay link, then approve remote visitors."
  }
};

const modeLabels = {
  receive: "Receive From Phone",
  send: "Send To Phone",
  localShare: "Local Web Share",
  liveShare: "Live Share"
};

async function postJson(url, body = {}) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  return response.json();
}

async function loadState() {
  const response = await fetch("/api/state", { cache: "no-store" });
  renderState(await response.json());
}

function normalizeState(next) {
  return {
    receiver: next && next.receiver || {},
    sender: next && next.sender || {},
    localShare: next && next.localShare || {},
    liveShare: next && next.liveShare || {}
  };
}

function renderState(next) {
  state.current = normalizeState(next);
  if (state.mode === "home") {
    if (state.current.sender.running) {
      state.mode = "send";
    } else if (state.current.receiver.running) {
      state.mode = "receive";
    }
  }
  if (state.pendingMode && !isModeSwitchDestructive(state.pendingMode)) {
    hideModeSwitchDialog();
  }
  renderMode();
  renderHostOptions(state.current);
  renderLocalHostOptions(state.current);
  renderReceive(state.current.receiver);
  renderSend(state.current.sender);
  renderWebShare("local", state.current.localShare);
  renderWebShare("live", state.current.liveShare);
}

function renderMode() {
  const activeCopy = copy[state.mode] || copy.home;
  elements.heroTitle.textContent = activeCopy.title;
  elements.heroSubtitle.textContent = activeCopy.subtitle;
  elements.statusPill.textContent = state.mode === "home"
    ? "Choose a mode"
    : activeModeState().status || "Ready";
  elements.modeTabsContainer.classList.remove("hidden");
  for (const tab of elements.modeTabs) {
    tab.classList.toggle("active", tab.dataset.mode === state.mode);
  }
  for (const panel of elements.modePanels) {
    panel.classList.toggle("active", state.mode !== "home" && panel.dataset.panel === state.mode);
  }
  for (const panel of elements.transferPanels) {
    panel.classList.toggle("active", state.mode !== "home" && panel.dataset.transferPanel === state.mode);
  }
}

function activeModeState() {
  return (state.current && state.current[state.mode]) || {};
}

function runningMode() {
  const current = state.current || {};
  if (current.receiver && current.receiver.running) {
    return "receive";
  }
  if (current.sender && current.sender.running) {
    return "send";
  }
  return "";
}

function isConnectedSession() {
  const current = state.current || {};
  return !!((current.receiver && current.receiver.connectedPeer)
    || (current.sender && current.sender.connectedPeer));
}

function isModeSwitchDestructive(targetMode) {
  if (targetMode !== "receive" && targetMode !== "send") {
    return false;
  }
  const activeRunningMode = runningMode();
  return isConnectedSession() && !!activeRunningMode && activeRunningMode !== targetMode;
}

function showModeSwitchDialog(targetMode) {
  const activeRunningMode = runningMode();
  state.pendingMode = targetMode;
  elements.modeSwitchCopy.textContent = `${modeLabels[activeRunningMode] || "The current session"} is still active. Switching to ${modeLabels[targetMode]} will cancel that session first.`;
  elements.confirmModeSwitchButton.textContent = `Switch to ${targetMode === "receive" ? "Receive" : "Send"}`;
  elements.modeSwitchDialog.classList.remove("hidden");
  elements.statusPill.textContent = "Confirm switch";
  elements.stayModeButton.focus();
}

function hideModeSwitchDialog() {
  state.pendingMode = null;
  elements.modeSwitchDialog.classList.add("hidden");
}

function renderHostOptions(next) {
  const receiver = next.receiver || {};
  const sender = next.sender || {};
  const interfaces = receiver.interfaces || sender.interfaces || [];
  renderSelect(elements.hostSelect, interfaces, activeModeState().host || receiver.host || sender.host || "127.0.0.1");
}

function renderLocalHostOptions(next) {
  const localShare = next.localShare || {};
  renderSelect(elements.localHostSelect, localShare.interfaces || [], localShare.host || "127.0.0.1");
}

function renderSelect(select, interfaces, selectedValue) {
  const previous = select.value;
  const selected = interfaces.some((item) => item.address === previous) ? previous : selectedValue;
  select.innerHTML = "";
  for (const item of interfaces) {
    const option = document.createElement("option");
    option.value = item.address;
    option.textContent = item.label;
    select.appendChild(option);
  }
  if (![...select.options].some((option) => option.value === selectedValue)) {
    const option = document.createElement("option");
    option.value = selectedValue;
    option.textContent = selectedValue;
    select.appendChild(option);
  }
  select.value = selected || selectedValue;
}

function renderReceive(receiver) {
  const transfers = receiver.transfers || [];
  const unlocked = !!receiver.connectedPeer || transfers.length > 0;
  const connected = !!receiver.connectedPeer;
  elements.receivePayloadText.textContent = receiver.running ? receiver.payload || "" : "";
  elements.receivePeer.value = receiver.connectedPeer || "Waiting for phone scan";
  elements.saveDir.value = receiver.saveDir || "";
  elements.hostSelect.disabled = connected;
  elements.startReceiveButton.disabled = !!receiver.running;
  elements.startReceiveButton.textContent = receiver.running ? "QR active" : "Show QR";
  elements.stopReceiveButton.disabled = !receiver.running;
  elements.receiveOptionsCard.classList.toggle("hidden", !unlocked);
  elements.receivePanel.classList.toggle("pairing", receiver.running && !unlocked);
  elements.receivePanel.classList.toggle("connected", connected);
  elements.receivePayloadText.classList.toggle("hidden", connected);
  elements.cancelReceiveSessionButton.classList.toggle("hidden", !connected);
  renderQr(elements.receiveQrWrap, receiver.running ? receiver.payload : "", "Click Receive to show QR.");
  renderTransfers(elements.receiveTransferList, elements.receiveTransferCounter, transfers, "Waiting for the phone to connect.");
}

function renderSend(sender) {
  const selectedFiles = sender.selectedFiles || [];
  const transfers = sender.transfers || [];
  const unlocked = !!sender.connectedPeer || selectedFiles.length > 0 || transfers.length > 0;
  const connected = !!sender.connectedPeer;
  elements.sendPayloadText.textContent = sender.running ? sender.payload || "" : "";
  elements.sendPeer.value = sender.connectedPeer || "Waiting for phone scan";
  elements.startSendButton.disabled = !!sender.running;
  elements.startSendButton.textContent = sender.running ? "QR active" : "Show QR";
  elements.stopSendButton.disabled = !sender.running;
  elements.clearSendFilesButton.disabled = selectedFiles.length === 0;
  elements.sendOptionsCard.classList.toggle("hidden", !unlocked);
  elements.sendPanel.classList.toggle("pairing", sender.running && !unlocked);
  elements.sendPanel.classList.toggle("connected", connected);
  elements.sendPayloadText.classList.toggle("hidden", connected);
  elements.cancelSendSessionButton.classList.toggle("hidden", !connected);
  renderQr(elements.sendQrWrap, sender.running ? sender.payload : "", "Click Send to show QR.");
  renderSelectedFiles(elements.selectedFileList, elements.selectedFileCounter, selectedFiles, "Choose files, start sender, then let the phone scan this QR.");
  renderTransfers(elements.sendTransferList, elements.sendTransferCounter, transfers, "Choose files and let the phone scan.");
}

function renderWebShare(scope, share) {
  const isLive = scope === "live";
  const selectedList = isLive ? elements.liveSelectedFileList : elements.localSelectedFileList;
  const selectedCounter = isLive ? elements.liveSelectedFileCounter : elements.localSelectedFileCounter;
  const visitorList = isLive ? elements.liveVisitorList : elements.localVisitorList;
  const visitorCounter = isLive ? elements.liveVisitorCounter : elements.localVisitorCounter;
  const payloadText = isLive ? elements.livePayloadText : elements.localPayloadText;
  const qrWrap = isLive ? elements.liveQrWrap : elements.localQrWrap;
  const files = share.files || [];
  const url = isLive ? share.publicUrl || share.url || "" : share.url || "";
  renderSelectedFiles(selectedList, selectedCounter, files, `Choose files only for ${isLive ? "Live Share" : "Local Web Share"}.`);
  payloadText.textContent = url;
  renderQr(qrWrap, share.running && url ? url : "", isLive ? "Create a global link to show QR." : "Start local share to show QR.");
  if (isLive) {
    elements.liveRelayStatus.textContent = share.status || "Global link off.";
    elements.liveFullUrlText.textContent = share.fullUrl && share.fullUrl !== url ? share.fullUrl : (share.tinyUrlError || "");
    renderApprovalRequests(share.pendingRequests || []);
  }
  renderVisitors(scope, visitorList, visitorCounter, share.visitors || []);
  if (!isLive) {
    elements.startLocalButton.disabled = !!share.running || files.length === 0;
    elements.stopLocalButton.disabled = !share.running;
    elements.clearLocalFilesButton.disabled = files.length === 0;
  } else {
    elements.startLiveButton.disabled = !!share.running || files.length === 0;
    elements.stopLiveButton.disabled = !share.running;
    elements.clearLiveFilesButton.disabled = files.length === 0;
  }
}

function renderQr(target, payload, emptyText) {
  if (!payload) {
    target.innerHTML = `<p class="empty">${escapeHtml(emptyText)}</p>`;
    return;
  }
  try {
    target.innerHTML = window.FluxQr.toSvg(payload);
  } catch (error) {
    target.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
  }
}

function renderSelectedFiles(target, counter, files, emptyText) {
  counter.textContent = String(files.length);
  if (!files.length) {
    target.innerHTML = `<p class="empty">${escapeHtml(emptyText)}</p>`;
    return;
  }
  target.innerHTML = files.map((file) => {
    const extension = file.name.includes(".") ? file.name.split(".").pop().slice(0, 4).toUpperCase() : "FILE";
    return `
      <article class="selected-file">
        <span class="file-badge">${escapeHtml(extension)}</span>
        <div>
          <strong>${escapeHtml(file.name)}</strong>
          <small>${escapeHtml(file.path || file.mimeType || file.category || "")}</small>
        </div>
        <span class="size">${escapeHtml(formatBytes(file.size))}</span>
      </article>
    `;
  }).join("");
}

function renderTransfers(target, counter, transfers, emptyText) {
  counter.textContent = String(transfers.length);
  if (!transfers.length) {
    target.innerHTML = `<p class="empty">${escapeHtml(emptyText)}</p>`;
    return;
  }
  target.innerHTML = transfers.map(renderTransfer).join("");
}

function renderTransfer(item) {
  const statusClass = String(item.status || "").toLowerCase();
  const total = Number(item.size);
  const copied = Number(item.transferred);
  const percent = total > 0 ? Math.max(0, Math.min(100, copied * 100 / total)) : 0;
  const detail = [
    formatBytes(copied),
    total >= 0 ? formatBytes(total) : "",
    item.speed > 0 ? `${formatBytes(item.speed)}/s` : "",
    item.path || ""
  ].filter(Boolean).join(" · ");
  return `
    <article class="transfer ${escapeHtml(statusClass)}">
      <div>
        <strong>${escapeHtml(item.name || "file")}</strong>
        <small>${escapeHtml(detail)}</small>
      </div>
      <span class="state">${escapeHtml(item.status || "Waiting")}</span>
      <div class="bar"><span style="width:${percent}%"></span></div>
    </article>
  `;
}

function renderApprovalRequests(requests) {
  elements.liveApprovalCounter.textContent = String(requests.length);
  if (!requests.length) {
    elements.liveApprovalList.innerHTML = "<p class=\"empty\">No pending approval requests.</p>";
    return;
  }
  elements.liveApprovalList.innerHTML = requests.map((request) => `
    <article class="visitor pending">
      <div>
        <strong>${escapeHtml(request.email || "Visitor")}</strong>
        <small>${escapeHtml([request.device, request.ip, request.location].filter(Boolean).join(" · "))}</small>
      </div>
      <div class="inline-actions">
        <button class="primary mini" data-approve="${escapeHtml(request.id)}" type="button">Approve</button>
        <button class="danger mini" data-deny="${escapeHtml(request.id)}" type="button">Deny</button>
      </div>
    </article>
  `).join("");
}

function renderVisitors(scope, target, counter, visitors) {
  counter.textContent = String(visitors.length);
  if (!visitors.length) {
    target.innerHTML = "<p class=\"empty\">No visitors yet.</p>";
    return;
  }
  target.innerHTML = visitors.map((visitor) => `
    <article class="visitor">
      <div>
        <strong>${escapeHtml(visitor.email || visitor.ip || "Browser visitor")}</strong>
        <small>${escapeHtml([visitor.device, visitor.location, formatBytes(visitor.bytesServed || 0)].filter(Boolean).join(" · "))}</small>
      </div>
      <button class="ghost mini" data-revoke-scope="${escapeHtml(scope)}" data-revoke="${escapeHtml(visitor.token)}" type="button">Remove</button>
    </article>
  `).join("");
}

function formatBytes(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) {
    return "-";
  }
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = value;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
}

function escapeHtml(value) {
  return String(value == null ? "" : "")
    .replace("", String(value == null ? "" : value))
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function selectedHost() {
  const receiver = state.current && state.current.receiver || {};
  const sender = state.current && state.current.sender || {};
  return elements.hostSelect.value || activeModeState().host || receiver.host || sender.host || undefined;
}

function selectedLocalHost() {
  const localShare = state.current && state.current.localShare || {};
  return elements.localHostSelect.value || localShare.host || undefined;
}

async function refreshActiveHost() {
  const host = selectedHost();
  if (state.mode === "receive") {
    renderState(await postJson("/api/receive/start", { host }));
  } else if (state.mode === "send") {
    renderState(await postJson("/api/send/start", { host }));
  }
}

async function chooseFilesFor(endpoint, mode) {
  if (!window.fluxDesktop || typeof window.fluxDesktop.chooseFiles !== "function") {
    elements.statusPill.textContent = "File picker works in the desktop app.";
    return;
  }
  const paths = await window.fluxDesktop.chooseFiles();
  if (!paths.length) {
    return;
  }
  state.mode = mode;
  renderState(await postJson(endpoint, { paths }));
}

for (const tab of elements.modeTabs) {
  tab.addEventListener("click", () => {
    requestModeActivation(tab.dataset.mode).catch((error) => {
      elements.statusPill.textContent = error.message;
    });
  });
}

async function requestModeActivation(mode) {
  if (mode === state.mode && !isModeSwitchDestructive(mode)) {
    return;
  }
  if (isModeSwitchDestructive(mode)) {
    showModeSwitchDialog(mode);
    return;
  }
  await activateMode(mode);
}

async function activateMode(mode) {
  hideModeSwitchDialog();
  state.mode = mode;
  renderState(state.current);
  if (mode === "receive") {
    renderState(await postJson("/api/receive/start", { host: selectedHost(), fresh: true }));
  } else if (mode === "send") {
    renderState(await postJson("/api/send/start", { host: selectedHost(), fresh: true }));
  }
}

elements.stayModeButton.addEventListener("click", () => {
  hideModeSwitchDialog();
  elements.statusPill.textContent = activeModeState().status || "Ready";
});

elements.confirmModeSwitchButton.addEventListener("click", () => {
  const targetMode = state.pendingMode;
  if (!targetMode) {
    hideModeSwitchDialog();
    return;
  }
  activateMode(targetMode).catch((error) => {
    elements.statusPill.textContent = error.message;
  });
});

elements.startReceiveButton.addEventListener("click", async () => {
  if (isModeSwitchDestructive("receive")) {
    showModeSwitchDialog("receive");
    return;
  }
  renderState(await postJson("/api/receive/start", { host: selectedHost(), fresh: true }));
});

elements.stopReceiveButton.addEventListener("click", async () => {
  renderState(await postJson("/api/receive/stop"));
  state.mode = "home";
  renderState(state.current);
});

elements.cancelReceiveSessionButton.addEventListener("click", async () => {
  renderState(await postJson("/api/receive/stop"));
  state.mode = "home";
  renderState(state.current);
});

elements.refreshReceiveQrButton.addEventListener("click", async () => {
  renderState(await postJson("/api/receive/start", { host: selectedHost() }));
});

elements.hostSelect.addEventListener("change", () => {
  refreshActiveHost().catch((error) => {
    elements.statusPill.textContent = error.message;
  });
});

elements.localHostSelect.addEventListener("change", async () => {
  if (state.current.localShare && state.current.localShare.running) {
    renderState(await postJson("/api/local/start", { host: selectedLocalHost() }));
  }
});

elements.openFolderButton.addEventListener("click", () => {
  postJson("/api/open-folder").catch(() => {});
});

elements.chooseSendFilesButton.addEventListener("click", () => chooseFilesFor("/api/send/files", "send"));
elements.chooseLocalFilesButton.addEventListener("click", () => chooseFilesFor("/api/local/files", "localShare"));
elements.chooseLiveFilesButton.addEventListener("click", () => chooseFilesFor("/api/live/files", "liveShare"));

elements.startSendButton.addEventListener("click", async () => {
  if (isModeSwitchDestructive("send")) {
    showModeSwitchDialog("send");
    return;
  }
  state.mode = "send";
  renderState(await postJson("/api/send/start", { host: selectedHost(), fresh: true }));
});

elements.stopSendButton.addEventListener("click", async () => {
  renderState(await postJson("/api/send/stop"));
  state.mode = "home";
  renderState(state.current);
});

elements.cancelSendSessionButton.addEventListener("click", async () => {
  renderState(await postJson("/api/send/stop"));
  state.mode = "home";
  renderState(state.current);
});

elements.refreshSendQrButton.addEventListener("click", async () => {
  state.mode = "send";
  renderState(await postJson("/api/send/start", { host: selectedHost() }));
});

elements.clearSendFilesButton.addEventListener("click", async () => {
  renderState(await postJson("/api/send/clear"));
});

elements.clearLocalFilesButton.addEventListener("click", async () => {
  renderState(await postJson("/api/local/clear"));
});

elements.startLocalButton.addEventListener("click", async () => {
  state.mode = "localShare";
  renderState(await postJson("/api/local/start", { host: selectedLocalHost() }));
});

elements.stopLocalButton.addEventListener("click", async () => {
  renderState(await postJson("/api/local/stop"));
});

elements.refreshLocalQrButton.addEventListener("click", async () => {
  if (state.current.localShare && state.current.localShare.running) {
    renderState(await postJson("/api/local/start", { host: selectedLocalHost() }));
  }
});

elements.clearLiveFilesButton.addEventListener("click", async () => {
  renderState(await postJson("/api/live/clear"));
});

elements.startLiveButton.addEventListener("click", async () => {
  state.mode = "liveShare";
  renderState(await postJson("/api/live/start"));
});

elements.stopLiveButton.addEventListener("click", async () => {
  renderState(await postJson("/api/live/stop"));
});

elements.refreshLiveButton.addEventListener("click", async () => {
  await loadState();
});

elements.liveApprovalList.addEventListener("click", async (event) => {
  const approve = event.target.closest("[data-approve]");
  const deny = event.target.closest("[data-deny]");
  if (approve) {
    renderState(await postJson("/api/web/approve", { scope: "live", id: approve.dataset.approve }));
  } else if (deny) {
    renderState(await postJson("/api/web/deny", { scope: "live", id: deny.dataset.deny }));
  }
});

document.body.addEventListener("click", async (event) => {
  const revoke = event.target.closest("[data-revoke]");
  if (!revoke) {
    return;
  }
  renderState(await postJson("/api/web/revoke", {
    scope: revoke.dataset.revokeScope || "live",
    token: revoke.dataset.revoke
  }));
});

const events = new EventSource("/events");
events.onmessage = (event) => {
  renderState(JSON.parse(event.data));
};
events.onerror = () => {
  elements.statusPill.textContent = "Reconnecting";
};

let pollBusy = false;
setInterval(async () => {
  if (pollBusy) {
    return;
  }
  pollBusy = true;
  try {
    await loadState();
  } catch (_) {
  } finally {
    pollBusy = false;
  }
}, 1000);

loadState().catch((error) => {
  elements.statusPill.textContent = error.message;
});
