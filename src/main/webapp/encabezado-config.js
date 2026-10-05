/* ==========================================================================
   encabezado-config.js — Datos compartidos del encabezado (computador y teléfono)
   --------------------------------------------------------------------------
   Aquí se editan UNA sola vez las categorías con sus ejemplos y los avisos
   cortos. Cargar ANTES de encabezado-movil.js y encabezado-pc.js.
   ========================================================================== */
(function () {
  "use strict";

  var MT = (window.MT = window.MT || {});

  // Categorías reales de la tienda. "ejemplos": al tocar uno se busca esa palabra.
  // "icono": ícono de Font Awesome (fa-solid).
  MT.CATEGORIAS = [
    { nombre: "Damas", icono: "fa-person-dress", ejemplos: ["Vestidos", "Blusas", "Pantalones", "Calzado", "Carteras", "Ropa interior"] },
    { nombre: "Caballeros", icono: "fa-person", ejemplos: ["Camisas", "Pantalones", "Tenis", "Relojes", "Gorras", "Billeteras"] },
    { nombre: "Niños", icono: "fa-children", ejemplos: ["Juguetes", "Ropa de niño", "Ropa de niña", "Mochilas", "Útiles escolares", "Bebés"] },
    { nombre: "Hogar y Decoración", icono: "fa-couch", ejemplos: ["Cortinas", "Cocina", "Organizadores", "Iluminación", "Baño", "Alfombras"] },
    { nombre: "Tecnología y Electrónica", icono: "fa-laptop", ejemplos: ["Audífonos", "Cargadores", "Fundas", "Smartwatch", "Parlantes", "Cables"] },
    { nombre: "Joyería y Accesorios", icono: "fa-gem", ejemplos: ["Anillos", "Pulseras", "Collares", "Aretes", "Relojes", "Cadenas"] },
    { nombre: "Deportes", icono: "fa-dumbbell", ejemplos: ["Gimnasio", "Ciclismo", "Camping", "Fútbol", "Yoga", "Binoculares"] },
    { nombre: "Servicios", icono: "fa-screwdriver-wrench", ejemplos: ["Diseño", "Reparaciones", "Limpieza", "Transporte", "Clases", "Eventos"] },
    { nombre: "Alimentos", icono: "fa-utensils", ejemplos: ["Snacks", "Dulces", "Café", "Salsas", "Postres", "Bebidas"] }
  ];

  // Botones destacados (los .main de siempre). "Todo" es el .filter de "Todo".
  MT.DESTACADOS = [
    { nombre: "Novedades", icono: "fa-wand-magic-sparkles" },
    { nombre: "Descuentos", icono: "fa-percent" },
    { nombre: "Colección", icono: "fa-layer-group" },
    { nombre: "Recomendados", icono: "fa-thumbs-up" }
  ];

  // Avisos cortos que rotan en la franja (el texto completo sale en "Ver").
  MT.AVISOS = [
    { icono: "fa-truck-fast", texto: "¿Producto en zona alejada? Coordinamos tu envío" },
    { icono: "fa-magnifying-glass", texto: "¿No lo encuentras? Lo buscamos por ti" },
    { icono: "fa-shield-halved", texto: "Compra y vende con confianza en Tienda Monjarrez" }
  ];

  // Nombre de quien tiene sesión (viene de /api/perfil, igual que el panel
  // de Perfil). Devuelve una promesa con el nombre, o "" si no hay sesión.
  var cachePerfil = null, pendiente = null;
  function haySesion() {
    try { return typeof window.haySesionActiva === "function" && !!window.haySesionActiva(); }
    catch (e) { return false; }
  }
  MT.haySesion = haySesion;
  // Datos del perfil: { nombre, iconoUrl, esVendedor } o null si no hay sesión.
  MT.perfil = function () {
    if (!haySesion()) { cachePerfil = null; return Promise.resolve(null); }
    if (cachePerfil) return Promise.resolve(cachePerfil);
    if (pendiente) return pendiente;
    pendiente = fetch("/api/perfil", { cache: "no-store" })
      .then(function (r) { return r.ok ? r.json() : {}; })
      .then(function (p) {
        pendiente = null; p = p || {};
        var d = {
          nombre: (p.nombre || "").trim(),
          iconoUrl: (p.iconoUrl || "").trim(),
          esVendedor: String(p.tipo || "").toLowerCase() === "vendedor"
        };
        if (d.nombre) cachePerfil = d;
        return d;
      })
      .catch(function () { pendiente = null; return null; });
    return pendiente;
  };
  // Nombre de quien tiene sesión, o "" si no hay.
  MT.nombre = function () {
    return MT.perfil().then(function (d) { return d ? d.nombre : ""; });
  };
  // Llamar cuando cambie el perfil (p. ej. al subir un icono nuevo).
  MT.invalidarPerfil = function () {
    cachePerfil = null;
    try { document.dispatchEvent(new CustomEvent("mt:perfil-actualizado")); } catch (e) {}
  };
  MT.primerNombre = function (n) { return (n || "").trim().split(/\s+/)[0] || ""; };
})();