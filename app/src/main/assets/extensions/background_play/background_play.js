/**
 * Background Audio & Media Engine for GeckoFlux
 *
 * 1. Spoofs Page Visibility API and intercepts blur/pause events for persistent background audio.
 * 2. Auto-dismisses "Are you still watching?" inactivity timeouts.
 * 3. Enforces viewport lock to prevent accidental pinch-zoom while keeping touch responsive.
 */
(function () {
  'use strict';

  /* ==========================================================================
     1. Page Visibility API Spoofing (Background Audio)
     ========================================================================== */
  try {
    Object.defineProperty(document, 'hidden', {
      get: function () {
        return false;
      },
      configurable: true
    });

    Object.defineProperty(document, 'visibilityState', {
      get: function () {
        return 'visible';
      },
      configurable: true
    });

    Object.defineProperty(document, 'webkitVisibilityState', {
      get: function () {
        return 'visible';
      },
      configurable: true
    });
  } catch (e) {
    console.error('[GeckoFlux] Failed to override visibility properties', e);
  }

  const blockEvent = function (e) {
    e.stopImmediatePropagation();
  };

  document.addEventListener('visibilitychange', blockEvent, true);
  document.addEventListener('webkitvisibilitychange', blockEvent, true);
  window.addEventListener('visibilitychange', blockEvent, true);
  window.addEventListener('blur', blockEvent, true);
  window.addEventListener('pagehide', blockEvent, true);

  /* ==========================================================================
     2. Auto-Dismiss "Are you still watching?" Dialogs
     ========================================================================== */
  function autoDismissInactivityDialogs() {
    const confirmButtons = document.querySelectorAll(
      'yt-confirm-dialog-renderer #confirm-button, ' +
      'paper-dialog #confirm-button, ' +
      'ytm-mealbar-promo-renderer button, ' +
      'yt-formatted-string[aria-label*="Yes"], ' +
      '.yt-spec-button-shape-next--filled'
    );

    confirmButtons.forEach((btn) => {
      const dialog = btn.closest('yt-confirm-dialog-renderer, paper-dialog, ytm-dialog');
      if (dialog) {
        const text = dialog.textContent || '';
        if (text.includes('still watching') || text.includes('paused') || text.includes('Continue')) {
          console.log('[GeckoFlux] Auto-dismissing inactivity dialog');
          btn.click();
          const video = document.querySelector('video');
          if (video && video.paused) {
            video.play().catch(() => {});
          }
        }
      }
    });
  }

  setInterval(autoDismissInactivityDialogs, 4000);

  /* ==========================================================================
     3. Viewport Lock (Prevents Accidental Pinch-Zoom)
     ========================================================================== */
  const enforceViewport = () => {
    if (!document.head) return;
    let meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'viewport';
      document.head.appendChild(meta);
    }
    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
  };

  enforceViewport();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', enforceViewport);
  }
})();
