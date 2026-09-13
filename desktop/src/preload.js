const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("fluxDesktop", {
  chooseFiles: () => ipcRenderer.invoke("flux:choose-files")
});
