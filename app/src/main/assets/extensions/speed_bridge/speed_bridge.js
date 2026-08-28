/**
 * Speed Controller, Network Quality Enforcer & Video Gestures Bridge
 *
 * Exclusively active on YouTube Video (disabled on YouTube Music):
 * 1. Playback Speed Engine with Audio Pitch Preservation.
 * 2. Swipe UP on video player -> Landscape Fullscreen.
 * 3. Swipe DOWN on fullscreen video -> Portrait Normal Mode.
 * 4. Double-Tap Left/Right -> Seek -10s / +10s with visual indicator.
 * 5. Fullscreen Swipe Left Side -> Screen Brightness Overlay.
 * 6. Fullscreen Swipe Right Side -> Audio Volume Controller with HUD.
 * 7. Network-Aware Default Quality: 720p on Wi-Fi, 480p on Mobile Data (freely changeable by user).
 */
(function () {
  'use strict';

  const isMusicApp = window.location.hostname === 'music.youtube.com';
  let currentTargetSpeed = 1.0;

  /* ==========================================================================
     1. Video Speed Control Engine
     ========================================================================== */
  function applySpeedToVideo(video, speed) {
    if (!video) return;
    try {
      video.playbackRate = speed;
      video.defaultPlaybackRate = speed;
      video.preservesPitch = true;
      video.mozPreservesPitch = true;
      video.webkitPreservesPitch = true;
    } catch (e) {
      console.warn('[GeckoFlux] Error applying playback rate', e);
    }
  }

  function setAllVideosSpeed(speed) {
    currentTargetSpeed = speed;
    const videos = document.querySelectorAll('video');
    videos.forEach((v) => applySpeedToVideo(v, speed));
  }

  /* ==========================================================================
     2. Network-Aware Default Quality Enforcer (720p on Wi-Fi, 480p on Cellular)
     ========================================================================== */
  function applyNetworkDefaultQuality() {
    try {
      const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
      const isCellular = conn && (conn.type === 'cellular' || conn.effectiveType === '2g' || conn.effectiveType === '3g');
      const targetQuality = isCellular ? 'large' : 'hd720'; // 480p on cellular, 720p on Wi-Fi

      const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
      if (player && typeof player.setPlaybackQualityRange === 'function') {
        player.setPlaybackQualityRange(targetQuality, targetQuality);
      }
    } catch (e) {
      // Ignore if player API is not ready
    }
  }

  const observer = new MutationObserver((mutations) => {
    if (currentTargetSpeed !== 1.0) {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeName === 'VIDEO') {
            applySpeedToVideo(node, currentTargetSpeed);
          } else if (node.querySelectorAll) {
            const vids = node.querySelectorAll('video');
            vids.forEach((v) => applySpeedToVideo(v, currentTargetSpeed));
          }
        }
      }
    }
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  document.addEventListener(
    'play',
    (e) => {
      if (e.target && e.target.nodeName === 'VIDEO') {
        if (currentTargetSpeed !== 1.0) {
          applySpeedToVideo(e.target, currentTargetSpeed);
        }
        applyNetworkDefaultQuality();
      }
    },
    true
  );

  /* ==========================================================================
     3. Gestures Engine: Fullscreen Swipe, Seek, Brightness & Volume (YouTube Video Only)
     ========================================================================== */
  if (!isMusicApp) {
    let touchStartX = 0;
    let touchStartY = 0;
    let touchStartTime = 0;
    let lastTapTime = 0;
    let lastTapX = 0;
    let isVerticalDrag = false;
    let initialVolume = 1.0;
    let initialBrightness = 1.0;
    let currentBrightness = 1.0;

    function isFullscreenActive() {
      return !!(
        document.fullscreenElement ||
        document.webkitFullscreenElement ||
        document.mozFullScreenElement ||
        document.msFullscreenElement
      );
    }

    function isPlayerTarget(target) {
      if (!target) return false;
      return !!target.closest(
        '#player, .player-container, .html5-video-player, ytm-player, video, .video-stream, .player-controls-background'
      );
    }

    // Floating On-Screen HUD for Volume / Brightness / Seek
    function showHud(text, isLeft) {
      let hud = document.getElementById('geckoflux-touch-hud');
      if (!hud) {
        hud = document.createElement('div');
        hud.id = 'geckoflux-touch-hud';
        document.body.appendChild(hud);
      }
      hud.textContent = text;
      hud.style.cssText = `
        position: fixed;
        top: 45%;
        ${isLeft ? 'left: 12%;' : 'right: 12%;'}
        transform: translateY(-50%);
        background: rgba(15, 15, 15, 0.85);
        border: 1px solid rgba(255, 255, 255, 0.2);
        color: #FFFFFF;
        font-size: 15px;
        font-weight: 600;
        padding: 10px 18px;
        border-radius: 20px;
        pointer-events: none;
        z-index: 9999999;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.6);
        transition: opacity 0.3s ease;
        opacity: 1;
      `;

      clearTimeout(hud._timer);
      hud._timer = setTimeout(() => {
        hud.style.opacity = '0';
        setTimeout(() => hud.remove(), 300);
      }, 700);
    }

    // Brightness overlay filter on video element
    function applyBrightnessOverlay(level) {
      const video = document.querySelector('video');
      if (video) {
        // Map 0.2 - 1.5 brightness level
        video.style.filter = `brightness(${level})`;
      }
    }

    document.addEventListener(
      'touchstart',
      (e) => {
        if (e.touches.length === 1 && (isPlayerTarget(e.target) || isFullscreenActive())) {
          touchStartX = e.touches[0].clientX;
          touchStartY = e.touches[0].clientY;
          touchStartTime = Date.now();
          isVerticalDrag = false;

          const video = document.querySelector('video');
          if (video) {
            initialVolume = video.volume;
          }
          initialBrightness = currentBrightness;
        }
      },
      { passive: true }
    );

    document.addEventListener(
      'touchmove',
      (e) => {
        if (e.touches.length === 1 && isFullscreenActive()) {
          const curX = e.touches[0].clientX;
          const curY = e.touches[0].clientY;
          const deltaY = touchStartY - curY; // Positive = swiping up
          const deltaX = Math.abs(curX - touchStartX);

          if (Math.abs(deltaY) > 20 && Math.abs(deltaY) > deltaX * 1.5) {
            isVerticalDrag = true;
            const screenWidth = window.innerWidth;
            const screenHeight = window.innerHeight;
            const step = deltaY / (screenHeight * 0.6);

            if (touchStartX < screenWidth * 0.4) {
              // Left Side -> Adjust Brightness
              currentBrightness = Math.min(1.5, Math.max(0.2, initialBrightness + step));
              applyBrightnessOverlay(currentBrightness);
              const percent = Math.round((currentBrightness / 1.5) * 100);
              showHud(`☀️ Brightness: ${percent}%`, true);
            } else if (touchStartX > screenWidth * 0.6) {
              // Right Side -> Adjust Volume
              const video = document.querySelector('video');
              if (video) {
                const newVol = Math.min(1.0, Math.max(0.0, initialVolume + step));
                video.volume = newVol;
                const percent = Math.round(newVol * 100);
                showHud(`🔊 Volume: ${percent}%`, false);
              }
            }
          }
        }
      },
      { passive: true }
    );

    document.addEventListener(
      'touchend',
      (e) => {
        if (e.changedTouches.length === 1 && (isPlayerTarget(e.target) || isFullscreenActive())) {
          if (isVerticalDrag) return;

          const endX = e.changedTouches[0].clientX;
          const endY = e.changedTouches[0].clientY;
          const deltaX = endX - touchStartX;
          const deltaY = endY - touchStartY;
          const timeDiff = Date.now() - touchStartTime;
          const now = Date.now();

          // A. Double-Tap to Seek (+/- 10s)
          if (timeDiff < 250 && Math.abs(deltaX) < 15 && Math.abs(deltaY) < 15) {
            if (now - lastTapTime < 300 && Math.abs(endX - lastTapX) < 40) {
              const video = document.querySelector('video');
              if (video) {
                const screenWidth = window.innerWidth;
                if (endX > screenWidth * 0.65) {
                  video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
                  showHud('+10s ⏩', false);
                } else if (endX < screenWidth * 0.35) {
                  video.currentTime = Math.max(0, video.currentTime - 10);
                  showHud('⏪ -10s', true);
                }
              }
            }
            lastTapTime = now;
            lastTapX = endX;
            return;
          }

          // B. Swipe UP (Enter Fullscreen) / Swipe DOWN (Exit Fullscreen)
          const isVerticalSwipe = Math.abs(deltaY) > 50 && Math.abs(deltaY) > Math.abs(deltaX) * 1.3;

          if (timeDiff < 500 && isVerticalSwipe) {
            if (deltaY < -50 && !isFullscreenActive()) {
              triggerFullscreenEnter();
            } else if (deltaY > 50 && isFullscreenActive()) {
              triggerFullscreenExit();
            }
          }
        }
      },
      { passive: true }
    );

    function triggerFullscreenEnter() {
      const fsButton = document.querySelector(
        '.fullscreen-icon, .ytp-fullscreen-button, button[aria-label*="Full screen"], button[aria-label*="fullscreen"]'
      );
      if (fsButton) {
        fsButton.click();
        return;
      }
      const video = document.querySelector('video');
      if (video) {
        const req =
          video.requestFullscreen ||
          video.webkitRequestFullscreen ||
          video.mozRequestFullScreen ||
          video.msRequestFullscreen;
        if (req) req.call(video).catch(() => {});
      }
    }

    function triggerFullscreenExit() {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      } else if (document.mozCancelFullScreen) {
        document.mozCancelFullScreen();
      }
    }
  }

  /* ==========================================================================
     4. Communication Hooks
     ========================================================================== */
  if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
      if (message && message.type === 'SET_SPEED') {
        const speed = parseFloat(message.speed) || 1.0;
        setAllVideosSpeed(speed);
        sendResponse({ status: 'ok', currentSpeed: speed });
      }
      return true;
    });
  }

  window.__setAppVideoSpeed = function (speed) {
    setAllVideosSpeed(speed);
  };
})();
