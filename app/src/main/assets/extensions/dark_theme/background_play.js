// GeckoFlux Background Playback Enabler
// Uses Gecko's official wrappedJSObject Xray bridge (matching Mozilla's video-bg-play pattern)
// to prevent YouTube and YouTube Music from pausing when hidden.
(function() {
  'use strict';

  function applyVisibilityOverride(doc) {
    if (!doc) return;
    try {
      Object.defineProperties(doc, {
        'hidden': { value: false, writable: false, configurable: true },
        'visibilityState': { value: 'visible', writable: false, configurable: true },
        'webkitHidden': { value: false, writable: false, configurable: true },
        'webkitVisibilityState': { value: 'visible', writable: false, configurable: true }
      });
    } catch (e) {}
  }

  // 1. Primary mechanism: Override document.wrappedJSObject (Gecko page-world DOM)
  var targetDoc = (typeof document !== 'undefined' && document.wrappedJSObject) 
      ? document.wrappedJSObject 
      : document;
  applyVisibilityOverride(targetDoc);

  // Also apply to Document.prototype in page world if accessible
  var targetWin = (typeof window !== 'undefined' && window.wrappedJSObject)
      ? window.wrappedJSObject
      : window;
  if (targetWin && targetWin.Document && targetWin.Document.prototype) {
    applyVisibilityOverride(targetWin.Document.prototype);
  }

  // Also apply to content script document
  if (typeof document !== 'undefined') {
    applyVisibilityOverride(document);
  }

  // 2. Intercept visibilitychange specifically on window in capture phase
  // Matches Mozilla's video-bg-play: prevents YouTube's visibility pause handler
  window.addEventListener(
    'visibilitychange',
    function(evt) { evt.stopImmediatePropagation(); },
    true
  );

  // 3. User activity ping: Prevents YouTube's idle timeout ("Are you still watching?")
  // Only runs when media playback is actively ongoing to prevent battery drain.
  var activityTimeout = null;

  function pressActivityKey() {
    try {
      var event = new KeyboardEvent('keydown', {
        bubbles: true,
        cancelable: true,
        keyCode: 18,
        which: 18
      });
      (targetDoc || document).dispatchEvent(event);
    } catch (e) {}
  }

  function isAnyVideoPlaying() {
    try {
      var doc = targetDoc || document;
      var videos = doc.querySelectorAll('video');
      for (var i = 0; i < videos.length; i++) {
        var v = videos[i];
        if (!v.paused && !v.ended && v.readyState > 2) {
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function scheduleNextActivityPing() {
    if (activityTimeout) {
      clearTimeout(activityTimeout);
      activityTimeout = null;
    }
    if (!isAnyVideoPlaying()) {
      return;
    }
    var delay = 60000 + Math.floor(Math.random() * 10000 - 5000); // 60s +/- 5s
    activityTimeout = window.setTimeout(function() {
      if (isAnyVideoPlaying()) {
        pressActivityKey();
        scheduleNextActivityPing();
      }
    }, delay);
  }

  window.addEventListener('play', function() {
    scheduleNextActivityPing();
  }, true);

  window.addEventListener('pause', function() {
    if (!isAnyVideoPlaying() && activityTimeout) {
      clearTimeout(activityTimeout);
      activityTimeout = null;
    }
  }, true);

  window.addEventListener('ended', function() {
    if (!isAnyVideoPlaying() && activityTimeout) {
      clearTimeout(activityTimeout);
      activityTimeout = null;
    }
  }, true);
})();
