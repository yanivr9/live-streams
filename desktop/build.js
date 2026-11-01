// Puts the TV app's screens (webos/) and the PC player together in app/, which the window loads.
const fs = require('fs');
const path = require('path');

const here = __dirname;
const web = path.join(here, '..', 'webos');
const out = path.join(here, 'app');

fs.rmSync(out, { recursive: true, force: true });
for (const part of ['css', 'js', 'img', 'largeIcon.png', 'icon.png']) {
  fs.cpSync(path.join(web, part), path.join(out, part), { recursive: true });
}
fs.mkdirSync(path.join(out, 'vendor'), { recursive: true });
fs.copyFileSync(require.resolve('hls.js/dist/hls.min.js'), path.join(out, 'vendor', 'hls.min.js'));
fs.copyFileSync(require.resolve('mpegts.js/dist/mpegts.js'), path.join(out, 'vendor', 'mpegts.js'));
fs.copyFileSync(path.join(here, 'player.js'), path.join(out, 'js', 'player.js'));

// The same page as on the TV, with the player scripts loaded before the app
let html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
html = html
  .replace('<script src="js/lists.js"></script>',
    '<script src="vendor/hls.min.js"></script>\n  <script src="vendor/mpegts.js"></script>\n  <script src="js/player.js"></script>\n  <script src="js/lists.js"></script>')
  .replace('Use the remote: OK opens the keyboard, Back closes it.', 'Click a field to type in it.');
fs.writeFileSync(path.join(out, 'index.html'), html);
console.log('app/ ready');
