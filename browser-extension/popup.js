(function () {
  "use strict";

  const elements = {
    statusText: document.getElementById("statusText"),
    fileCounter: document.getElementById("fileCounter"),
    fileInput: document.getElementById("fileInput"),
    addCurrentTabButton: document.getElementById("addCurrentTabButton"),
    clearFilesButton: document.getElementById("clearFilesButton"),
    fileList: document.getElementById("fileList"),
    connectionPill: document.getElementById("connectionPill"),
    startButton: document.getElementById("startButton"),
    stopButton: document.getElementById("stopButton"),
    qrWrap: document.getElementById("qrWrap"),
    shareUrl: document.getElementById("shareUrl"),
    copyLinkButton: document.getElementById("copyLinkButton"),
    relayUrlInput: document.getElementById("relayUrlInput"),
    approvalCounter: document.getElementById("approvalCounter"),
    approvalList: document.getElementById("approvalList"),
    visitorCounter: document.getElementById("visitorCounter"),
    visitorList: document.getElementById("visitorList"),
    tabButtons: Array.from(document.querySelectorAll("[data-tab]")),
    panels: Array.from(document.querySelectorAll("[data-panel]"))
  };

  let currentState = null;
  let activeTab = "files";

  function ask(message) {
    return new Promise((resolve, reject) => {
      chrome.runtime.sendMessage(message, (response) => {
        if (chrome.runtime.lastError) {
          reject(new Error(chrome.runtime.lastError.message));
          return;
        }
        if (!response || !response.ok) {
          reject(new Error(response && response.error || "Extension command failed."));
          return;
        }
        resolve(response);
      });
    });
  }

  async function refresh() {
    const response = await ask({ type: "state.get" });
    currentState = response.state;
    render(currentState);
  }

  function render(state) {
    const files = state.files || [];
    const requests = state.pendingRequests || [];
    const visitors = state.visitors || [];
    elements.statusText.textContent = state.status || "Live Share is off.";
    elements.fileCounter.textContent = String(files.length);
    elements.connectionPill.textContent = state.connected ? "Online" : state.running ? "Syncing" : "Off";
    elements.connectionPill.classList.toggle("is-online", Boolean(state.connected));
    elements.connectionPill.classList.toggle("is-connecting", Boolean(!state.connected && state.running));
    elements.connectionPill.classList.toggle("is-off", Boolean(!state.connected && !state.running));
    elements.startButton.disabled = state.running || files.length === 0;
    elements.stopButton.disabled = !state.running;
    elements.clearFilesButton.disabled = files.length === 0 || state.running;
    elements.relayUrlInput.value = state.relayWsUrl || "";
    setTabAttention("access", requests.length > 0);
    renderFiles(files, state.running);
    renderLink(state);
    renderApprovals(requests);
    renderVisitors(visitors);
  }

  function renderFiles(files, locked) {
    if (!files.length) {
      elements.fileList.innerHTML = "<p class=\"empty\">No browser files selected yet.</p>";
      return;
    }
    elements.fileList.innerHTML = files.map((file) => `
      <article class="row">
        <span class="badge">${escapeHtml(extensionLabel(file.name))}</span>
        <div>
          <strong>${escapeHtml(file.name)}</strong>
          <small>${escapeHtml([file.category, formatBytes(file.size)].filter(Boolean).join(" · "))}</small>
        </div>
        <button class="ghost mini" data-remove-file="${escapeHtml(file.id)}" ${locked ? "disabled" : ""} type="button">Remove</button>
      </article>
    `).join("");
  }

  function renderLink(state) {
    const url = state.publicUrl || "";
    elements.shareUrl.textContent = url || "Create a live link after choosing files.";
    elements.copyLinkButton.disabled = !url;
    if (!url) {
      elements.qrWrap.innerHTML = "<p class=\"empty\">Create a live link to show QR.</p>";
      return;
    }
    try {
      elements.qrWrap.innerHTML = window.FluxQr.toSvg(url);
    } catch (error) {
      elements.qrWrap.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
    }
  }

  function renderApprovals(requests) {
    elements.approvalCounter.textContent = String(requests.length);
    if (!requests.length) {
      elements.approvalList.innerHTML = "<p class=\"empty\">No pending access requests.</p>";
      return;
    }
    elements.approvalList.innerHTML = requests.map((request) => `
      <article class="row two">
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

  function renderVisitors(visitors) {
    elements.visitorCounter.textContent = String(visitors.length);
    if (!visitors.length) {
      elements.visitorList.innerHTML = "<p class=\"empty\">No approved visitors yet.</p>";
      return;
    }
    elements.visitorList.innerHTML = visitors.map((visitor) => `
      <article class="row two">
        <div>
          <strong>${escapeHtml(visitor.email || visitor.ip || "Visitor")}</strong>
          <small>${escapeHtml([visitor.device, visitor.location, formatBytes(visitor.bytesServed || 0)].filter(Boolean).join(" · "))}</small>
        </div>
        <button class="ghost mini" data-remove-visitor="${escapeHtml(visitor.token)}" type="button">Remove</button>
      </article>
    `).join("");
  }

  async function addCurrentTab() {
    const tabs = await new Promise((resolve) => {
      chrome.tabs.query({ active: true, currentWindow: true }, resolve);
    });
    const tab = tabs && tabs[0];
    if (!tab || !tab.url) {
      throw new Error("Could not read the current tab.");
    }
    const title = tab.title || "Current tab";
    const text = `${title}\n${tab.url}\n`;
    const fileName = `${safeName(title).slice(0, 80) || "current-tab"}.url.txt`;
    const file = new File([text], fileName, { type: "text/plain", lastModified: Date.now() });
    await FluxDb.putFiles([file]);
    await ask({ type: "files.updated" });
    await refresh();
  }

  elements.fileInput.addEventListener("change", async () => {
    try {
      await FluxDb.putFiles(elements.fileInput.files);
      elements.fileInput.value = "";
      await ask({ type: "files.updated" });
      await refresh();
    } catch (error) {
      elements.statusText.textContent = error.message;
    }
  });

  elements.tabButtons.forEach((button) => {
    button.addEventListener("click", () => {
      setActiveTab(button.dataset.tab);
    });
  });

  elements.addCurrentTabButton.addEventListener("click", () => {
    addCurrentTab().catch((error) => {
      elements.statusText.textContent = error.message;
    });
  });

  elements.clearFilesButton.addEventListener("click", async () => {
    try {
      await ask({ type: "files.clear" });
      await refresh();
    } catch (error) {
      elements.statusText.textContent = error.message;
    }
  });

  elements.startButton.addEventListener("click", async () => {
    try {
      const response = await ask({
        type: "share.start",
        relayWsUrl: elements.relayUrlInput.value
      });
      currentState = response.state;
      render(currentState);
    } catch (error) {
      elements.statusText.textContent = error.message;
    }
  });

  elements.stopButton.addEventListener("click", async () => {
    try {
      const response = await ask({ type: "share.stop" });
      currentState = response.state;
      render(currentState);
    } catch (error) {
      elements.statusText.textContent = error.message;
    }
  });

  elements.copyLinkButton.addEventListener("click", async () => {
    const url = currentState && currentState.publicUrl;
    if (!url) {
      return;
    }
    await navigator.clipboard.writeText(url);
    elements.statusText.textContent = "Link copied.";
  });

  document.body.addEventListener("click", async (event) => {
    const removeFile = event.target.closest("[data-remove-file]");
    const approve = event.target.closest("[data-approve]");
    const deny = event.target.closest("[data-deny]");
    const removeVisitor = event.target.closest("[data-remove-visitor]");
    try {
      if (removeFile) {
        await ask({ type: "file.remove", id: removeFile.dataset.removeFile });
      } else if (approve) {
        await ask({ type: "access.approve", id: approve.dataset.approve });
      } else if (deny) {
        await ask({ type: "access.deny", id: deny.dataset.deny });
      } else if (removeVisitor) {
        await ask({ type: "visitor.remove", token: removeVisitor.dataset.removeVisitor });
      } else {
        return;
      }
      await refresh();
    } catch (error) {
      elements.statusText.textContent = error.message;
    }
  });

  chrome.runtime.onMessage.addListener((message) => {
    if (message && message.type === "state.changed") {
      refresh().catch(() => {});
    }
  });

  initializeActiveTab();

  setInterval(() => {
    refresh().catch(() => {});
  }, 1000);

  refresh().catch((error) => {
    elements.statusText.textContent = error.message;
  });

  function setActiveTab(tab) {
    activeTab = tab || "files";
    elements.tabButtons.forEach((button) => {
      button.classList.toggle("active", button.dataset.tab === activeTab);
    });
    elements.panels.forEach((panel) => {
      panel.classList.toggle("active", panel.dataset.panel === activeTab);
    });
  }

  function setTabAttention(tab, active) {
    elements.tabButtons.forEach((button) => {
      if (button.dataset.tab === tab) {
        button.classList.toggle("has-alert", active);
      }
    });
  }

  function initializeActiveTab() {
    const hashTab = String(location.hash || "").replace("#", "");
    if (isKnownTab(hashTab)) {
      setActiveTab(hashTab);
      return;
    }
    chrome.storage.local.get({ preferredPopupTab: "" }, (stored) => {
      if (isKnownTab(stored.preferredPopupTab)) {
        setActiveTab(stored.preferredPopupTab);
        chrome.storage.local.remove("preferredPopupTab");
      }
    });
  }

  function isKnownTab(tab) {
    return elements.tabButtons.some((button) => button.dataset.tab === tab);
  }

  function extensionLabel(name) {
    return String(name || "FILE").includes(".")
      ? String(name).split(".").pop().slice(0, 4).toUpperCase()
      : "FILE";
  }

  function safeName(name) {
    return String(name || "file").replace(/[\\/:*?"<>|]/g, "_").replace(/\s+/g, " ").trim();
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
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
})();
