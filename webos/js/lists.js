/*
 * Channel lists for the TV: the Sport and Israeli TV tabs, picked from the provider's channels the same way as the
 * Android app (SportList.kt): one tile per channel, in the best quality allowed, with the app's own logos.
 * No browser APIs here, so the same file runs in Node for the tests.
 */
(function (root) {
  'use strict';

  // "Label = name at the provider" (several names separated by "|"). A name in quotes is exactly that channel.
  var SPORT_TEXT = [
    'Sport 1 = YES SPORT 1',
    'Sport 2 = YES SPORT 2',
    'Sport 3 = YES SPORT 3',
    'Sport 4 = YES SPORT 4',
    'Sport 5 = "Sport 5 FHD"',
    'Sport 5+ = Sport 5+ | Sport 5 Plus',
    'Sport 5 Live = Sport 5+ Live',
    'Sport 5 Stars = Sport 5+ Stars',
    'Sport 5 Gold = Sport 5 Gold | Sport 5+ Gold',
    'Sport 5+ Max = Sport 5+ Max',
    'Sport 6 = YES SPORT 6',
    'ONE 1',
    'ONE 2'
  ].join('\n');

  var ISRAEL_TEXT = [
    'Channel 11 · Kan = Kan 11 | Kan11',
    'Channel 12 · Keshet = Channel 12',
    'Channel 13 · Reshet = Channel 13',
    'Channel 14 = Channel 14',
    'Channel 15 = Channel 15'
  ].join('\n');

  // Highest quality score a tile may pick: Full HD on the TV (4K takes minutes to start on these streams)
  var CAP = { best: Infinity, fullHd: 4.9, hd: 3.9 };

  var LOGOS = {
    'SPORT 1': 'sport1', 'SPORT 2': 'sport2', 'SPORT 3': 'sport3', 'SPORT 4': 'sport4',
    'SPORT 5': 'sport5', 'SPORT 6': 'sport6',
    'SPORT 5+': 'sport5plus', 'SPORT 5 PLUS': 'sport5plus',
    'SPORT 5 LIVE': 'sport5live', 'SPORT 5+ LIVE': 'sport5live',
    'SPORT 5 STARS': 'sport5stars', 'SPORT 5+ STARS': 'sport5stars',
    'ONE 1': 'one1', 'ONE 2': 'one2',
    'KAN 11': 'kan11', 'KAN11': 'kan11', 'CHANNEL 12': 'channel12', 'CHANNEL 13': 'channel13', 'CHANNEL 14': 'channel14'
  };

  // Words that only describe picture quality; anything else after the name means a different channel
  var QUALITY_WORDS = ['4K', 'UHD', 'HDR', 'FHD', 'HD', 'SD', 'P50', 'P60', 'HEVC', 'H265', '[MULTI-SUB]'];

  function words(s) { return s.trim().split(/\s+/).filter(function (w) { return w.length > 0; }); }
  function has(list, w) { return list.indexOf(w) >= 0; }

  /** "IL: YES SPORT 1 FHD" → "YES SPORT 1 FHD"; names without a two-letter country prefix stay as they are. */
  function displayName(name) {
    var m = /^[A-Z]{2}\s*[:|]\s*(.+)$/.exec(name.trim());
    return m ? m[1].trim() : name.trim();
  }

  function qualityOf(ws) {
    var fourK = has(ws, '4K') || has(ws, 'UHD');
    var fps50 = has(ws, 'P50') || has(ws, 'P60');
    var score = fourK ? 5 + (has(ws, 'HDR') ? 0.5 : 0)
      : has(ws, 'FHD') ? 4 + (fps50 ? 0.5 : 0)
      : has(ws, 'HD') ? 3
      : has(ws, 'SD') ? 1
      : 2;   // no quality in the name
    var label = fourK && has(ws, 'HDR') ? '4K HDR'
      : fourK ? '4K'
      : has(ws, 'FHD') && fps50 ? 'Full HD 50 fps'
      : has(ws, 'FHD') ? 'Full HD'
      : has(ws, 'HD') ? 'HD'
      : has(ws, 'SD') ? 'SD'
      : '';
    return { score: score, label: label };
  }

  /** The quality of the channel if it is the channel [pattern] (in any quality), otherwise null. */
  function qualityIfMatches(channel, pattern) {
    var name = words(displayName(channel.name).toUpperCase()).join(' ');
    var p = pattern.trim();
    if (p.length > 2 && p.charAt(0) === '"' && p.charAt(p.length - 1) === '"') {   // "Sport 5 FHD": that channel only
      var exact = words(p.substring(1, p.length - 1).toUpperCase());
      return name === exact.join(' ') ? qualityOf(exact.filter(function (w) { return has(QUALITY_WORDS, w); })) : null;
    }
    var want = p.toUpperCase();
    if (name.indexOf(want) !== 0) return null;
    var rest = name.substring(want.length);
    if (rest.length > 0 && rest.charAt(0) !== ' ') return null;   // "SPORT 1" must not match "SPORT 10"
    var ws = words(rest);
    for (var i = 0; i < ws.length; i++) if (!has(QUALITY_WORDS, ws[i])) return null;   // "SPORT 5 GOLD" is another channel
    return qualityOf(ws);
  }

  function parse(text) {
    return text.split('\n').map(function (l) { return l.trim(); })
      .filter(function (l) { return l && l.charAt(0) !== '#'; })
      .map(function (line) {
        var eq = line.indexOf('=');
        var label = (eq >= 0 ? line.substring(0, eq) : '').trim();
        var patterns = (eq >= 0 ? line.substring(eq + 1) : line).split('|')
          .map(function (s) { return s.trim(); }).filter(function (s) { return s; });
        return { label: label || patterns[0], patterns: patterns };
      })
      .filter(function (e) { return e.patterns.length > 0; });
  }

  function logoFor(entry) {
    var keys = [entry.label].concat(entry.patterns);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i].replace(/"/g, '').trim().toUpperCase().replace(/^YES /, '').trim();
      if (LOGOS[k]) return 'img/logos/' + LOGOS[k] + '.png';
    }
    return null;
  }

  /** One channel per line of the list, in that order, each in the best quality up to [cap]. */
  function pick(channels, text, cap) {
    var max = cap === undefined ? Infinity : cap;
    var out = [];
    parse(text).forEach(function (entry) {
      var options = [];
      channels.forEach(function (c) {
        for (var i = 0; i < entry.patterns.length; i++) {
          var q = qualityIfMatches(c, entry.patterns[i]);
          if (q) { options.push({ c: c, q: q }); break; }
        }
      });
      if (!options.length) return;
      var allowed = options.filter(function (o) { return o.q.score <= max; });
      var best = allowed.length
        ? allowed.reduce(function (a, b) { return b.q.score > a.q.score ? b : a; })
        : options.reduce(function (a, b) { return b.q.score < a.q.score ? b : a; });   // nothing within the limit: the lowest there is
      out.push({
        id: best.c.id, name: entry.label, quality: best.q.label, group: best.c.group,
        // Only the app's own logos: some provider logos are broken pictures ("image not found"); otherwise the number shows
        logo: logoFor(entry) || '', epg: best.c.epg
      });
    });
    return out;
  }

  /** Program titles come base64-encoded (UTF-8 text); some servers send plain text. */
  function decodeTitle(s, atobFn) {
    if (!s) return '';
    try {
      var bin = atobFn(s);
      var bytes = [];
      for (var i = 0; i < bin.length; i++) bytes.push('%' + ('0' + bin.charCodeAt(i).toString(16)).slice(-2));
      var text = decodeURIComponent(bytes.join(''));
      return text.trim() || s;
    } catch (e) {
      return s;
    }
  }

  /** "Maccabi - Hapoel · until 21:00", or '' when the guide has nothing on right now. */
  function nowLabel(listings, nowSeconds, atobFn, clock) {
    for (var i = 0; i < listings.length; i++) {
      var o = listings[i];
      var start = parseInt(o.start_timestamp, 10), stop = parseInt(o.stop_timestamp, 10);
      if (nowSeconds >= start && nowSeconds < stop) return decodeTitle(o.title, atobFn) + ' · until ' + clock(stop);
    }
    return '';
  }

  var api = {
    SPORT_TEXT: SPORT_TEXT, ISRAEL_TEXT: ISRAEL_TEXT, CAP: CAP,
    displayName: displayName, pick: pick, decodeTitle: decodeTitle, nowLabel: nowLabel, qualityOf: qualityOf
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Lists = api;
})(this);
