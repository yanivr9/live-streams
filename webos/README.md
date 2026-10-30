# Stream Desk for LG webOS

The essentials of the Android app, running on the LG TV itself (webOS 4, 2018, and later):
sign-in to the IPTV (Xtream) account, the Sport and Israeli TV tabs with logos and what's on now,
all channels in the chosen folders, and a full-screen player for the Magic Remote.

| Remote | Home | Watching |
| --- | --- | --- |
| Arrows | move between tiles; Up from the first row goes to the tabs | Up / Down: next / previous channel |
| OK | play the channel, or open the tab | show the channel; retry after an error |
| CH + / − | | next / previous channel |
| Numbers | play channel N of the tab | 3 → channel 3, 1 then 2 → channel 12 |
| Back | to the tabs; on the tabs, leave the app | back to the channels |

Plays the provider's HLS (`.m3u8`) stream, falling back to `.ts`. Tiles pick Full HD at most
(Settings → Quality on this TV switches 4K on). Written in ES5 with no CSS grid, because webOS 4 runs Chrome 53.

## Install (needs a computer once)

1. On the TV: open **LG Content Store**, install **Developer Mode**, open it and sign in with a free LG developer
   account (create one at https://webostv.developer.lge.com). Turn on **Dev Mode Status**; the TV restarts.
   Open Developer Mode again and turn on **Key Server**. Note the TV's IP address and the **Passphrase** shown.
2. On the computer (Windows or Mac, same Wi-Fi): install **webOS Dev Manager**
   (https://github.com/webosbrew/dev-manager-desktop/releases), add the TV with its IP and passphrase.
3. In Dev Manager, **Apps → Install** and choose `dist/webos/com.streamdesk.tv_<version>_all.ipk`.

Developer Mode switches itself off after 50 hours: open the Developer Mode app on the TV and press
**Extend** before then (no computer needed). If it runs out, the app is removed and has to be installed again.

## Build

```sh
npm install -g @webos-tools/cli
ares-package webos -o dist/webos -e README.md   # → dist/webos/com.streamdesk.tv_<version>_all.ipk
```

| File | What it does |
| --- | --- |
| `js/lists.js` | Sport and Israeli TV lists: best quality per channel, exact names in quotes, logos, guide titles (no browser code, so it also runs in Node) |
| `js/app.js` | Screens, the remote, sign-in, the player (reconnects, one-screen switching gap) |
| `appinfo.json` | The webOS app's identity, icons and Back-key handling |
