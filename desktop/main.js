// Stream Desk for Windows: the TV app's screens (from webos/) in a window, with a player library for the streams.
const { app, BrowserWindow, Menu } = require('electron');
const path = require('path');

// The provider's streams are fetched the way the Android app fetches them
const USER_AGENT = 'VLC/3.0.20 LibVLC/3.0.20';
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

function open() {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 960,
    minHeight: 540,
    backgroundColor: '#0E1113',
    title: 'Stream Desk',
    icon: path.join(__dirname, 'app', 'largeIcon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      // Only the app's own local pages run here; this lets them read the provider's lists and streams directly
      webSecurity: false,
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  Menu.setApplicationMenu(null);
  win.webContents.setUserAgent(USER_AGENT);
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  // F11 or Alt+Enter: full screen on and off
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type === 'keyDown' && (input.key === 'F11' || (input.alt && input.key === 'Enter'))) {
      win.setFullScreen(!win.isFullScreen());
      e.preventDefault();
    }
  });
  win.maximize();
  win.loadFile(path.join(__dirname, 'app', 'index.html'));
}

app.whenReady().then(open);
app.on('window-all-closed', () => app.quit());
