(function (root) {
  'use strict';
  // Local previews only: there is no recorder, upload, AI vision, or remote control.
  root.DuaMediaSession = function (options) {
    options = options || {};
    var nav = options.navigator || root.navigator || {};
    var preview = options.preview || null;
    var state = { status: 'idle', mode: null, error: '' };
    var stream = null;
    var generation = 0;
    var destroyed = false;
    function publish(status, mode, error) {
      state = { status: status, mode: mode || null, error: error || '' };
      if (typeof options.onState === 'function') options.onState(Object.assign({}, state));
    }
    function stopTracks(value) { if (value && value.getTracks) value.getTracks().forEach(function (track) { track.stop(); }); }
    function stop() {
      generation++;
      var previous = stream;
      stream = null;
      if (preview) preview.srcObject = null;
      stopTracks(previous);
      publish('idle');
    }
    function interrupt() {
      if (root.speechSynthesis && root.speechSynthesis.cancel) root.speechSynthesis.cancel();
      if (typeof options.onInterrupt === 'function') options.onInterrupt();
    }
    function errorMessage(error) {
      if (error && (error.name === 'NotAllowedError' || error.name === 'SecurityError')) return 'Permission was denied or capture was cancelled. You can retry.';
      if (error && error.name === 'NotFoundError') return 'No camera was found on this device.';
      if (error && error.name === 'NotReadableError') return 'The camera or screen is unavailable. Close other capture apps and retry.';
      return 'Preview could not start on this browser.';
    }
    async function start(mode) {
      if (destroyed) throw Error('This preview session has closed.');
      if (nav.userActivation && !nav.userActivation.isActive) { stop(); publish('error', mode, 'Start preview by tapping its button.'); return false; }
      var devices = nav.mediaDevices;
      var supported = devices && typeof devices[mode === 'camera' ? 'getUserMedia' : 'getDisplayMedia'] === 'function';
      stop();
      if (!supported) { publish('error', mode, mode === 'camera' ? 'Camera preview is not supported. Open this page over HTTPS in a supported browser.' : 'Screen preview is not supported on this browser. Try desktop Chrome or Edge.'); return false; }
      var current = generation;
      publish('requesting', mode);
      try {
        var next = await (mode === 'camera' ? devices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false }) : devices.getDisplayMedia({ video: true, audio: false }));
        if (destroyed || current !== generation) { stopTracks(next); return false; }
        if (!next || !next.getTracks || !next.getVideoTracks || !next.getVideoTracks().length) { stopTracks(next); throw Error('No video track'); }
        stream = next;
        next.getTracks().forEach(function (track) { track.addEventListener('ended', function () { if (stream === next) stop(); }); });
        if (preview) { preview.muted = true; preview.autoplay = true; preview.playsInline = true; preview.srcObject = next; if (preview.play) { var played = preview.play(); if (played && played.catch) played.catch(function () {}); } }
        publish('active', mode);
        return true;
      } catch (error) {
        if (current !== generation || destroyed) return false;
        if (stream) { var failed = stream; stream = null; if (preview) preview.srcObject = null; stopTracks(failed); }
        // Permission failures never publish provider or device details.
        publish('error', mode, errorMessage(error));
        return false;
      }
    }
    function hidden() { if (!root.document || root.document.visibilityState === 'hidden') { stop(); interrupt(); } }
    function pagehide() { stop(); interrupt(); }
    if (root.addEventListener) root.addEventListener('pagehide', pagehide);
    if (root.document && root.document.addEventListener) root.document.addEventListener('visibilitychange', hidden);
    return {
      startCamera: function () { return start('camera'); },
      startScreen: function () { return start('screen'); },
      stop: stop,
      interrupt: interrupt,
      state: function () { return Object.assign({}, state); },
      capabilities: function () { return { camera: !!(nav.mediaDevices && nav.mediaDevices.getUserMedia), screen: !!(nav.mediaDevices && nav.mediaDevices.getDisplayMedia) }; },
      destroy: function () { destroyed = true; stop(); interrupt(); if (root.removeEventListener) root.removeEventListener('pagehide', pagehide); if (root.document && root.document.removeEventListener) root.document.removeEventListener('visibilitychange', hidden); }
    };
  };
})(typeof window !== 'undefined' ? window : globalThis);
