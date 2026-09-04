(function() {
  try {
    if (!document.cookie.includes('f6=400')) {
      var expiry = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toUTCString();
      document.cookie = "PREF=f6=400; domain=.youtube.com; path=/; expires=" + expiry + "; SameSite=Lax";
    }
    if (document.documentElement) {
      document.documentElement.setAttribute('dark', 'true');
    }
  } catch (e) {
    // Ignore any security restrictions
  }
})();
