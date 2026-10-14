(() => {
  "use strict";
  const config = window.KaroRideConfig || {};
  const dictionaries = window.KaroRideI18n || {};
  const storedLanguage = () => { try { return localStorage.getItem("hellmold_lang"); } catch (_) { return null; } };
  let lang = new URLSearchParams(location.search).get("lang") === "en" ? "en" :
    new URLSearchParams(location.search).get("lang") === "de" ? "de" : storedLanguage() === "en" ? "en" : "de";
  const t = key => dictionaries[lang]?.[key] || dictionaries.de?.[key] || key;
  const publicUrl = value => {
    try { const url = new URL(value); return url.protocol === "https:" ? url.href : null; }
    catch (_) { return null; }
  };
  const supportUrl = publicUrl(config.supportUrl);
  function applyLanguage(next, updateUrl) {
    lang = next === "en" ? "en" : "de";
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach(el => { el.textContent = t(el.dataset.i18n); });
    // HTML comes exclusively from this project's static translation dictionary.
    document.querySelectorAll("[data-i18n-html]").forEach(el => { el.innerHTML = t(el.dataset.i18nHtml); });
    document.querySelectorAll("[data-i18n-aria]").forEach(el => { el.setAttribute("aria-label", t(el.dataset.i18nAria)); });
    document.querySelectorAll("[data-lang]").forEach(el => el.setAttribute("aria-pressed", String(el.dataset.lang === lang)));
    document.title = t(document.body.classList.contains("privacy-page") ? "privacy.meta.title" : "meta.title");
    const description = document.querySelector('meta[name="description"]');
    if (description) description.content = t(document.body.classList.contains("privacy-page") ? "privacy.meta.description" : "meta.description");
    document.querySelectorAll(".coffee-amount").forEach(el => { el.innerHTML = `${lang === "de" ? "3,50" : "3.50"} <span>€</span>`; });
    document.querySelectorAll("[data-release-size]").forEach(el => { el.textContent = config.bytes ? `${(config.bytes / 1_000_000).toLocaleString(lang === "de" ? "de-DE" : "en-GB", {maximumFractionDigits:1})} MB` : ""; });
    if (config.repositoryUrl) document.querySelectorAll("[data-repository-link]").forEach(el => { el.textContent = t("repository.label"); });
    try { localStorage.setItem("hellmold_lang", lang); } catch (_) { /* The language toggle works without storage. */ }
    if (updateUrl) {
      try { const url = new URL(location.href); url.searchParams.set("lang", lang); history.replaceState(null, "", url); }
      catch (_) { /* Direct local-file previews may restrict history changes. */ }
    }
    document.querySelectorAll('a[href^="datenschutz.html"],a[href^="index.html"]').forEach(el => { el.href = `${el.getAttribute("href").split("?")[0]}?lang=${lang}`; });
  }
  document.querySelectorAll("[data-lang]").forEach(el => el.addEventListener("click", () => applyLanguage(el.dataset.lang, true)));
  document.querySelectorAll("[data-support-link]").forEach(el => {
    if (supportUrl) { el.href = supportUrl; el.target = "_blank"; el.rel = "noopener noreferrer"; }
    else { el.setAttribute("aria-disabled", "true"); el.addEventListener("click", event => event.preventDefault()); }
  });
  document.querySelectorAll("[data-support-pending]").forEach(el => { el.hidden = !!supportUrl; });
  document.querySelectorAll("[data-repository-link]").forEach(el => { el.href = publicUrl(config.repositoryUrl) || publicUrl(config.githubProfileUrl) || "https://github.com/242Xtro/"; });
  document.querySelectorAll("[data-release-version]").forEach(el => { el.textContent = config.version || "1.1.1-preview.1"; });
  if (config.apk && !config.apk.includes(":") && !config.apk.startsWith("/")) {
    document.querySelectorAll("[data-apk-link]").forEach(el => { el.href = config.apk; });
    document.querySelectorAll("[data-hash-link]").forEach(el => { el.href = `${config.apk}.sha256`; });
  }
  for (const [kind, paths] of [["screen", config.screenshots], ["image", config.images]]) {
    for (const [name, path] of Object.entries(paths || {})) {
      if (!/^[a-z]+$/.test(name) || !path || path.includes(":") || path.startsWith("/") || path.includes("..")) continue;
      document.querySelectorAll(`[data-${kind}="${name}"]`).forEach(el => {
        const image = new Image(); image.alt = "";
        image.className = kind === "screen" ? "screenshot-replacement" : "photo-replacement";
        image.decoding = "async"; image.loading = name === "hero" || name === "control" ? "eager" : "lazy";
        image.addEventListener("load", () => {
          const caption = el.querySelector(".scene-label");
          if (caption) caption.hidden = true;
        });
        image.addEventListener("error", () => image.remove()); image.src = path; el.append(image);
      });
    }
  }
  applyLanguage(lang, false);

  const header = document.querySelector(".site-header");
  const menu = document.querySelector("[data-menu-toggle]");
  function closeMenu() {
    header?.classList.remove("menu-open"); menu?.setAttribute("aria-expanded", "false");
    requestVisualUpdate();
  }
  menu?.addEventListener("click", () => {
    const open = header.classList.toggle("menu-open");
    menu.setAttribute("aria-expanded", String(open)); requestVisualUpdate();
  });
  document.querySelectorAll(".desktop-nav a").forEach(el => el.addEventListener("click", closeMenu));
  document.addEventListener("keydown", event => { if (event.key === "Escape") closeMenu(); });

  const reminder = document.querySelector("[data-support-reminder]");
  const supportSection = document.getElementById("support");
  const hero = document.querySelector(".hero-stage");
  let dismissed = false;
  try { dismissed = sessionStorage.getItem("karoride_support_dismissed") === "1"; } catch (_) { /* Still dismissible without storage. */ }
  document.querySelector("[data-dismiss-support]")?.addEventListener("click", () => {
    dismissed = true; reminder.hidden = true;
    try { sessionStorage.setItem("karoride_support_dismissed", "1"); } catch (_) { /* In-memory dismissal remains active. */ }
  });
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)");
  const smallScreen = matchMedia("(max-width: 760px)");
  const layers = [...document.querySelectorAll("[data-parallax]")];
  let scheduled = false;
  function updateVisuals() {
    scheduled = false;
    const height = innerHeight;
    for (const layer of layers) {
      const parent = layer.parentElement.getBoundingClientRect();
      // No scroll hijacking, no animation loop. Mobile and reduced motion stay still.
      const offset = reduceMotion.matches || smallScreen.matches || parent.bottom < -100 || parent.top > height + 100
        ? 0 : Math.max(-55, Math.min(55, (height / 2 - (parent.top + parent.height / 2)) * Number(layer.dataset.parallax)));
      layer.style.setProperty("--parallax-y", `${offset.toFixed(2)}px`);
    }
    if (reminder && supportSection && hero) {
      const pastHero = hero.getBoundingClientRect().bottom < -150;
      const beforeSupport = supportSection.getBoundingClientRect().top > height + 24;
      reminder.hidden = !supportUrl || dismissed || !pastHero || !beforeSupport || header.classList.contains("menu-open");
    }
  }
  function requestVisualUpdate() {
    if (!scheduled) { scheduled = true; requestAnimationFrame(updateVisuals); }
  }
  addEventListener("scroll", requestVisualUpdate, {passive:true});
  addEventListener("resize", requestVisualUpdate, {passive:true});
  reduceMotion.addEventListener("change", requestVisualUpdate);
  smallScreen.addEventListener("change", requestVisualUpdate);
  requestVisualUpdate();
})();
