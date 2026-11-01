/*
 * Plays the provider's live streams on a PC, for the shared app code (webos/js/app.js):
 * HLS (.m3u8) with hls.js, MPEG-TS (.ts) with mpegts.js. A fatal problem is reported back so the app
 * can switch format or reconnect, as it does on the TV.
 */
(function () {
  'use strict';
  var hls = null;
  var ts = null;

  function stop(video) {
    if (hls) { try { hls.destroy(); } catch (e) { /* already gone */ } hls = null; }
    if (ts) {
      try { ts.pause(); ts.unload(); ts.detachMediaElement(); ts.destroy(); } catch (e) { /* already gone */ }
      ts = null;
    }
    video.removeAttribute('src');
    try { video.load(); } catch (e) { /* nothing was loaded */ }
  }

  function load(video, url, ext, onFatal) {
    stop(video);
    if (ext === 'm3u8' && window.Hls && Hls.isSupported()) {
      hls = new Hls({ maxBufferLength: 30, backBufferLength: 30, enableWorker: true });
      hls.on(Hls.Events.ERROR, function (event, data) { if (data.fatal) onFatal(data.details || 'stream error'); });
      hls.loadSource(url);
      hls.attachMedia(video);
    } else if (window.mpegts && mpegts.isSupported()) {
      ts = mpegts.createPlayer({ type: 'mpegts', isLive: true, url: url }, { enableWorker: true, lazyLoad: false });
      ts.on(mpegts.Events.ERROR, function (type, detail) { onFatal(detail || type || 'stream error'); });
      ts.attachMediaElement(video);
      ts.load();
    } else {
      video.src = url;
    }
    var p = video.play();
    if (p && p.catch) p.catch(function () { /* the player's own error events report problems */ });
  }

  window.DesktopPlayer = { load: load, stop: stop };
})();
