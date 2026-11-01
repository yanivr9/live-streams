# Stream Desk for Windows

The TV app's essentials on a PC: sign in, Sport / Israeli TV / All tabs with logos and what's on now, and a player.
It is the LG version's screens (`../webos`) in an Electron window, with hls.js and mpegts.js playing the streams
(`player.js`), since a PC's browser engine can't play them by itself.

| Keyboard / mouse | Channels | Watching |
| --- | --- | --- |
| Arrows, mouse wheel, pointer | move between tiles | Up / Down: next / previous channel |
| Enter, click | play | show the channel |
| Page Up / Page Down | | next / previous channel |
| Numbers | play channel N of the tab | 3 → channel 3, 1 then 2 → channel 12 |
| Backspace, Esc | to the tabs | back to the channels |
| F11, Alt+Enter, double-click | full screen on / off | |

## Install

Unzip `StreamDesk-Windows.zip` anywhere (for example Documents) and run `Stream Desk.exe` in the folder.
Windows may warn that the program is from an unknown publisher: More info → Run anyway.

## Build

```sh
npm install
npm run dist     # → out/Stream Desk-win32-x64 (zip it to hand it over; it's ~100 MB, too big for a git file)
npm start        # run it on this computer
```
