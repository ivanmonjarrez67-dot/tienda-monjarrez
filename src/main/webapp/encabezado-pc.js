/* ==========================================================================
   encabezado-pc.js — Encabezado para computador (pantallas de más de 768px)
   --------------------------------------------------------------------------
   Franja negra (avisos + vender + QR) y barra principal con: logo y nombre,
   Mi tienda, Novedades, Categorías ▾ (panel con ejemplos), búsqueda con lupa
   roja, Perfil, Ayuda, carrito y camioncito.
   No cambia la lógica de filtros: todo "toca por detrás" los botones de
   siempre (.filter, .main, #searchButton, #qrBtn, #btnAbrirPerfil ...).
   Requiere encabezado-config.js. Cargar al FINAL del <body>.
   ========================================================================== */
(function () {
  "use strict";
  if (window.__pcInit) return;
  window.__pcInit = true;

  var MT = window.MT || {};
  var CATEGORIAS = MT.CATEGORIAS || [];
  var DESTACADOS = MT.DESTACADOS || [];
  var AVISOS = MT.AVISOS || [{ icono: "fa-shield-halved", texto: "Tienda Monjarrez" }];
  var MS_ROTAR = 5000;
  var MQ = window.matchMedia("(min-width: 769px)");

  /* ----------------------------- utilidades ----------------------------- */
  function q(sel, raiz) { return (raiz || document).querySelector(sel); }
  function qa(sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); }
  function crear(tag, clase, html) {
    var e = document.createElement(tag);
    if (clase) e.className = clase;
    if (html != null) e.innerHTML = html;
    return e;
  }
  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; });
  }
  function clic(el) { if (el) el.click(); }
  function haySesion() { return MT.haySesion ? MT.haySesion() : false; }
  function botonFiltro(cat) {
    var l = qa("button.filter");
    for (var i = 0; i < l.length; i++) if (l[i].getAttribute("data-category") === cat) return l[i];
    return null;
  }
  function botonMain(n) {
    var l = qa("button.main");
    for (var i = 0; i < l.length; i++) if (l[i].getAttribute("data-main") === n) return l[i];
    return null;
  }
  function limpiarFiltros() {
    try { filtroPrincipal = null; } catch (e) {}
    try { categoriaSeleccionada = null; } catch (e) {}
    qa(".main").forEach(function (b) { b.classList.remove("active"); });
    qa(".filter").forEach(function (b) { b.classList.remove("active"); });
  }
  function aplicarFiltro(tipo, valor) {
    limpiarFiltros();
    var inp = q("#search");
    if (inp) inp.value = "";
    clic(tipo === "main" ? botonMain(valor) : botonFiltro(valor));
    window.scrollTo(0, 0);
  }
  function buscarTexto(texto) {
    limpiarFiltros();
    var inp = q("#search");
    if (inp) inp.value = texto;
    clic(q("#searchButton"));
    window.scrollTo(0, 0);
  }

  /* ------------------------------- estado ------------------------------- */
  var header, headerContenido, strip, avisoTxt, avisoIco, venderBtn;
  var nav, btnTienda, btnCats, lockTienda;
  var acciones, btnPerfil, avatar, perfilTxt;
  var chipsWrap, chips, flechaDer, flechaIzq;
  var mega, megaLista, megaPanel, veil, perfilMenu, avisosOv, avisosCuerpo;
  var catActual = "Destacados";
  var abiertoEn = 0, timerAbrir = null, timerCerrar = null, timerAviso = null, idxAviso = 0;

  /* ----------------------------- construcción ----------------------------- */
  function construir() {
    var contenedor = q(".container");
    header = q(".container > header") || q("header");
    if (!contenedor || !header) return false;
    headerContenido = q(".header-content", header);
    var grupoBusqueda = q(".search-group", header);
    if (!headerContenido || !grupoBusqueda) return false;

    // ---- Franja negra ----
    strip = crear("div");
    strip.id = "pcStrip";
    var izq = crear("div", "pc-strip-aviso");
    avisoIco = crear("i", "fa-solid ico");
    avisoTxt = crear("span");
    avisoTxt.id = "pcAvisoTxt";
    var ver = crear("button", "pc-link", 'Ver<i class="fa-solid fa-chevron-right"></i>');
    ver.type = "button";
    ver.addEventListener("click", abrirAvisos);
    izq.appendChild(avisoIco); izq.appendChild(avisoTxt); izq.appendChild(ver);

    var der = crear("div", "pc-strip-der");
    venderBtn = crear("button", "pc-link", "");
    venderBtn.type = "button";
    venderBtn.addEventListener("click", function () {
      var esVend = esVendedor();
      clic(document.getElementById(esVend ? "btnPublicarHeroVendedor" : "btnVenderHero"));
    });
    var qr = crear("button", "pc-link", '<i class="fa-solid fa-qrcode"></i>QR');
    qr.type = "button";
    qr.title = "QR de Tienda Monjarrez";
    qr.addEventListener("click", function () { clic(q("#qrBtn")); });
    der.appendChild(venderBtn); der.appendChild(qr);
    strip.appendChild(izq); strip.appendChild(der);
    contenedor.insertBefore(strip, contenedor.firstChild);

    // ---- Enlaces de la barra principal ----
    nav = crear("div", "pc-nav pc-only");
    btnTienda = crear("button", "pc-nav-btn tienda", '<i class="fa-solid fa-star"></i>Mi tienda');
    lockTienda = crear("i", "fa-solid fa-lock lock");
    btnTienda.appendChild(lockTienda);
    btnTienda.type = "button";
    btnTienda.addEventListener("click", function () { cerrarTodo(); clic(botonFiltro("Mi tienda")); });

    btnCats = crear("button", "pc-nav-btn", 'Categorías<i class="fa-solid fa-chevron-down chev"></i>');
    btnCats.type = "button";
    btnCats.addEventListener("click", function () {
      if (!mega.classList.contains("on")) abrirMega();
      else if (Date.now() - abiertoEn > 700) cerrarMega(); // si recién se abrió con el cursor, el clic no lo cierra
    });
    btnCats.addEventListener("mouseenter", function () { clearTimeout(timerCerrar); timerAbrir = setTimeout(abrirMega, 140); });
    btnCats.addEventListener("mouseleave", programarCierre);

    nav.appendChild(btnTienda); nav.appendChild(btnCats);
    headerContenido.insertBefore(nav, grupoBusqueda);

    // ---- Perfil y Ayuda (después de la búsqueda, antes del carrito) ----
    acciones = crear("div", "pc-acciones pc-only");
    btnPerfil = crear("button", "pc-accion");
    btnPerfil.type = "button";
    avatar = crear("span", "pc-avatar", '<i class="fa-regular fa-user"></i>');
    perfilTxt = crear("span", "pc-perfil-txt");
    btnPerfil.appendChild(avatar); btnPerfil.appendChild(perfilTxt);
    btnPerfil.addEventListener("click", function (e) {
      e.stopPropagation();
      if (!haySesion()) { cerrarTodo(); if (typeof window.mostrarLogin === "function") window.mostrarLogin(); return; }
      if (perfilMenu.classList.contains("on")) cerrarPerfilMenu(); else abrirPerfilMenu();
    });
    var btnAyuda = crear("button", "pc-accion", '<i class="fa-regular fa-circle-question"></i><span class="pc-ayuda-txt">Ayuda</span>');
    btnAyuda.type = "button";
    btnAyuda.addEventListener("click", function () {
      cerrarTodo();
      if (typeof window.mostrarAyuda === "function") window.mostrarAyuda();
    });
    acciones.appendChild(btnPerfil); acciones.appendChild(btnAyuda);
    headerContenido.insertBefore(acciones, grupoBusqueda.nextSibling);

    // ---- Panel de categorías ----
    veil = crear("div"); veil.id = "pcVeil";
    veil.addEventListener("click", cerrarTodo);
    document.body.appendChild(veil);

    mega = crear("div", "pc-only");
    mega.id = "pcMega";
    megaLista = crear("div", "pc-mega-lista");
    megaPanel = crear("div", "pc-mega-panel");
    mega.appendChild(megaLista); mega.appendChild(megaPanel);
    mega.addEventListener("mouseenter", function () { clearTimeout(timerCerrar); });
    mega.addEventListener("mouseleave", programarCierre);
    header.appendChild(mega);

    // ---- Menú de perfil ----
    perfilMenu = crear("div", "pc-only");
    perfilMenu.id = "pcPerfilMenu";
    perfilMenu.addEventListener("click", function (e) { e.stopPropagation(); });
    header.appendChild(perfilMenu);

    // ---- Ventana de avisos ----
    avisosOv = crear("div");
    avisosOv.id = "pcAvisosOv";
    var caja = crear("div", "pc-avisos-caja");
    caja.innerHTML = '<div class="pc-avisos-top"><h2>Avisos</h2><button type="button" class="pc-x" aria-label="Cerrar">&times;</button></div>';
    avisosCuerpo = crear("div", "pc-avisos-cuerpo");
    caja.appendChild(avisosCuerpo);
    avisosOv.appendChild(caja);
    avisosOv.addEventListener("click", function (e) { if (e.target === avisosOv) cerrarAvisos(); });
    q(".pc-x", caja).addEventListener("click", cerrarAvisos);
    document.body.appendChild(avisosOv);

    construirChips();
    return true;
  }


  /* -------------------- filtros encima de los productos -------------------- */
  function construirChips() {
    var wrapper = document.getElementById("productGridWrapper");
    if (!wrapper) return;
    chipsWrap = crear("div");
    chipsWrap.id = "pcChipsWrap";
    chips = crear("div", "pc-chips");
    chips.setAttribute("role", "tablist");

    function chip(tipo, valor, etiqueta) {
      var b = crear("button", "pc-chip", esc(etiqueta));
      b.type = "button";
      b.setAttribute("data-tipo", tipo);
      b.setAttribute("data-valor", valor);
      b.addEventListener("click", function () { aplicarFiltro(tipo, valor); });
      chips.appendChild(b);
    }
    chip("filter", "Todo", "Todo");
    // Mi tienda y Categorías viven en la barra de arriba; aquí Novedades, los destacados y las categorías.
    DESTACADOS.forEach(function (d) { chip("main", d.nombre, d.nombre); });
    CATEGORIAS.forEach(function (c) { chip("filter", c.nombre, c.nombre); });

    flechaDer = crear("button", "pc-chip-flecha der", '<i class="fa-solid fa-chevron-right"></i>');
    flechaIzq = crear("button", "pc-chip-flecha izq", '<i class="fa-solid fa-chevron-left"></i>');
    flechaDer.type = flechaIzq.type = "button";
    flechaDer.setAttribute("aria-label", "Ver más filtros");
    flechaIzq.setAttribute("aria-label", "Ver filtros anteriores");
    flechaDer.addEventListener("click", function () { chips.scrollBy({ left: chips.clientWidth * 0.7, behavior: "smooth" }); });
    flechaIzq.addEventListener("click", function () { chips.scrollBy({ left: -chips.clientWidth * 0.7, behavior: "smooth" }); });
    chips.addEventListener("scroll", actualizarFlechas, { passive: true });
    window.addEventListener("resize", actualizarFlechas);

    chipsWrap.appendChild(flechaIzq);
    chipsWrap.appendChild(chips);
    chipsWrap.appendChild(flechaDer);

    // Justo encima de la cuadrícula: después del canvas de estrellas, antes de todo lo demás.
    var canvas = document.getElementById("starsCanvas");
    if (canvas && canvas.parentNode === wrapper) wrapper.insertBefore(chipsWrap, canvas.nextSibling);
    else wrapper.insertBefore(chipsWrap, wrapper.firstChild);

    var obs = new MutationObserver(sincronizarFiltros);
    qa("button.main, button.filter").forEach(function (b) { obs.observe(b, { attributes: true, attributeFilter: ["class"] }); });
    setTimeout(actualizarFlechas, 300);
  }

  function actualizarFlechas() {
    if (!chips) return;
    var max = chips.scrollWidth - chips.clientWidth;
    flechaIzq.classList.toggle("ver", chips.scrollLeft > 4);
    flechaDer.classList.toggle("ver", chips.scrollLeft < max - 4);
  }

  // Lo marcado sale siempre de los botones originales, así también se
  // refleja un filtro aplicado desde otro lado (enlaces, correos, etc.).
  function sincronizarFiltros() {
    var tipo = "filter", valor = "Todo";
    var m = q("button.main.active");
    var tiendaActiva = false;
    if (m) { tipo = "main"; valor = m.getAttribute("data-main"); }
    else {
      var f = qa("button.filter.active")[0];
      if (f) {
        valor = f.getAttribute("data-category");
        if (valor === "Mi tienda") tiendaActiva = true;
      }
    }
    if (chips) {
      var activo = null;
      qa(".pc-chip", chips).forEach(function (b) {
        var on = b.getAttribute("data-tipo") === tipo && b.getAttribute("data-valor") === valor;
        b.classList.toggle("on", on);
        if (on) activo = b;
      });
      if (activo && chips.scrollTo && MQ.matches) {
        chips.scrollTo({ left: activo.offsetLeft - (chips.clientWidth - activo.offsetWidth) / 2, behavior: "smooth" });
      }
    }
    // El enlace de Mi tienda también se marca cuando están activos.
    if (btnTienda) btnTienda.classList.toggle("on", tiendaActiva);
  }

  /* ----------------------------- categorías ----------------------------- */
  function programarCierre() {
    clearTimeout(timerAbrir);
    clearTimeout(timerCerrar);
    timerCerrar = setTimeout(cerrarMega, 220);
  }

  function pintarMegaLista() {
    megaLista.innerHTML = "";
    var items = [{ nombre: "Destacados", icono: "fa-fire" }].concat(CATEGORIAS);
    items.forEach(function (c) {
      var b = crear("button", "pc-mega-item" + (c.nombre === catActual ? " on" : ""),
        '<i class="fa-solid ' + c.icono + ' ic"></i><span>' + esc(c.nombre) + '</span><i class="fa-solid fa-chevron-right fl"></i>');
      b.type = "button";
      b.addEventListener("mouseenter", function () { if (catActual !== c.nombre) { catActual = c.nombre; pintarMegaLista(); pintarMegaPanel(); } });
      b.addEventListener("click", function () {
        if (c.nombre === "Destacados") { catActual = c.nombre; pintarMegaLista(); pintarMegaPanel(); return; }
        cerrarTodo(); aplicarFiltro("filter", c.nombre);
      });
      megaLista.appendChild(b);
    });
  }

  function tile(icono, texto, accion) {
    var b = crear("button", "pc-tile", '<span class="circ"><i class="fa-solid ' + icono + '"></i></span><span>' + esc(texto) + "</span>");
    b.type = "button";
    b.addEventListener("click", function () { cerrarTodo(); accion(); });
    return b;
  }

  function pintarMegaPanel() {
    megaPanel.innerHTML = "";
    megaPanel.scrollTop = 0;
    var grid = crear("div", "pc-mega-grid");
    if (catActual === "Destacados") {
      megaPanel.appendChild(crear("h3", null, "Destacados"));
      grid.appendChild(tile("fa-border-all", "Todo", function () { aplicarFiltro("filter", "Todo"); }));
      DESTACADOS.forEach(function (d) {
        grid.appendChild(tile(d.icono, d.nombre, function () { aplicarFiltro("main", d.nombre); }));
      });
    } else {
      var cat = CATEGORIAS.filter(function (c) { return c.nombre === catActual; })[0];
      if (!cat) return;
      megaPanel.appendChild(crear("h3", null, esc(cat.nombre)));
      var todo = crear("button", "pc-mega-todo", 'Ver todo en ' + esc(cat.nombre) + '<i class="fa-solid fa-arrow-right"></i>');
      todo.type = "button";
      todo.addEventListener("click", function () { cerrarTodo(); aplicarFiltro("filter", cat.nombre); });
      megaPanel.appendChild(todo);
      megaPanel.appendChild(crear("p", "pc-mega-sub", "Ejemplos"));
      cat.ejemplos.forEach(function (ej) {
        grid.appendChild(tile(cat.icono, ej, function () { buscarTexto(ej); }));
      });
    }
    megaPanel.appendChild(grid);
  }

  function abrirMega() {
    clearTimeout(timerAbrir); clearTimeout(timerCerrar);
    cerrarPerfilMenu();
    pintarMegaLista(); pintarMegaPanel();
    abiertoEn = Date.now();
    mega.classList.add("on");
    veil.classList.add("on");
    btnCats.classList.add("abierto");
  }
  function cerrarMega() {
    clearTimeout(timerAbrir);
    mega.classList.remove("on");
    btnCats.classList.remove("abierto");
    if (!perfilMenu.classList.contains("on")) veil.classList.remove("on");
  }

  /* ------------------------------- perfil ------------------------------- */
  function esVendedor() {
    var hv = document.getElementById("heroBannerVendedor");
    return !!(hv && hv.style.display !== "none");
  }

  function fila(icono, texto, accion, clase, href) {
    var e = crear(href ? "a" : "button", "pc-pm-fila" + (clase ? " " + clase : ""),
      '<i class="fa-solid ' + icono + '"></i><span>' + esc(texto) + "</span>");
    if (href) { e.href = href; e.target = "_blank"; e.rel = "noopener"; }
    else e.type = "button";
    e.addEventListener("click", function () {
      cerrarTodo();
      if (accion) setTimeout(accion, 120);
    });
    return e;
  }

  function pintarPerfilMenu() {
    perfilMenu.innerHTML = "";
    var saludo = crear("div", "pc-pm-titulo", "Tu cuenta");
    perfilMenu.appendChild(saludo);
    if (MT.nombre) MT.nombre().then(function (n) { if (n) saludo.textContent = "Hola, " + MT.primerNombre(n); });

    perfilMenu.appendChild(fila("fa-user", "Perfil", function () { clic(q("#btnAbrirPerfil")); }));
    perfilMenu.appendChild(fila("fa-truck-fast", "Mis pedidos", null, "", "mis-pedidos.html"));
    perfilMenu.appendChild(crear("div", "pc-pm-sep"));

    var temas = crear("div", "pc-pm-temas");
    [["fa-circle-half-stroke", "Tema", "themeToggle"], ["fa-meteor", "Estrellado", "starThemeBtn"], ["fa-heart", "Rosado", "pinkThemeBtn"]]
      .forEach(function (t) {
        var b = crear("button", null, '<i class="fa-solid ' + t[0] + '"></i><span>' + t[1] + "</span>");
        b.type = "button";
        b.addEventListener("click", function () { clic(document.getElementById(t[2])); });
        temas.appendChild(b);
      });
    perfilMenu.appendChild(temas);
    perfilMenu.appendChild(crear("div", "pc-pm-sep"));
    perfilMenu.appendChild(fila("fa-right-from-bracket", "Cerrar sesión", function () {
      if (typeof window.manejarClicSesion === "function") window.manejarClicSesion();
    }, "peligro"));
  }

  function abrirPerfilMenu() {
    cerrarMega();
    pintarPerfilMenu();
    perfilMenu.classList.add("on");
    veil.classList.add("on");
  }
  function cerrarPerfilMenu() {
    if (!perfilMenu) return;
    perfilMenu.classList.remove("on");
    if (!mega.classList.contains("on")) veil.classList.remove("on");
  }

  // Texto del botón de perfil, del botón de vender y candado de Mi tienda.
  var ultimoEstado = "";
  function refrescar() {
    var ses = haySesion();
    var vend = esVendedor();
    var estado = ses + "|" + vend;
    var b = botonFiltro("Mi tienda");
    lockTienda.style.display = (!b || b.getAttribute("data-bloqueado") === "true") ? "" : "none";
    venderBtn.innerHTML = vend
      ? '<i class="fa-solid fa-plus"></i>Publicar un nuevo producto'
      : '<i class="fa-solid fa-store"></i>¿Quieres vender?';

    if (estado === ultimoEstado) return;
    ultimoEstado = estado;
    if (!ses) {
      avatar.innerHTML = '<i class="fa-regular fa-user"></i>';
      perfilTxt.innerHTML = "<small>Bienvenido/a</small><b>Iniciar sesión</b>";
      return;
    }
    avatar.innerHTML = '<i class="fa-regular fa-user"></i>';
    perfilTxt.innerHTML = "<small>Hola,</small><b>Mi cuenta</b>";
    if (MT.nombre) MT.nombre().then(function (n) {
      if (!n || !haySesion()) return;
      var p = MT.primerNombre(n);
      avatar.textContent = p.charAt(0).toUpperCase();
      perfilTxt.innerHTML = "<small>Hola,</small><b>" + esc(p) + "</b>";
    });
  }

  /* ------------------------------- avisos ------------------------------- */
  function pintarAviso() {
    var a = AVISOS[idxAviso % AVISOS.length];
    avisoIco.className = "fa-solid ico " + a.icono;
    avisoTxt.textContent = a.texto;
  }
  function rotarAviso() {
    if (!MQ.matches || avisosOv.classList.contains("on")) return;
    avisoTxt.classList.add("out");
    setTimeout(function () { idxAviso++; pintarAviso(); avisoTxt.classList.remove("out"); }, 250);
  }
  function bannerVigente() {
    var ids = ["heroBanner", "heroBannerVendedor"];
    for (var i = 0; i < ids.length; i++) {
      var b = document.getElementById(ids[i]);
      if (b && b.style.display !== "none") return b;
    }
    return document.getElementById("heroBanner");
  }
  function abrirAvisos() {
    cerrarTodo();
    avisosCuerpo.innerHTML = "";
    var original = bannerVigente();
    if (original) {
      var copia = original.cloneNode(true);
      copia.removeAttribute("id");
      copia.style.display = "";
      qa("[id]", copia).forEach(function (n) { n.removeAttribute("id"); });
      qa(".cta-vender-btn", copia).forEach(function (n) { n.parentNode.removeChild(n); });
      qa("*", copia).forEach(function (n) {
        if (n.style && n.style.display === "none") n.style.display = "";
        n.classList.remove("ocultando", "reapareciendo");
      });
      avisosCuerpo.appendChild(copia);
    }
    avisosOv.classList.add("on");
  }
  function cerrarAvisos() { avisosOv.classList.remove("on"); }

  function cerrarTodo() {
    cerrarMega();
    cerrarPerfilMenu();
    veil.classList.remove("on");
  }

  /* ------------------------------- activar ------------------------------- */
  function alCambiarAncho() {
    document.body.classList.toggle("mt-pc", MQ.matches);
    if (!MQ.matches) { cerrarTodo(); cerrarAvisos(); }
    else { refrescar(); sincronizarFiltros(); actualizarFlechas(); }
  }

  function iniciar() {
    if (!construir()) return;
    pintarAviso();
    document.addEventListener("click", function (e) {
      if (perfilMenu.classList.contains("on") && !perfilMenu.contains(e.target)) cerrarPerfilMenu();
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") { cerrarTodo(); cerrarAvisos(); }
    });
    timerAviso = setInterval(rotarAviso, MS_ROTAR);
    setInterval(refrescar, 1500);
    if (MQ.addEventListener) MQ.addEventListener("change", alCambiarAncho);
    else if (MQ.addListener) MQ.addListener(alCambiarAncho);
    alCambiarAncho();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
  else iniciar();
})();