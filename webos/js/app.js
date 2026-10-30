/*
 * Stream Desk for LG webOS: the TV side of the Android app's essentials. Sign in to the IPTV (Xtream) account,
 * the Sport and Israeli TV tabs with logos and what's on now, all channels in the chosen folders, and a full-screen
 * player driven by the Magic Remote (CH +/−, arrows, number keys, OK, Back).
 * Written in ES5 because webOS 4 (2018 TVs) runs Chrome 53.
 */
(function () {
  'use strict';

  var KEY = {
    LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, OK: 13, BACK: 461, ESC: 27,
    CH_UP: 33, CH_DOWN: 34, STOP: 413, FF: 417, RW: 412
  };
  var COLUMNS = 4;
  var SWITCH_GAP_MS = 1200;     // let the provider count the old stream as closed (one-screen accounts)
  var START_TIMEOUT_MS = 15000;
  var STALL_MS = 25000;
  var MAX_RECONNECTS = 8;
  var GUIDE_EVERY_MS = 3 * 60 * 1000;

  var TABS = [
    { id: 'sport', label: 'Sport' },
    { id: 'israel', label: 'Israeli TV' },
    { id: 'all', label: 'All' },
    { id: 'settings', label: 'Settings' }
  ];

  function $(id) { return document.getElementById(id); }
  function store(key, value) {
    try {
      if (value === undefined) return JSON.parse(localStorage.getItem(key));
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) { return null; }
  }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function clock(seconds) { var d = new Date(seconds * 1000); return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function enc(s) { return encodeURIComponent(s); }

  var state = {
    login: store('login'),                  // { server, user, pass, folders }
    quality: store('quality') || 'fullHd',  // 'fullHd' or 'best'
    channels: store('channels') || [],      // the account's live channels in the chosen folders
    tab: store('tab') || 0,
    zone: 'grid',                           // 'tabs' or 'grid'
    tabFocus: 0,
    index: 0,                               // focused tile
    guide: {},                              // channel id → "what's on now"
    screen: 'home'
  };

  // ---------- the account ----------

  function base(server) {
    var s = server.trim();
    if (!/^https?:\/\//i.test(s)) s = 'http://' + s;
    return s.replace(/\/+$/, '') + '/';
  }

  function api(login, action, extra) {
    return base(login.server) + 'player_api.php?username=' + enc(login.user) + '&password=' + enc(login.pass) +
      (action ? '&action=' + action : '') + (extra || '');
  }

  function getJson(url) {
    return fetch(url).then(function (r) {
      if (!r.ok) throw new Error('the server answered HTTP ' + r.status);
      return r.json();
    });
  }

  /** The account's live channels in the chosen folders. Many servers only speak http: an https address falls back to it. */
  function loadChannels(login) {
    return getJson(api(login)).then(function (info) {
      var user = info && info.user_info;
      if (!user || String(user.auth) !== '1') throw new Error('wrong username or password');
      if (user.status && user.status !== 'Active') throw new Error('the account is ' + user.status.toLowerCase());
      return Promise.all([getJson(api(login, 'get_live_categories')), getJson(api(login, 'get_live_streams'))]);
    }).then(function (parts) {
      var cats = {};
      parts[0].forEach(function (c) { cats[c.category_id] = c.category_name; });
      var terms = (login.folders || '').split(',').map(function (t) { return t.trim().toLowerCase(); }).filter(function (t) { return t; });
      return parts[1].map(function (o) {
        return { id: o.stream_id, name: o.name || ('Channel ' + o.stream_id), group: cats[o.category_id] || '', logo: o.stream_icon || '' };
      }).filter(function (c) {
        return !terms.length || terms.some(function (t) { return c.group.toLowerCase().indexOf(t) >= 0; });
      });
    }).catch(function (e) {
      if (/^https:/i.test(base(login.server))) {
        var plain = {}; for (var k in login) plain[k] = login[k];
        plain.server = base(login.server).replace(/^https:/i, 'http:');
        return loadChannels(plain).then(function (list) { login.server = plain.server; return list; });
      }
      throw e;
    });
  }

  function streamUrl(channel, ext) {
    var l = state.login;
    return base(l.server) + 'live/' + enc(l.user) + '/' + enc(l.pass) + '/' + channel.id + '.' + ext;
  }

  // ---------- what each tab shows ----------

  function cap() { return state.quality === 'best' ? Lists.CAP.best : Lists.CAP.fullHd; }

  function tabChannels(tabId) {
    if (tabId === 'sport') return Lists.pick(state.channels, Lists.SPORT_TEXT, cap());
    if (tabId === 'israel') return Lists.pick(state.channels, Lists.ISRAEL_TEXT, cap());
    return state.channels.map(function (c) { return { id: c.id, name: Lists.displayName(c.name), quality: c.group, logo: c.logo }; });
  }

  var visible = [];

  function render() {
    var tabs = $('tabs');
    tabs.innerHTML = '';
    TABS.forEach(function (t, i) {
      var el = document.createElement('span');
      el.className = 'tab' + (i === state.tab ? ' selected' : '') + (state.zone === 'tabs' && i === state.tabFocus ? ' focused' : '');
      el.textContent = t.label;
      el.onclick = function () { state.tabFocus = i; openTab(i); };
      tabs.appendChild(el);
    });

    visible = tabChannels(TABS[state.tab].id);
    var grid = $('grid');
    grid.innerHTML = '';
    var msg = $('message');
    if (!state.login) {
      showMessage('Welcome! Open Settings (press Up, then go to Settings) to sign in to your IPTV account.');
    } else if (!visible.length) {
      showMessage(state.loading ? 'Loading your channels…' : 'No channels here. Check the folder name in Settings.');
    } else {
      msg.className = 'message hidden';
    }
    state.index = Math.min(state.index, Math.max(0, visible.length - 1));
    visible.forEach(function (c, i) {
      var tile = document.createElement('div');
      tile.className = 'tile' + (state.zone === 'grid' && i === state.index ? ' focused' : '');
      var logo = document.createElement('div');
      logo.className = 'logo';
      var num = document.createElement('span');
      num.className = 'num';
      num.textContent = pad(i + 1);
      if (c.logo) {
        var img = document.createElement('img');
        img.src = c.logo;
        img.onerror = function () { logo.innerHTML = ''; logo.appendChild(num); };
        logo.appendChild(img);
      } else {
        logo.appendChild(num);
      }
      var name = document.createElement('div');
      name.className = 'name';
      name.textContent = c.name;
      var now = document.createElement('div');
      now.className = 'now';
      now.textContent = state.guide[c.id] || c.quality || '';
      tile.appendChild(logo); tile.appendChild(name); tile.appendChild(now);
      tile.onclick = function () { play(visible, i); };
      tile.onmouseover = function () { if (state.zone !== 'grid' || state.index !== i) { state.zone = 'grid'; state.index = i; refreshFocus(); } };
      grid.appendChild(tile);
    });
    refreshFocus();
  }

  function showMessage(text) {
    var msg = $('message');
    msg.textContent = text;
    msg.className = 'message';
  }

  /** Moves the white highlight without rebuilding the screen, and keeps the focused row in view. */
  function refreshFocus() {
    var tabs = $('tabs').children;
    for (var t = 0; t < tabs.length; t++) {
      tabs[t].className = 'tab' + (t === state.tab ? ' selected' : '') + (state.zone === 'tabs' && t === state.tabFocus ? ' focused' : '');
    }
    var tiles = $('grid').children;
    for (var i = 0; i < tiles.length; i++) tiles[i].className = 'tile' + (state.zone === 'grid' && i === state.index ? ' focused' : '');
    var focused = tiles[state.index];
    if (focused && state.zone === 'grid') {
      var rowHeight = tiles.length > COLUMNS ? tiles[COLUMNS].offsetTop - tiles[0].offsetTop : focused.offsetHeight;
      var row = Math.floor(state.index / COLUMNS);
      $('grid').scrollTop = Math.max(0, row - 1) * rowHeight;
    }
  }

  function openTab(i) {
    if (TABS[i].id === 'settings') { openSettings(); return; }
    state.tab = i;
    state.index = 0;
    state.zone = 'grid';   // straight to the channels of the tab just chosen
    store('tab', i);
    render();
    refreshGuide();
  }

  // ---------- what's on now ----------

  var guideTimer = null;

  function refreshGuide() {
    clearTimeout(guideTimer);
    guideTimer = setTimeout(refreshGuide, GUIDE_EVERY_MS);
    var id = TABS[state.tab].id;
    if (!state.login || id === 'all') return;   // only the short lists: a guide for hundreds of channels is too slow
    var list = visible.slice();
    var now = Date.now() / 1000;
    list.forEach(function (c) {
      getJson(api(state.login, 'get_short_epg', '&stream_id=' + c.id + '&limit=3')).then(function (data) {
        var label = Lists.nowLabel((data && data.epg_listings) || [], now, window.atob, clock);
        if (label && state.guide[c.id] !== label) {
          state.guide[c.id] = label;
          var i = visible.indexOf(c);
          var tile = $('grid').children[i];
          if (tile) tile.querySelector('.now').textContent = label;
        }
      }).catch(function () { /* no guide for this channel */ });
    });
  }

  // ---------- settings ----------

  var settingsItems = ['f-server', 'f-user', 'f-pass', 'f-folders', 'b-quality', 'b-save', 'b-cancel', 'b-signout'];
  var settingsFocus = 0;

  function openSettings() {
    var l = state.login || {};
    $('f-server').value = l.server || '';
    $('f-user').value = l.user || '';
    $('f-pass').value = l.pass || '';
    $('f-folders').value = l.folders === undefined ? 'Hebrew' : l.folders;
    $('f-error').textContent = '';
    showQuality();
    settingsFocus = state.login ? 5 : 0;
    show('settings');
    refreshSettingsFocus();
  }

  function showQuality() {
    $('b-quality').textContent = 'Quality on this TV: ' + (state.quality === 'best' ? 'Best (4K)' : 'Up to Full HD');
  }

  function refreshSettingsFocus() {
    settingsItems.forEach(function (id, i) {
      var el = $(id);
      el.className = el.className.replace(/\s*focused/g, '') + (i === settingsFocus ? ' focused' : '');
    });
  }

  function activateSetting() {
    var id = settingsItems[settingsFocus];
    var el = $(id);
    if (el.tagName === 'INPUT') { el.focus(); return; }   // opens the TV's keyboard
    if (id === 'b-quality') {
      state.quality = state.quality === 'best' ? 'fullHd' : 'best';
      store('quality', state.quality);
      showQuality();
    } else if (id === 'b-save') {
      signIn();
    } else if (id === 'b-cancel') {
      closeSettings();
    } else if (id === 'b-signout') {
      state.login = null; state.channels = []; state.guide = {};
      store('login', null); store('channels', []);
      closeSettings();
    }
  }

  function signIn() {
    var login = {
      server: $('f-server').value.trim(), user: $('f-user').value.trim(), pass: $('f-pass').value,
      folders: $('f-folders').value.trim()
    };
    if (!login.server || !login.user || !login.pass) { $('f-error').textContent = 'Fill in the server address, username and password.'; return; }
    $('f-error').textContent = 'Signing in…';
    loadChannels(login).then(function (list) {
      state.login = login; state.channels = list; state.guide = {};
      store('login', login); store('channels', list);
      state.tab = 0; store('tab', 0);
      closeSettings();
    }).catch(function (e) {
      $('f-error').textContent = 'Couldn\'t sign in: ' + (e.message || 'no answer from the server') + '.';
    });
  }

  function closeSettings() {
    show('home');
    state.zone = 'grid';
    render();
    refreshGuide();
  }

  // ---------- the player ----------

  var player = { list: [], index: 0, ext: 'm3u8', reconnects: 0, timer: null, stallTimer: null, bannerTimer: null, typed: '', typedTimer: null, open: false };
  var video = $('video');

  function play(list, i) {
    if (!list.length) return;
    var wasPlaying = player.open;
    player.list = list;
    player.open = true;
    show('player');
    switchTo(i, wasPlaying);
  }

  /** Closes the current stream first, and opens only the channel you stop on when CH+ is pressed several times. */
  function switchTo(i, wait) {
    player.index = (i + player.list.length) % player.list.length;
    player.ext = 'm3u8';
    player.reconnects = 0;
    showBanner();
    stopVideo();
    clearTimeout(player.timer);
    setStatus(wait ? 'Switching…' : 'Connecting…');
    player.timer = setTimeout(load, wait ? SWITCH_GAP_MS : 0);
  }

  function load() {
    var c = player.list[player.index];
    setStatus('Connecting…');
    video.src = streamUrl(c, player.ext);
    video.load();
    var p = video.play();
    if (p && p.catch) p.catch(function () { /* errors arrive through the error event */ });
    clearTimeout(player.timer);
    player.timer = setTimeout(function () { onProblem('no picture after ' + START_TIMEOUT_MS / 1000 + ' s'); }, START_TIMEOUT_MS);
  }

  function stopVideo() {
    clearTimeout(player.stallTimer);
    video.removeAttribute('src');
    try { video.load(); } catch (e) { /* nothing was loaded */ }
  }

  /** The stream failed, ended or froze: try the other format once, then reconnect with growing pauses. */
  function onProblem(reason) {
    if (!player.open) return;
    clearTimeout(player.timer);
    clearTimeout(player.stallTimer);
    if (player.ext === 'm3u8' && player.reconnects === 0) {
      player.ext = 'ts';
      stopVideo();
      player.timer = setTimeout(load, 500);
      return;
    }
    if (player.reconnects >= MAX_RECONNECTS) {
      stopVideo();
      setStatus('This channel isn\'t playing (' + reason + '). Press OK to try again, or CH +/− for another channel. ' +
        'If the phone is playing, your one-screen account may be in use there.', true);
      return;
    }
    player.reconnects++;
    stopVideo();
    setStatus('Connection lost. Reconnecting…');
    player.timer = setTimeout(load, Math.min(1000 * Math.pow(2, player.reconnects - 1), 8000));
  }

  video.addEventListener('playing', function () {
    clearTimeout(player.timer);
    clearTimeout(player.stallTimer);
    player.reconnects = 0;
    setStatus(null);
  });
  video.addEventListener('waiting', function () {
    clearTimeout(player.stallTimer);
    player.stallTimer = setTimeout(function () { onProblem('frozen'); }, STALL_MS);
  });
  video.addEventListener('error', function () { if (video.getAttribute('src')) onProblem('the stream stopped'); });
  video.addEventListener('ended', function () { onProblem('the stream ended'); });

  function closePlayer() {
    player.open = false;
    clearTimeout(player.timer);
    stopVideo();
    setStatus(null);
    show('home');
    var i = visible.indexOf(player.list[player.index]);
    if (i >= 0) { state.index = i; state.zone = 'grid'; }
    refreshFocus();
  }

  function setStatus(text, isError) {
    var el = $('status');
    if (!text) { el.className = 'status hidden'; return; }
    el.textContent = text;
    el.className = 'status' + (isError ? ' error' : '');
  }

  function showBanner(text) {
    var c = player.list[player.index];
    var el = $('banner');
    el.innerHTML = '';
    var title = document.createElement('div');
    title.textContent = text || ((player.index + 1) + ' / ' + player.list.length + '  ·  ' + c.name);
    el.appendChild(title);
    var sub = state.guide[c.id] || c.quality;
    if (!text && sub) {
      var s = document.createElement('div');
      s.className = 'sub';
      s.textContent = sub;
      el.appendChild(s);
    }
    el.className = 'banner';
    clearTimeout(player.bannerTimer);
    player.bannerTimer = setTimeout(function () { el.className = 'banner hidden'; }, 4000);
  }

  /** Number keys: 3 goes to channel 3; 1 then 2 to channel 12 (after a moment, or at once with OK). */
  function typeDigit(d) {
    player.typed = (player.typed.length >= 3 ? '' : player.typed) + d;
    showBanner('Channel ' + player.typed + '_');
    clearTimeout(player.typedTimer);
    player.typedTimer = setTimeout(goToTyped, 1500);
  }

  function goToTyped() {
    clearTimeout(player.typedTimer);
    var n = parseInt(player.typed, 10);
    player.typed = '';
    if (n >= 1 && n <= player.list.length) { if (n - 1 !== player.index) switchTo(n - 1, true); else showBanner(); }
    else showBanner('No channel ' + n + ' (1–' + player.list.length + ')');
  }

  // ---------- the remote ----------

  function show(screen) {
    state.screen = screen;
    ['home', 'settings', 'player'].forEach(function (s) { $(s).className = 'screen' + (s === screen ? '' : ' hidden'); });
  }

  function digitOf(code) {
    if (code >= 48 && code <= 57) return code - 48;
    if (code >= 96 && code <= 105) return code - 96;   // number pad
    return -1;
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode;
    var handled = true;

    if (state.screen === 'player') {
      var d = digitOf(code);
      if (d >= 0) typeDigit(d);
      else if (code === KEY.UP || code === KEY.CH_UP || code === KEY.FF) switchTo(player.index + 1, true);
      else if (code === KEY.DOWN || code === KEY.CH_DOWN || code === KEY.RW) switchTo(player.index - 1, true);
      else if (code === KEY.OK) {
        if (player.typed) goToTyped();
        else if (/error/.test($('status').className)) switchTo(player.index, false);
        else showBanner();
      }
      else if (code === KEY.LEFT || code === KEY.RIGHT) showBanner();
      else if (code === KEY.BACK || code === KEY.ESC || code === KEY.STOP) closePlayer();
      else handled = false;

    } else if (state.screen === 'settings') {
      var active = document.activeElement;
      if (active && active.tagName === 'INPUT') {
        // The TV keyboard is open: OK or Back closes it and moves on
        if (code === KEY.OK || code === KEY.BACK || code === KEY.ESC) {
          active.blur();
          if (code === KEY.OK && settingsFocus < 3) settingsFocus++;
          refreshSettingsFocus();
        } else handled = false;
      } else if (code === KEY.DOWN || code === KEY.RIGHT) { settingsFocus = Math.min(settingsFocus + 1, settingsItems.length - 1); refreshSettingsFocus(); }
      else if (code === KEY.UP || code === KEY.LEFT) { settingsFocus = Math.max(settingsFocus - 1, 0); refreshSettingsFocus(); }
      else if (code === KEY.OK) activateSetting();
      else if (code === KEY.BACK || code === KEY.ESC) closeSettings();
      else handled = false;

    } else {
      var n = digitOf(code);
      if (n > 0 && n <= visible.length) play(visible, n - 1);
      else if (state.zone === 'tabs') {
        if (code === KEY.LEFT) state.tabFocus = Math.max(0, state.tabFocus - 1);
        else if (code === KEY.RIGHT) state.tabFocus = Math.min(TABS.length - 1, state.tabFocus + 1);
        else if (code === KEY.DOWN && visible.length) state.zone = 'grid';
        else if (code === KEY.OK) { openTab(state.tabFocus); return e.preventDefault(); }
        else if (code === KEY.BACK || code === KEY.ESC) window.close();   // Back on the tabs leaves the app
        else handled = false;
        refreshFocus();
      } else {
        var i = state.index;
        if (code === KEY.RIGHT) i = Math.min(i + 1, visible.length - 1);
        else if (code === KEY.LEFT) i = Math.max(i - 1, 0);
        else if (code === KEY.DOWN) i = Math.min(i + COLUMNS, visible.length - 1);
        else if (code === KEY.UP) {
          if (i < COLUMNS) { state.zone = 'tabs'; state.tabFocus = state.tab; refreshFocus(); return e.preventDefault(); }
          i -= COLUMNS;
        }
        else if (code === KEY.OK) { if (visible.length) play(visible, i); return e.preventDefault(); }
        else if (code === KEY.BACK || code === KEY.ESC) { state.zone = 'tabs'; state.tabFocus = state.tab; refreshFocus(); return e.preventDefault(); }
        else handled = false;
        if (i !== state.index) { state.index = i; refreshFocus(); }
      }
    }
    if (handled) e.preventDefault();
  });

  // ---------- start ----------

  function tick() { var d = new Date(); $('clock').textContent = pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  tick();
  setInterval(tick, 15000);

  if (state.tab >= TABS.length - 1) state.tab = 0;
  render();
  if (!state.login) {
    state.zone = 'tabs'; state.tabFocus = TABS.length - 1; refreshFocus();
  } else {
    // Show the saved list at once, then refresh it from the provider
    refreshGuide();
    state.loading = !state.channels.length;
    if (state.loading) render();
    loadChannels(state.login).then(function (list) {
      state.loading = false;
      state.channels = list;
      store('channels', list);
      if (state.screen === 'home') render();
      refreshGuide();
    }).catch(function (e) {
      state.loading = false;
      if (!state.channels.length) showMessage('Couldn\'t load your channels: ' + (e.message || 'no answer from the server') + '. Check Settings and the internet connection.');
    });
  }
})();
