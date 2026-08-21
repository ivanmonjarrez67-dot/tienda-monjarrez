// 🆕 Se detecta ACÁ ARRIBA, apenas carga el script, si la página se
// abrió desde un enlace de producto compartido (?producto=ID). Tiene
// que guardarse en esta bandera global antes de que cualquier otra
// parte del código limpie ese parámetro de la URL con
// history.replaceState (ver abrirProductoDesdeUrl más abajo) — si se
// leyera la URL más tarde (por ejemplo en window.addEventListener("load")
// de index.html), podría ya no estar el parámetro.
window.__llegaDesdeProductoCompartido = new URLSearchParams(window.location.search).has("producto");

document.addEventListener("DOMContentLoaded", function () {
  document.getElementById("addProductButton").onclick = function () {
    document.getElementById("addProductModal").style.display = "block";
  };

  // 🔧 Antes solo tomaba el PRIMER ".close" del documento (querySelector),
  // así que al agregar el modal "Editar Producto" (que también usa la
  // clase .close) el botón de cerrar de "Agregar Producto" dejaba de
  // funcionar bien / cerraba el modal equivocado. Ahora cada botón
  // .close cierra el modal que lo contiene.
  document.querySelectorAll(".modal .close").forEach((btn) => {
    btn.onclick = function () {
      const modal = btn.closest(".modal");
      if (modal) modal.style.display = "none";
    };
  });

const themeToggle = document.getElementById('themeToggle');
const productGrid = document.getElementById('productGrid');

themeToggle.addEventListener('change', () => {
  if (themeToggle.checked) {
    activarClaro();
  } else {
    activarOscuro();
  }
});

// 🔧 Refactorizado a función reutilizable: antes solo servía para el
// formulario "Agregar Producto". La usamos también para "Editar
// Producto", que tiene sus propios inputs (editImagenProducto, etc.)
// pero necesita exactamente la misma lógica de subida a Cloudinary.
function conectarSubidaImagen(inputFileId, inputUrlId, previewImgId) {
  const inputFile = document.getElementById(inputFileId);
  const inputUrl = document.getElementById(inputUrlId);
  const previewImg = document.getElementById(previewImgId);
  if (!inputFile || !inputUrl || !previewImg) return;

  inputFile.addEventListener("change", () => {
    if (inputFile.files.length === 0) return;
    const file = inputFile.files[0];
    previewImg.src = URL.createObjectURL(file);
    previewImg.style.display = "inline-block";
    const formData = new FormData();
    formData.append("imagenProducto", file);
    fetch("/GuardarProductoArchivo", {
      method: "POST",
      body: formData
    })
    .then(resp => {
      if (!resp.ok)
        return resp.text().then(msg => { throw new Error(msg); });
      return resp.text();
    })
    .then(data => {
      const imageUrl = data.trim();
      inputUrl.value = imageUrl;
    })
    .catch(err => {
      alert("Error al subir la imagen: " + err.message);
    });
  });
}

conectarSubidaImagen("imagenProducto", "imageUrl", "previewImagen");
conectarSubidaImagen("editImagenProducto", "editImageUrl", "editPreviewImagen");
// 🆕 Imágenes adicionales (opcionales, hasta 2 extra): mismo mecanismo de
// subida, reutilizando conectarSubidaImagen con los ids de los nuevos
// inputs del formulario.
conectarSubidaImagen("imagenProducto2", "imageUrl2", "previewImagen2");
conectarSubidaImagen("imagenProducto3", "imageUrl3", "previewImagen3");
conectarSubidaImagen("editImagenProducto2", "editImageUrl2", "editPreviewImagen2");
conectarSubidaImagen("editImagenProducto3", "editImageUrl3", "editPreviewImagen3");

function cargarProductosMiTienda() {
  const idDiv = document.getElementById("usuarioIdVisibleMitienda");
  if (!idDiv) return;
  const idText = idDiv.textContent.trim();
  const usuarioId = idText.replace("ID de usuario:", "").trim();
  if (!usuarioId || isNaN(usuarioId)) return;

  fetch(`/MisProductosServlet?usuario_id=${usuarioId}`)
    .then(res => {
      if (!res.ok) throw new Error(`Error al obtener productos (status ${res.status})`);
      return res.json();
    })
    .then(productos => {
      const grid = document.getElementById("misProductosGrid");
      if (!grid) return;
      grid.innerHTML = "";
      productoSeleccionado = null;
      if (!productos || productos.length === 0) {
        grid.innerHTML = '<p style="color:white;">No tienes productos registrados.</p>';
        return;
      }
      productos.forEach(producto => {
        const card = document.createElement("div");
        card.className = "producto";
        const precioNumerico = parseFloat(producto.precio);
        const precioFormateado = !isNaN(precioNumerico)
          ? precioNumerico.toLocaleString("es-CR", { maximumFractionDigits: 0 })
          : null;
        const precioAnteriorNumerico = parseFloat(producto.precio_anterior);
        const tieneDescuento = !isNaN(precioAnteriorNumerico) && precioAnteriorNumerico > precioNumerico;
        const precioAnteriorHtml = tieneDescuento
          ? `<span class="producto-precio-anterior">₡${precioAnteriorNumerico.toLocaleString("es-CR", { maximumFractionDigits: 0 })}</span>
             <span class="producto-descuento-badge">-${Math.round((1 - precioNumerico / precioAnteriorNumerico) * 100)}%</span>`
          : "";
        const precioHtml = precioFormateado
          ? `<p class="producto-precio">₡${precioFormateado} ${precioAnteriorHtml}</p>`
          : "";
        const galeriaHtml = construirGaleriaHTML(
          [producto.imagen, producto.imagen2, producto.imagen3],
          producto.nombre || "",
          "width:150px;height:150px;object-fit:cover;border-radius:8px;"
        );
        card.innerHTML = `
          ${galeriaHtml}
          <h3>${producto.nombre || ''} (ID: ${producto.id})</h3>
          ${precioHtml}
          <div class="producto-mitienda-acciones">
            <button class="more-info-btn"
              data-id="${producto.id ?? ''}"
              data-empresa="${producto.empresa || ''}"
              data-imagen="${producto.imagen || ''}"
              data-imagen2="${producto.imagen2 || ''}"
              data-imagen3="${producto.imagen3 || ''}"
              data-nombre="${producto.nombre || ''}"
              data-descripcion="${producto.descripcion || ''}"
              data-provincia="${producto.provincia || ''}"
              data-ciudad="${producto.ciudad || ''}"
              data-telefono="${producto.telefono || ''}"
              data-correo="${producto.correo || ''}"
              data-precio="${producto.precio || ''}"
              data-precio-anterior="${producto.precio_anterior || ''}"
              data-categoria="${producto.categoria || ''}">
              Ver detalles
            </button>
            <button type="button" class="icon-btn editar-producto-btn" title="Editar producto" aria-label="Editar producto">
              <i class="fa-solid fa-pen"></i>
            </button>
            <button type="button" class="icon-btn eliminar-producto-btn" title="Eliminar producto" aria-label="Eliminar producto">
              <i class="fa-solid fa-trash-can"></i>
            </button>
          </div>
        `;
        grid.appendChild(card);
      });
    })
    .catch(err => console.error(err));
}

  const menuBtn = document.getElementById('menuBtn');
  const menuContent = document.getElementById('menuContent');
  if (menuBtn && menuContent) {
    // El botón vive dentro de .toolbar (que tiene overflow para
    // deslizarse hacia los lados), así que el desplegable usa
    // position:fixed (ver styles.css) calculado en JS, de modo que
    // siempre flota por encima de todo, incluido el grid de productos.
    function posicionarMenu() {
      const r = menuBtn.getBoundingClientRect();
      menuContent.style.top = (r.bottom + 8) + 'px';
      menuContent.style.right = (window.innerWidth - r.right) + 'px';
      menuContent.style.left = 'auto';
    }
    menuBtn.addEventListener('click', () => {
      const abrir = menuContent.style.display !== 'block';
      if (abrir) posicionarMenu();
      menuContent.style.display = abrir ? 'block' : 'none';
    });
    window.addEventListener('click', (e) => {
      if (!menuBtn.contains(e.target) && !menuContent.contains(e.target)) {
        menuContent.style.display = 'none';
      }
    });
    window.addEventListener('resize', () => {
      if (menuContent.style.display === 'block') posicionarMenu();
    });
    window.addEventListener('scroll', () => {
      if (menuContent.style.display === 'block') posicionarMenu();
    }, true);
  }

window.mostrarAyuda = function mostrarAyuda() {
  const modal = document.getElementById("ayudaModal");
  if (modal) modal.classList.add("activo");
}

const ayudaModal = document.getElementById("ayudaModal");
const cerrarAyuda = document.getElementById("cerrarAyudaModal");
if (cerrarAyuda) cerrarAyuda.addEventListener("click", () => ayudaModal.classList.remove("activo"));
if (ayudaModal) {
  ayudaModal.addEventListener("click", (e) => {
    if (e.target === ayudaModal) ayudaModal.classList.remove("activo");
  });
}

document.getElementById("addProductForm").onsubmit = function (event) {
  event.preventDefault();
  const idText = document.getElementById("usuarioIdVisibleMitienda").textContent.trim();
  const usuarioId = idText.replace("ID de usuario:", "").trim();
  const name = document.getElementById("name").value.trim();
  const categoria = document.getElementById("categoria").value;
  const description = document.getElementById("description").value.trim();
  const price = document.getElementById("price").value.trim();
  const imageUrl = document.getElementById("imageUrl").value.trim();
  const empresa = document.getElementById("empresa").value.trim();
  const telefono = document.getElementById("telefono").value.trim();
  const correo = document.getElementById("correoProducto").value.trim();
  const provincia = document.getElementById("provincia").value;
  const ciudad = document.getElementById("ciudad").value.trim();

  // 🆕 Opcionales: precio anterior (rebaja) e imágenes adicionales 2 y 3.
  const precioAnteriorInput = document.getElementById("precioAnterior");
  const precioAnterior = precioAnteriorInput ? precioAnteriorInput.value.trim() : "";
  const imageUrl2Input = document.getElementById("imageUrl2");
  const imageUrl2 = imageUrl2Input ? imageUrl2Input.value.trim() : "";
  const imageUrl3Input = document.getElementById("imageUrl3");
  const imageUrl3 = imageUrl3Input ? imageUrl3Input.value.trim() : "";

  if (!usuarioId || isNaN(usuarioId)) {
    alert("No se pudo obtener el ID del usuario. Vuelva a iniciar sesión o revise el campo.");
    return;
  }

  const limite = LIMITE_PRODUCTOS[miTiendaTipoSuscripcion];
  if (limite) {
    const cantidadActual = document.querySelectorAll("#misProductosGrid .producto").length;
    if (cantidadActual >= limite) {
      alert(`Alcanzó el límite de ${limite} productos para su suscripción "${miTiendaTipoSuscripcion}". Elimine algún producto o mejore su plan para agregar más.`);
      return;
    }
  }

  if (!empresa || empresa.length < 2) {
    alert("Por favor, ingrese un nombre válido para su empresa (mínimo 2 caracteres).");
    return;
  }
  if (!correo) {
    alert("Por favor, ingrese un correo electrónico válido.");
    return;
  }
  const correoRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!correoRegex.test(correo)) {
    alert("El correo ingresado no tiene un formato válido.");
    return;
  }
  if (!price || isNaN(price) || Number(price) <= 0) {
    alert("Por favor, ingrese un precio válido mayor que 0.");
    return;
  }

  fetch("/GuardarProducto", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      usuario_id: usuarioId,
      nombre: name,
      categoria: categoria,
      descripcion: description,
      precio: price,
      imagen: imageUrl,
      empresa: empresa,
      telefono: telefono,
      correo: correo,
      provincia: provincia,
      ciudad: ciudad,
      precio_anterior: precioAnterior,
      imagen2: imageUrl2,
      imagen3: imageUrl3,
    }),
  })
    .then((response) => {
      if (!response.ok) {
        return response.text().then((msg) => { throw new Error("Error del servidor: " + msg); });
      }
      cargarProductosMiTienda();
      alert("Producto guardado con éxito.");
      document.getElementById("addProductModal").style.display = "none";
      document.getElementById("addProductForm").reset();
      window.Monji?.exito("¡Tu producto ya está publicado! 🎉");
    })
    .catch((error) => {
      alert("Error al conectar con el servidor: " + error.message);
    });
};

function pintarMosaico(grid, productos, construirTarjetaHTML) {
  grid.classList.add("mosaic-grid");
  grid.innerHTML = "";
  if (!productos || productos.length === 0) return false;
  const anchoDisponible = grid.clientWidth || window.innerWidth;
  const anchoMinColumna = 210;
  let numColumnas = window.innerWidth <= 500 ? 2 : Math.max(1, Math.floor(anchoDisponible / anchoMinColumna));
  numColumnas = Math.min(numColumnas, productos.length);
  const columnas = [];
  for (let i = 0; i < numColumnas; i++) {
    const col = document.createElement("div");
    col.className = "masonry-col";
    grid.appendChild(col);
    columnas.push(col);
  }
  productos.forEach((producto) => {
    const card = document.createElement("div");
    card.className = "producto";
    card.setAttribute("data-categoria", (producto.nombre || "").toLowerCase());
    card.innerHTML = construirTarjetaHTML(producto);
    let colMasCorta = columnas[0];
    columnas.forEach((col) => {
      if (col.children.length < colMasCorta.children.length) colMasCorta = col;
    });
    colMasCorta.appendChild(card);
  });
  return true;
}

// 🆕 Aviso "mega mercado": se usa en dos casos —
//   1) una búsqueda o filtro no encuentra coincidencias (conContactoExtra:
//      false, solo el correo de "buscamos"), y
//   2) al final del listado completo de productos (conContactoExtra: true,
//      agrega también el correo de soporte como canal alterno). El envío
//      es simple: links mailto: con asunto/cuerpo prellenado, sin backend
//      nuevo de por medio. (🔧 Antes el segundo canal era un teléfono —
//      no se veía profesional — ahora también es un mailto.)
function crearAvisoMegaMercado({ conContactoExtra } = {}) {
  const asunto = encodeURIComponent("Busco un producto en Tienda Monjarrez");
  const cuerpo = encodeURIComponent(
    "Hola,\n\nNo encontré el producto que buscaba en el catálogo. ¿Podrían ayudarme a conseguirlo en su mega mercado y coordinar el envío?\n\nProducto: \n\nQuedo atento/a, muchas gracias."
  );
  const mailto = `mailto:buscamos@tiendamonjarrez.com?subject=${asunto}&body=${cuerpo}`;
  const contactoExtraHtml = conContactoExtra
    ? `<p class="mercado-aviso-telefono"><i class="fa-solid fa-headset"></i> ¿Prefieres contactar a soporte? Escríbenos a <a href="mailto:soporte@tiendamonjarrez.com">soporte@tiendamonjarrez.com</a></p>`
    : "";
  return `
    <div class="mercado-aviso">
      <div class="mercado-aviso-icono"><i class="fa-solid fa-store"></i></div>
      <h3>¿No encuentras lo que buscabas?</h3>
      <p>Nuestros vendedores suben productos nuevos cada día. Si aún no está publicado, tenemos acceso a un mega mercado para buscarlo, coordinar el envío y avisarte apenas lo consigamos.</p>
      <a class="mercado-aviso-btn" href="${mailto}"><i class="fa-solid fa-envelope"></i> Escríbenos a buscamos@tiendamonjarrez.com</a>
      ${contactoExtraHtml}
    </div>
  `;
}

// 🆕 Muestra el aviso de mega mercado justo después del último producto
// del catálogo COMPLETO (sin filtros ni búsqueda). Vive en un contenedor
// aparte (#avisoFinCatalogo) para no pisar el grid ni tener que
// reconstruirlo. Se limpia cuando no aplica (catálogo vacío, búsqueda o
// filtro activos) para que no quede pegado donde no corresponde.
function mostrarAvisoFinCatalogo() {
  const contenedor = document.getElementById("avisoFinCatalogo");
  if (contenedor) contenedor.innerHTML = crearAvisoMegaMercado({ conContactoExtra: true });
}

function limpiarAvisoFinCatalogo() {
  const contenedor = document.getElementById("avisoFinCatalogo");
  if (contenedor) contenedor.innerHTML = "";
}

// 🆕 Arma la galería de una tarjeta: 1 sola imagen (imagen base) si no hay
// fotos adicionales, o un mini carrusel con puntitos si hay imagen2 y/o
// imagen3. Las imágenes viajan en data-imagenes (JSON) para que el click
// delegado de los puntos (ver listener global más abajo) sepa a qué
// imagen cambiar sin tener que reconstruir la tarjeta entera.
function construirGaleriaHTML(imagenes, alt, estiloImg) {
  const validas = imagenes.filter(Boolean);
  if (validas.length === 0) {
    return `<img src="" alt="${alt}" style="${estiloImg}">`;
  }
  const jsonImagenes = JSON.stringify(validas).replace(/"/g, "&quot;");
  if (validas.length === 1) {
    return `<div class="producto-galeria" data-imagenes="${jsonImagenes}">
      <img class="galeria-img" src="${validas[0]}" alt="${alt}" style="${estiloImg}">
    </div>`;
  }
  const dotsHtml = validas
    .map((_, i) => `<span class="galeria-dot${i === 0 ? " active" : ""}" data-index="${i}"></span>`)
    .join("");
  return `<div class="producto-galeria" data-imagenes="${jsonImagenes}">
    <img class="galeria-img" src="${validas[0]}" alt="${alt}" style="${estiloImg}">
    <div class="galeria-dots">${dotsHtml}</div>
  </div>`;
}
// 🆕 construirGaleriaHTML se define DENTRO del callback de
// DOMContentLoaded, así que por defecto es una función local a ese
// bloque y no existe en window. El panel "Ver detalles" de index.html
// vive en un <script> aparte y depende de encontrarla como
// window.construirGaleriaHTML (ver el "typeof construirGaleriaHTML"
// ahí) — sin esta línea, ese chequeo siempre daba false y el panel
// caía al <img> simple sin carrusel ni puntos.
window.construirGaleriaHTML = construirGaleriaHTML;

// 🆕 Cambia la imagen activa de una galería dado un índice destino (usada
// tanto por el clic en los puntos como por el swipe). Se encarga de
// actualizar la imagen mostrada y qué punto queda "active".
function irAImagenGaleria(galeria, idx) {
  let imagenes = [];
  try { imagenes = JSON.parse(galeria.dataset.imagenes || "[]"); } catch (err) { imagenes = []; }
  if (!imagenes.length) return;
  const idxSeguro = ((idx % imagenes.length) + imagenes.length) % imagenes.length; // wrap-around
  const img = galeria.querySelector(".galeria-img");
  if (img && imagenes[idxSeguro]) img.src = imagenes[idxSeguro];
  const dots = galeria.querySelectorAll(".galeria-dot");
  dots.forEach((d, i) => d.classList.toggle("active", i === idxSeguro));
}

// 🆕 Click delegado (una sola vez, a nivel de documento) para los puntitos
// de cualquier galería de la página: catálogo, "Mi tienda" y panel de
// detalle. Cambia la imagen mostrada sin recargar ni reconstruir la
// tarjeta.
document.addEventListener("click", (e) => {
  const dot = e.target.closest(".galeria-dot");
  if (!dot) return;
  const galeria = dot.closest(".producto-galeria");
  if (!galeria) return;
  irAImagenGaleria(galeria, parseInt(dot.dataset.index, 10));
});

// 🆕 Deslizar para cambiar de imagen (swipe táctil en celular, o
// arrastrar con el cursor en escritorio). Se usan Pointer Events porque
// unifican mouse y touch en un solo set de listeners: no hace falta
// duplicar la lógica para touchstart/touchend y mousedown/mouseup.
// Solo se activa si el arrastre fue mayormente HORIZONTAL y superó un
// mínimo de píxeles (UMBRAL_SWIPE), para no interferir con el scroll
// vertical normal de la página ni con un simple clic/tap.
const UMBRAL_SWIPE = 30;
let swipeGaleria = null;
let swipeInicioX = 0;
let swipeInicioY = 0;

document.addEventListener("pointerdown", (e) => {
  const galeria = e.target.closest(".producto-galeria");
  if (!galeria || !galeria.querySelector(".galeria-dots")) return; // solo si hay más de 1 imagen
  swipeGaleria = galeria;
  swipeInicioX = e.clientX;
  swipeInicioY = e.clientY;
});

document.addEventListener("pointerup", (e) => {
  if (!swipeGaleria) return;
  const galeria = swipeGaleria;
  swipeGaleria = null;
  const deltaX = e.clientX - swipeInicioX;
  const deltaY = e.clientY - swipeInicioY;
  if (Math.abs(deltaX) < UMBRAL_SWIPE || Math.abs(deltaX) < Math.abs(deltaY)) return;
  const dots = galeria.querySelectorAll(".galeria-dot");
  let idxActual = 0;
  dots.forEach((d, i) => { if (d.classList.contains("active")) idxActual = i; });
  // Deslizar hacia la izquierda (deltaX negativo) = siguiente imagen;
  // hacia la derecha (deltaX positivo) = imagen anterior.
  irAImagenGaleria(galeria, idxActual + (deltaX < 0 ? 1 : -1));
});


function construirTarjetaProductoHTML(producto) {
  // 🆕 Precio visible directo en la tarjeta (antes solo vivía dentro de
  // data-precio, oculto hasta abrir "Ver detalles"). Se formatea con
  // separador de miles para que se lea bien con montos grandes.
  const precioNumerico = parseFloat(producto.precio);
  const precioFormateado = !isNaN(precioNumerico)
    ? precioNumerico.toLocaleString("es-CR", { maximumFractionDigits: 0 })
    : null;

  // 🆕 Precio anterior tachado (rebaja): viene de la tabla Descuentos vía
  // el JSON del backend (producto.precio_anterior). Solo se muestra si es
  // mayor al precio real.
  const precioAnteriorNumerico = parseFloat(producto.precio_anterior);
  const tieneDescuento = !isNaN(precioAnteriorNumerico) && precioAnteriorNumerico > precioNumerico;
  const precioAnteriorHtml = tieneDescuento
    ? `<span class="producto-precio-anterior">₡${precioAnteriorNumerico.toLocaleString("es-CR", { maximumFractionDigits: 0 })}</span>
       <span class="producto-descuento-badge">-${Math.round((1 - precioNumerico / precioAnteriorNumerico) * 100)}%</span>`
    : "";

  const precioHtml = precioFormateado
    ? `<p class="producto-precio">₡${precioFormateado} ${precioAnteriorHtml}</p>`
    : "";
  const badgeHtml = producto.empresa
    ? `<span class="producto-badge">${producto.empresa}</span>`
    : "";

  let galeriaHtml = construirGaleriaHTML(
    [producto.imagen, producto.imagen2, producto.imagen3],
    producto.nombre || "",
    "width:150px;height:150px;object-fit:cover;"
  );

  // 🔧 El badge del nombre de empresa se inserta DENTRO del contenedor de
  // la imagen (.producto-galeria, que ya es position:relative) en vez de
  // quedar como hermano suelto de toda la tarjeta. Así "bottom" (ver
  // styles.css) lo ancla a la esquina inferior de la FOTO, no al fondo de
  // toda la tarjeta (que quedaría flotando sobre el precio/botón).
  if (badgeHtml) {
    galeriaHtml = galeriaHtml.replace(
      /(<div class="producto-galeria"[^>]*>)/,
      `$1${badgeHtml}`
    );
  }

  return `
        ${galeriaHtml}
        <h3>${producto.nombre || ''}</h3>
        ${precioHtml}
        <button class="more-info-btn"
          data-id="${producto.id ?? ''}"
          data-empresa="${producto.empresa || ''}"
          data-imagen="${producto.imagen || ''}"
          data-imagen2="${producto.imagen2 || ''}"
          data-imagen3="${producto.imagen3 || ''}"
          data-nombre="${producto.nombre || ''}"
          data-descripcion="${producto.descripcion || ''}"
          data-provincia="${producto.provincia || ''}"
          data-ciudad="${producto.ciudad || ''}"
          data-telefono="${producto.telefono || ''}"
          data-correo="${producto.correo || ''}"
          data-precio="${producto.precio || ''}"
          data-precio-anterior="${producto.precio_anterior || ''}"
        >
          Ver detalles
        </button>
      `;
}

function mostrarEsqueletoCarga(grid, cantidad = 8) {
  if (!grid) return;
  grid.classList.remove("mosaic-grid");
  grid.classList.add("skeleton-grid");
  grid.innerHTML = "";
  for (let i = 0; i < cantidad; i++) {
    const tarjeta = document.createElement("div");
    tarjeta.className = "skeleton-card";
    tarjeta.innerHTML = `
      <div class="skeleton-img"></div>
      <div class="skeleton-line skeleton-line--title"></div>
      <div class="skeleton-line skeleton-line--btn"></div>
    `;
    grid.appendChild(tarjeta);
  }
}

// 🆕 Si la página se abrió desde un enlace compartido
// (tiendamonjarrez.com/index.html?producto=123, a donde redirige
// ProductoServlet después de servir los metadatos Open Graph), abre
// automáticamente el panel "Ver detalles" de ese producto para que la
// persona no tenga que buscarlo a mano en el catálogo.
function abrirProductoDesdeUrl() {
  const params = new URLSearchParams(window.location.search);
  const idProducto = params.get("producto");
  if (!idProducto) return;
  const btn = document.querySelector(`.more-info-btn[data-id="${idProducto}"]`);
  if (btn) {
    btn.click();
    btn.scrollIntoView({ behavior: "smooth", block: "center" });
  }
  // Limpiar el parámetro de la URL para que no se reabra el panel si el
  // usuario navega y regresa, sin necesidad de recargar la página.
  window.history.replaceState({}, document.title, window.location.pathname);
}

const gridProductosInicial = document.getElementById("productGrid");
mostrarEsqueletoCarga(gridProductosInicial);

fetch("/api/productos")
  .then((response) => response.json())
  .then((productos) => {
    const grid = document.getElementById("productGrid");
    grid.classList.remove("skeleton-grid");
    pintarMosaico(grid, productos, construirTarjetaProductoHTML);
    abrirProductoDesdeUrl();
    // 🆕 Este fetch es el catálogo COMPLETO (sin filtros ni búsqueda), así
    // que aquí sí corresponde el aviso de mega mercado al final del listado.
    if (productos && productos.length > 0) mostrarAvisoFinCatalogo();
  })
  .catch((error) => {
    console.error("Error al cargar productos:", error);
    const grid = document.getElementById("productGrid");
    if (grid) { grid.classList.remove("skeleton-grid"); grid.innerHTML = ""; }
  });

  window.addEventListener("scroll", function () {
    const image = document.querySelector(".welcome-image");
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    if (image) image.style.transform = `translateY(${scrollTop * 0.3}px)`;
  });

const searchButton = document.getElementById("searchButton");
const searchInput = document.getElementById("search");

// Búsqueda por texto único: el servlet ya compara nombre, descripción,
// empresa y provincia contra este mismo valor, así que aquí solo se
// manda "q". Los #provincia/#ciudad son del form "Agregar Producto",
// no se tocan desde aquí.
function buscarProductos() {
  const query = searchInput.value.trim();
  const url = query
    ? `/api/busqueda-productos?q=${encodeURIComponent(query)}`
    : `/api/busqueda-productos`;

  fetch(url)
    .then((response) => response.json())
    .then((productos) => {
      const grid = document.getElementById("productGrid");
      limpiarAvisoFinCatalogo(); // ya no es el catálogo completo, ese aviso no aplica aquí
      if (productos.length === 0) {
        grid.innerHTML = crearAvisoMegaMercado({ conContactoExtra: false });
        window.Monji?.buscando("No encontré ese producto en el catálogo, pero tranquilo: tenemos acceso a un mega mercado. Escríbenos y te ayudamos a conseguirlo 🔍");
        return;
      }
      pintarMosaico(grid, productos, construirTarjetaProductoHTML);
    })
    .catch((error) => console.error("Error al buscar productos:", error));
}

if (searchButton) searchButton.addEventListener("click", buscarProductos);
searchInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") { e.preventDefault(); buscarProductos(); }
});

let filtroPrincipal = null;
let categoriaSeleccionada = null;

document.querySelectorAll(".main").forEach((btn) => {
  btn.addEventListener("click", () => {
    filtroPrincipal = btn.dataset.main;
    document.querySelectorAll(".main").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    cargarProductos();
  });
});

document.querySelectorAll(".filter").forEach((btn) => {
  btn.addEventListener("click", (e) => {
    // 🆕 "Mi tienda" bloqueada para invitados/compradores: antes el
    // botón quedaba con `disabled`, así que ni siquiera llegaba a
    // dispararse este clic. Ahora sí llega, y en vez de aplicar el
    // filtro se le explica a la persona por qué esa sección es
    // exclusiva para vendedores — con stopImmediatePropagation() para
    // que tampoco se dispare el otro listener de ".filter" que solo
    // marca la clase "active" (ver index.html), y así el botón no
    // quede marcado como seleccionado sin haber entrado realmente.
    if (btn.dataset.category === "Mi tienda" && btn.dataset.bloqueado === "true") {
      e.stopImmediatePropagation();
      alert("La sección \"Mi tienda\" es exclusiva para vendedores registrados en Tienda Monjarrez. Regístrate o inicia sesión como vendedor para publicar y administrar tus propios productos.");
      return;
    }
    categoriaSeleccionada = btn.dataset.category;
    document.querySelectorAll(".filter").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const wrapperCatalogo = document.getElementById("productGridWrapper");
    const miTiendaContainer = document.getElementById("miTiendaContainer");
    if (categoriaSeleccionada === "Mi tienda") {
      if (wrapperCatalogo) wrapperCatalogo.style.display = "none";
    } else {
      if (wrapperCatalogo) wrapperCatalogo.style.display = "";
      if (miTiendaContainer) miTiendaContainer.style.display = "none";
      cargarProductos();
    }
    if (categoriaSeleccionada === "Mi tienda") {
      document.getElementById("miTiendaModal").style.display = "block";
    }
  });
});

function cargarProductos() {
  const params = new URLSearchParams();
  if (filtroPrincipal) params.append("filtro", filtroPrincipal);
  if (categoriaSeleccionada) params.append("categoria", categoriaSeleccionada);
  fetch(`/api/productos-filtrados?${params.toString()}`)
    .then((response) => response.json())
    .then((productos) => {
      const grid = document.getElementById("productGrid");
      // Solo es "el catálogo completo" si no hay filtro ni categoría
      // aplicados; con cualquiera de los dos activo, el aviso de fin de
      // catálogo no corresponde aquí.
      const esCatalogoCompleto = !filtroPrincipal && !categoriaSeleccionada;
      if (productos.length === 0) {
        limpiarAvisoFinCatalogo();
        grid.innerHTML = crearAvisoMegaMercado({ conContactoExtra: false });
        window.Monji?.buscando("No encontré productos en esta categoría, pero tranquilo: tenemos acceso a un mega mercado. Escríbenos y te ayudamos a conseguirlo 🔍");
        return;
      }
      pintarMosaico(grid, productos, construirTarjetaProductoHTML);
      if (esCatalogoCompleto) mostrarAvisoFinCatalogo();
      else limpiarAvisoFinCatalogo();
    })
    .catch((error) => console.error("Error al cargar productos filtrados:", error));
}

let miTiendaTipoSuscripcion = null;
const LIMITE_PRODUCTOS = { "Básica": 7, "Avanzada": 14 };

document.getElementById("cerrarMiTiendaModal").addEventListener("click", () => {
  document.getElementById("miTiendaModal").style.display = "none";
  const wrapperCatalogo = document.getElementById("productGridWrapper");
  if (wrapperCatalogo) wrapperCatalogo.style.display = "";
  cargarProductos();
});

document.getElementById("miTiendaForm").addEventListener("submit", function (e) {
  e.preventDefault();
  const cedula = document.getElementById("miTiendaCedula").value;
  const password = document.getElementById("miTiendaPassword").value;
  const errorMsg = document.getElementById("errorMsgMiTienda");
  errorMsg.style.display = "none";

  fetch("/api/mi-tienda-login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `cedula=${encodeURIComponent(cedula)}&password=${encodeURIComponent(password)}`
  })
  .then(response => {
    return response.text().then(text => {
      if (response.ok && text.startsWith("OK:")) {
        const partes = text.split(":");
        const usuarioId = partes[1].trim();
        miTiendaTipoSuscripcion = partes[2] ? partes[2].trim() : null;
        const idDiv = document.getElementById("usuarioIdVisibleMitienda");
        if (idDiv) {
          idDiv.textContent = `ID de usuario: ${usuarioId}`;
          idDiv.style.display = "block";
        }
        document.getElementById("miTiendaModal").style.display = "none";
        document.getElementById("miTiendaContainer").style.display = "block";
        cargarProductosMiTienda(cedula);
      } else {
        errorMsg.textContent = text || "Credenciales inválidas";
        errorMsg.style.display = "block";
      }
    });
  })
  .catch(err => {
    errorMsg.textContent = "Error en la conexión";
    errorMsg.style.display = "block";
  });
});

let productoSeleccionado = null;

document.getElementById("misProductosGrid").addEventListener("click", (e) => {
  const card = e.target.closest(".producto");
  if (!card) return;
  document.querySelectorAll("#misProductosGrid .producto").forEach(p => p.classList.remove("selected"));
  card.classList.add("selected");
  const btn = card.querySelector(".more-info-btn");
  // 🆕 Se guardan todos los campos del producto (antes solo id/nombre/
  // descripción, suficiente para "Eliminar" pero no para precargar el
  // formulario de "Editar Producto").
  productoSeleccionado = {
    id: btn.dataset.id,
    nombre: btn.dataset.nombre,
    descripcion: btn.dataset.descripcion,
    empresa: btn.dataset.empresa,
    imagen: btn.dataset.imagen,
    imagen2: btn.dataset.imagen2,
    imagen3: btn.dataset.imagen3,
    provincia: btn.dataset.provincia,
    ciudad: btn.dataset.ciudad,
    telefono: btn.dataset.telefono,
    correo: btn.dataset.correo,
    precio: btn.dataset.precio,
    precioAnterior: btn.dataset.precioAnterior,
    categoria: btn.dataset.categoria
  };

  // 🆕 Íconos de editar (lápiz) y eliminar (tacho) de ESTA tarjeta: como
  // productoSeleccionado ya quedó armado arriba con los datos de esta
  // misma tarjeta, basta con llamar la acción correspondiente — no hace
  // falta abrir "Ver detalles" primero.
  if (e.target.closest(".editar-producto-btn")) {
    abrirModalEdicionProducto();
    return;
  }
  if (e.target.closest(".eliminar-producto-btn")) {
    eliminarProductoSeleccionado();
    return;
  }
});

// 🆕 "Editar producto": antes era un botón único arriba de "Mi tienda"
// que operaba sobre el último producto abierto con "Ver detalles".
// Ahora es el ícono de lápiz de cada tarjeta, así que se llama
// directamente con el producto de ESA tarjeta (ver click delegado más
// arriba en #misProductosGrid) y precarga el modal de edición con sus
// datos actuales, incluida la imagen.
function abrirModalEdicionProducto() {
  if (!productoSeleccionado) {
    alert("Seleccione un producto para editar.");
    return;
  }
  document.getElementById("editProductId").value = productoSeleccionado.id || "";
  document.getElementById("editName").value = productoSeleccionado.nombre || "";
  document.getElementById("editCategoria").value = productoSeleccionado.categoria || "";
  document.getElementById("editDescription").value = productoSeleccionado.descripcion || "";
  document.getElementById("editImageUrl").value = productoSeleccionado.imagen || "";
  document.getElementById("editTelefono").value = productoSeleccionado.telefono || "";
  document.getElementById("editCorreoProducto").value = productoSeleccionado.correo || "";
  document.getElementById("editProvincia").value = productoSeleccionado.provincia || "";
  document.getElementById("editCiudad").value = productoSeleccionado.ciudad || "";
  document.getElementById("editPrice").value = productoSeleccionado.precio || "";
  document.getElementById("editEmpresa").value = productoSeleccionado.empresa || "";

  // 🆕 Precio anterior (rebaja) e imágenes adicionales — opcionales, se
  // dejan vacíos si el producto no tenía ninguno.
  const editPrecioAnteriorInput = document.getElementById("editPrecioAnterior");
  if (editPrecioAnteriorInput) editPrecioAnteriorInput.value = productoSeleccionado.precioAnterior || "";
  const editImageUrl2Input = document.getElementById("editImageUrl2");
  if (editImageUrl2Input) editImageUrl2Input.value = productoSeleccionado.imagen2 || "";
  const editImageUrl3Input = document.getElementById("editImageUrl3");
  if (editImageUrl3Input) editImageUrl3Input.value = productoSeleccionado.imagen3 || "";

  const preview = document.getElementById("editPreviewImagen");
  if (preview) {
    if (productoSeleccionado.imagen) {
      preview.src = productoSeleccionado.imagen;
      preview.style.display = "inline-block";
    } else {
      preview.removeAttribute("src");
      preview.style.display = "none";
    }
  }
  const preview2 = document.getElementById("editPreviewImagen2");
  if (preview2) {
    if (productoSeleccionado.imagen2) {
      preview2.src = productoSeleccionado.imagen2;
      preview2.style.display = "inline-block";
    } else {
      preview2.removeAttribute("src");
      preview2.style.display = "none";
    }
  }
  const preview3 = document.getElementById("editPreviewImagen3");
  if (preview3) {
    if (productoSeleccionado.imagen3) {
      preview3.src = productoSeleccionado.imagen3;
      preview3.style.display = "inline-block";
    } else {
      preview3.removeAttribute("src");
      preview3.style.display = "none";
    }
  }

  document.getElementById("editProductModal").style.display = "flex";
}

document.getElementById("cerrarEditProductModal").addEventListener("click", () => {
  document.getElementById("editProductModal").style.display = "none";
});

document.getElementById("editProductForm").addEventListener("submit", function (event) {
  event.preventDefault();

  const id = document.getElementById("editProductId").value.trim();
  const name = document.getElementById("editName").value.trim();
  const categoria = document.getElementById("editCategoria").value;
  const description = document.getElementById("editDescription").value.trim();
  const price = document.getElementById("editPrice").value.trim();
  const imageUrl = document.getElementById("editImageUrl").value.trim();
  const empresa = document.getElementById("editEmpresa").value.trim();
  const telefono = document.getElementById("editTelefono").value.trim();
  const correo = document.getElementById("editCorreoProducto").value.trim();
  const provincia = document.getElementById("editProvincia").value;
  const ciudad = document.getElementById("editCiudad").value.trim();

  // 🆕 Opcionales: precio anterior (rebaja) e imágenes adicionales 2 y 3.
  // Si el vendedor los deja vacíos, el backend borra lo que hubiera antes.
  const editPrecioAnteriorInput = document.getElementById("editPrecioAnterior");
  const precioAnterior = editPrecioAnteriorInput ? editPrecioAnteriorInput.value.trim() : "";
  const editImageUrl2Input = document.getElementById("editImageUrl2");
  const imageUrl2 = editImageUrl2Input ? editImageUrl2Input.value.trim() : "";
  const editImageUrl3Input = document.getElementById("editImageUrl3");
  const imageUrl3 = editImageUrl3Input ? editImageUrl3Input.value.trim() : "";

  // 🆕 El backend ahora exige usuario_id para verificar que el producto
  // le pertenece a quien intenta editarlo (mismo dueño que lo publicó),
  // igual que ya se hace para "Agregar Producto".
  const idTextEdit = document.getElementById("usuarioIdVisibleMitienda").textContent.trim();
  const usuarioIdEdit = idTextEdit.replace("ID de usuario:", "").trim();

  if (!id) {
    alert("No se pudo identificar el producto a editar. Cierre el modal e intente de nuevo.");
    return;
  }
  if (!usuarioIdEdit || isNaN(usuarioIdEdit)) {
    alert("No se pudo obtener el ID del usuario. Vuelva a iniciar sesión.");
    return;
  }
  if (!empresa || empresa.length < 2) {
    alert("Por favor, ingrese un nombre válido para su empresa (mínimo 2 caracteres).");
    return;
  }
  if (!correo) {
    alert("Por favor, ingrese un correo electrónico válido.");
    return;
  }
  const correoRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!correoRegex.test(correo)) {
    alert("El correo ingresado no tiene un formato válido.");
    return;
  }
  if (!price || isNaN(price) || Number(price) <= 0) {
    alert("Por favor, ingrese un precio válido mayor que 0.");
    return;
  }
  if (!imageUrl) {
    alert("Por favor, ingrese o suba una imagen del producto.");
    return;
  }

  fetch("/EditarProducto", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      id: id,
      usuario_id: usuarioIdEdit,
      nombre: name,
      categoria: categoria,
      descripcion: description,
      precio: price,
      imagen: imageUrl,
      empresa: empresa,
      telefono: telefono,
      correo: correo,
      provincia: provincia,
      ciudad: ciudad,
      precio_anterior: precioAnterior,
      imagen2: imageUrl2,
      imagen3: imageUrl3,
    }),
  })
    .then((response) => {
      if (!response.ok) {
        return response.text().then((msg) => { throw new Error("Error del servidor: " + msg); });
      }
      cargarProductosMiTienda();
      alert("Producto actualizado con éxito.");
      document.getElementById("editProductModal").style.display = "none";
      document.getElementById("editProductForm").reset();
      productoSeleccionado = null;
    })
    .catch((error) => {
      alert("Error al conectar con el servidor: " + error.message);
    });
});

// 🆕 "Eliminar producto": mismo cambio que "Editar producto" — ahora es
// el ícono de tacho de cada tarjeta en vez de un botón único arriba.
function eliminarProductoSeleccionado() {
  if (!productoSeleccionado) {
    alert("Seleccione un producto para eliminar.");
    return;
  }
  if (!confirm(`¿Desea eliminar el producto "${productoSeleccionado.nombre}" con descripción "${productoSeleccionado.descripcion}"?`)) return;

  fetch("/EliminarProducto", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ id: productoSeleccionado.id })
  })
  .then(response => {
    if (response.ok) {
      alert("Producto eliminado con éxito.");
      const cedula = document.getElementById("miTiendaCedula").value;
      cargarProductosMiTienda(cedula);
      productoSeleccionado = null;
    } else {
      alert("No se pudo eliminar el producto.");
    }
  })
  .catch(err => console.error(err));
}

const rolesContainer = document.getElementById("rolesContainer");
const formularioComprador = document.getElementById("formularioComprador");
const registroVendedorUnificado = document.getElementById("registroVendedorUnificado");
const usuarioIdVisible = document.getElementById("usuarioIdVisible");
const contenidoVendedorRegistrado = document.getElementById("contenidoVendedorRegistrado");

const pasoUsuario = document.getElementById("pasoUsuario");
const pasoSolicitud = document.getElementById("pasoSolicitud");
const pasoSuscripcion = document.getElementById("pasoSuscripcion");

const btnVendedor = document.getElementById("btnVendedor");
const btnComprador = document.getElementById("btnComprador");
const volverRolesBtn = document.getElementById("volverRolesBtn");
const btnAtrasVendedor = document.getElementById("btnAtrasVendedor");
const btnAtrasComprador = document.getElementById("btnAtrasComprador");

const btnSiguienteUsuario = document.getElementById("btnSiguienteUsuario");
const btnSiguienteSolicitud = document.getElementById("btnSiguienteSolicitud");
const btnAtrasSolicitud = document.getElementById("btnAtrasSolicitud");
const btnAtrasSuscripcion = document.getElementById("btnAtrasSuscripcion");

let usuarioId = null;

function openVendedorFlow() {
  rolesContainer.style.display = "none";
  registroVendedorUnificado.style.display = "block";
  pasoUsuario.style.display = "block";
}

function openCompradorFlow() {
  rolesContainer.style.display = "none";
  formularioComprador.style.display = "block";
}

function closeRegistroModal() {
  const registroModal = document.getElementById("registroModal");
  if (registroModal) registroModal.style.display = "none";
  const loginModal = document.getElementById("loginModal");
  if (loginModal) loginModal.style.display = "block";
}

function backFromVendedor() {
  registroVendedorUnificado.style.display = "none";
  rolesContainer.style.display = "block";
}

function backFromComprador() {
  formularioComprador.style.display = "none";
  rolesContainer.style.display = "block";
}

btnVendedor?.addEventListener("click", openVendedorFlow);
btnComprador?.addEventListener("click", openCompradorFlow);
volverRolesBtn?.addEventListener("click", closeRegistroModal);
btnAtrasVendedor?.addEventListener("click", backFromVendedor);
btnAtrasComprador?.addEventListener("click", backFromComprador);
btnAtrasSolicitud?.addEventListener("click", backFromSolicitud);
btnAtrasSuscripcion?.addEventListener("click", backFromSuscripcion);

volverRolesBtn?.addEventListener("click", () => {
  const registroModal = document.getElementById("registroModal");
  if (registroModal) registroModal.style.display = "none";
  rolesContainer.style.display = "none";
  registroVendedorUnificado.style.display = "none";
  formularioComprador.style.display = "none";
  const loginModal = document.getElementById("loginModal");
  if (loginModal) loginModal.style.display = "block";
});

function backFromSolicitud() {
  pasoSolicitud.style.display = "none";
  pasoUsuario.style.display = "block";
}

function backFromSuscripcion() {
  pasoSuscripcion.style.display = "none";
  pasoSolicitud.style.display = "block";
}

btnSiguienteUsuario?.addEventListener("click", async () => {
  const nombre = document.getElementById("nombreV").value;
  const correo = document.getElementById("correoV").value;
  const contraseña = document.getElementById("contraseñaV").value;
  if (!nombre || !correo || !contraseña) return alert("Complete todos los campos.");
  try {
    const formData = new URLSearchParams({ nombre, correo, contraseña });
    const res = await fetch("/registroVendedor", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: formData.toString()
    });
    const data = await res.json();

    // 🔧 Antes se avanzaba de paso sin revisar si el registro realmente
    // funcionó. Si el correo ya existía, el servidor respondía con
    // usuarioId -1 (o -2/-3/-4 para otros errores) y el flujo seguía de
    // todas formas, arrastrando ese id inválido a /guardarSolicitud y
    // /guardarSuscripcion, donde rompía con 500 por violar la llave
    // foránea. Ahora se valida antes de continuar.
    if (!res.ok || !data.usuarioId || data.usuarioId <= 0) {
      alert(data.mensaje || "No se pudo registrar el usuario.");
      return;
    }

    usuarioId = data.usuarioId;
    usuarioIdVisible.textContent = "ID de usuario: " + usuarioId;
    pasoUsuario.style.display = "none";
    pasoSolicitud.style.display = "block";
  } catch (err) {
    alert("Error al guardar usuario: " + err.message);
  }
});

btnSiguienteSolicitud?.addEventListener("click", async () => {
  const provincia = pasoSolicitud.querySelector("[name=provincia]").value;
  const canton = pasoSolicitud.querySelector("[name=canton]").value;
  const descripcion = pasoSolicitud.querySelector("[name=descripcion]").value;
  const precio_promedio = pasoSolicitud.querySelector("[name=precio_promedio]").value;
  const telefono = pasoSolicitud.querySelector("[name=telefono]").value;
  if (!provincia || !canton || !descripcion) return alert("Complete todos los campos.");
  const usuarioIdFromDiv = parseInt(usuarioIdVisible.textContent.replace("ID de usuario: ", "").trim());
  if (!usuarioIdFromDiv) return alert("Usuario no registrado.");
  try {
    const solicitudData = new URLSearchParams({ provincia, canton, descripcion, precio_promedio, telefono, usuario_id: usuarioIdFromDiv });
    await fetch("/guardarSolicitud", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: solicitudData.toString()
    });
    document.getElementById("loginModal").style.display = "none";
    rolesContainer.style.display = "none";
    pasoUsuario.style.display = "none";
    pasoSolicitud.style.display = "none";
    pasoSuscripcion.style.display = "block";
  } catch (err) {
    alert("Error al guardar solicitud");
  }
});

document.getElementById("btnFinalizarRegistro")?.addEventListener("click", async () => {
  const usuarioIdFromDiv = parseInt(usuarioIdVisible.textContent.replace("ID de usuario: ", "").trim());
  if (!usuarioIdFromDiv) return alert("Usuario no registrado.");
  const tipoSuscripcionInput = pasoSuscripcion.querySelector('input[name="suscripcion"]:checked');
  const metodoPago = document.getElementById("metodoPago").value;
  const cedula = document.getElementById("cedula").value.trim();
  if (!tipoSuscripcionInput) return alert("Seleccione un tipo de suscripción.");
  if (!metodoPago) return alert("Seleccione un método de pago.");
  if (!cedula) return alert("Ingrese su cédula.");
  try {
    const suscripcionData = new URLSearchParams({
      usuario_id: usuarioIdFromDiv,
      suscripcion: tipoSuscripcionInput.value,
      metodoPago: metodoPago,
      cedula: cedula
    });
    const res = await fetch("/guardarSuscripcion", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: suscripcionData.toString()
    });
    if (!res.ok) throw new Error("El servidor respondió con estado " + res.status);
    alert("¡Registro completado! Su solicitud como vendedor quedó pendiente de aprobación.");
    ["registroModal", "loginModal", "rolesContainer", "registroVendedorUnificado", "pasoUsuario", "pasoSolicitud", "pasoSuscripcion"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.style.display = "none";
    });
    const contenidoPrincipal = document.getElementById("contenidoVendedorRegistrado");
    if (contenidoPrincipal) contenidoPrincipal.style.display = "block";
    const heroBanner = document.getElementById("heroBanner");
    if (heroBanner) heroBanner.style.display = "none";
  } catch (err) {
    alert("Error al guardar la suscripción: " + err.message);
  }
});

const registroFormComprador = document.getElementById("registroFormComprador");

registroFormComprador?.addEventListener("submit", async (e) => {
  e.preventDefault();
  const nombre = document.getElementById("nombreC").value;
  const correo = document.getElementById("correoC").value;
  const contraseña = document.getElementById("contraseñaC").value;
  if (!nombre || !correo || !contraseña) { alert("Complete todos los campos."); return; }
  try {
    const formData = new URLSearchParams({ nombre, correo, contraseña });
    const res = await fetch("/registroComprador", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: formData.toString()
    });
    if (!res.ok) {
      const msg = await res.text();
      alert(res.status === 409 ? msg : "Error al registrar comprador: " + msg);
      return;
    }
    const usuarioId = await res.text();
    if (!usuarioId || parseInt(usuarioId) <= 0) throw new Error("No se pudo registrar el usuario");
    usuarioIdVisible.textContent = "ID de usuario: " + usuarioId;
    // 🔧 Antes el alert mostraba el ID de usuario ("...ID: " + usuarioId),
    // exponiendo un dato interno innecesario para el comprador. El ID
    // ya se guarda en el DOM/sessionStorage para las peticiones; el
    // usuario solo necesita saber que el registro fue exitoso.
    alert("¡Registro exitoso! Ya puedes empezar a comprar.");
    ["registroModal", "loginModal", "formularioComprador", "rolesContainer", "pasoUsuario", "pasoSolicitud", "pasoSuscripcion"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.style.display = "none";
    });
    const contenidoPrincipal =
      document.getElementById("contenidoCompradorRegistrado") ||
      document.getElementById("contenidoPrincipal") ||
      document.getElementById("contenidoVendedorRegistrado");
    if (contenidoPrincipal) contenidoPrincipal.style.display = "block";
  } catch (err) {
    alert("Error al conectar con el servidor: " + err.message);
  }
});

const loginModal = document.getElementById("loginModal");
const modalVendedor = document.getElementById("modalVendedor");
const modalComprador = document.getElementById("modalComprador");
const registroModal = document.getElementById("registroModal");

const cancelarBtn = document.getElementById("cancelarBtn");
const continuarBtn = document.getElementById("continuarBtn");
const registrarseBtn = document.getElementById("registrarseBtn");

const btnInvitado = document.querySelector(".role-btn.invitado");

const btnVolverVendedor = modalVendedor?.querySelector(".volver-btn");
const btnVolverComprador = modalComprador?.querySelector(".volver-btn");
const volverRegistroBtn = registroModal?.querySelector(".volver-btn");

const usuarioIdVisibleEl = document.getElementById("usuarioIdVisible");

function setUsuarioId(id) {
  if(!id) return;
  if(usuarioIdVisibleEl) {
    usuarioIdVisibleEl.textContent = id;
    usuarioIdVisibleEl.style.display = "block";
  }
  sessionStorage.setItem("usuarioId", String(id));
}

function getUsuarioId() {
  const fromEl = usuarioIdVisibleEl?.textContent?.trim();
  if(fromEl && /^\d+$/.test(fromEl)) return fromEl;
  const fromSession = sessionStorage.getItem("usuarioId");
  if(fromSession && /^\d+$/.test(fromSession)) return fromSession;
  return null;
}

function showModal(modal) { if(modal){ modal.style.display = "flex"; document.body.classList.add("modal-open"); } }
function hideModal(modal) { if(modal){ modal.style.display = "none"; document.body.classList.remove("modal-open"); } }
window.showModal = showModal;
window.hideModal = hideModal;

function ocultarTodo() {
  ["contenidoInvitado","contenidoCompradorRegistrado","contenidoVendedorRegistrado"].forEach(id=>{
    const el = document.getElementById(id); if(el) el.style.display="none";
  });
}

function cancelarFormulario(idFormulario, idForm) {
  if(confirm("¿Desea borrar la información escrita?")) {
    document.getElementById(idForm)?.reset();
    const f = document.getElementById(idFormulario);
    if(f) f.style.display = "none";
    document.getElementById("rolesContainer")?.style.setProperty("display","flex");
  }
}

window.addEventListener("DOMContentLoaded", () => {
  const usuarioIdVisible = document.getElementById("usuarioIdVisible");
  const texto = usuarioIdVisible?.textContent?.trim() || "";
  const tieneId = /\d+/.test(texto);
  if (!tieneId && loginModal) {
    loginModal.style.display = "flex";
  }
});

cancelarBtn?.addEventListener("click", () => hideModal(loginModal));
// 🔧 Se quitó el alert("Has continuado con tu selección"): no aportaba
// información útil, solo interrumpía con un clic extra.
continuarBtn?.addEventListener("click", () => { hideModal(loginModal); });

registrarseBtn?.addEventListener("click", () => {
  hideModal(loginModal);
  showModal(registroModal);
  document.getElementById("formularioComprador")?.style.setProperty("display","none");
  document.getElementById("rolesContainer")?.style.setProperty("display","flex");
});

volverRegistroBtn?.addEventListener("click", () => {
  hideModal(registroModal);
  showModal(loginModal);
  document.getElementById("formularioComprador")?.style.setProperty("display","none");
  document.getElementById("rolesContainer")?.style.setProperty("display","flex");
});

btnVendedor?.addEventListener("click", () => { hideModal(loginModal); showModal(modalVendedor); });
btnComprador?.addEventListener("click", () => { hideModal(loginModal); showModal(modalComprador); });
btnInvitado?.addEventListener("click", () => { ocultarTodo(); hideModal(loginModal); document.getElementById("contenidoInvitado")?.style.setProperty("display","block"); actualizarMiTienda("invitado"); });

btnVolverVendedor?.addEventListener("click", () => { hideModal(modalVendedor); showModal(loginModal); });
btnVolverComprador?.addEventListener("click", () => { hideModal(modalComprador); showModal(loginModal); });

document.getElementById("btnCancelar")?.addEventListener("click",()=>cancelarFormulario("formularioComprador","registroForm"));
document.getElementById("btnCancelarVendedor")?.addEventListener("click",()=>cancelarFormulario("formularioVendedor","registroFormVendedor"));
document.getElementById("btnCancelarSolicitud")?.addEventListener("click",()=>cancelarFormulario("formularioSolicitud","solicitudForm"));
document.getElementById("btnCancelarSuscripcion")?.addEventListener("click",()=>cancelarFormulario("formularioSuscripcion","suscripcionForm"));

function actualizarMiTienda(usuario) {
  const heroBanner = document.getElementById("heroBanner");
  if (heroBanner) {
    const esVendedor = typeof usuario === "string" && usuario.startsWith("vendedor");
    heroBanner.style.display = esVendedor ? "none" : "";
  }
  const heroBannerVendedor = document.getElementById("heroBannerVendedor");
  if (heroBannerVendedor) {
    const esVendedor = typeof usuario === "string" && usuario.startsWith("vendedor");
    heroBannerVendedor.style.display = esVendedor ? "" : "none";
  }
  if (typeof window.resetNotifBanner === "function") window.resetNotifBanner();
  const miTiendaBtn = document.querySelector('.filter[data-category="Mi tienda"]');
  if (!miTiendaBtn) return;
  const candado = miTiendaBtn.querySelector(".candado-mi-tienda");
  const puedeEntrar = usuario === "vendedor" || usuario === "vendedor-recién-registrado";
  // 🆕 Antes se usaba `disabled = true`, pero un botón deshabilitado no
  // recibe clics en absoluto — así que a un invitado/comprador nunca se
  // le explicaba por qué "Mi tienda" no estaba disponible. Ahora sigue
  // siendo clickeable; el candado 🔒 y el aviso los maneja el listener
  // de ".filter" (ver script.js, bloque "Mi tienda bloqueada").
  miTiendaBtn.dataset.bloqueado = puedeEntrar ? "false" : "true";
  miTiendaBtn.style.opacity = puedeEntrar ? 1 : 0.6;
  miTiendaBtn.title = puedeEntrar ? "" : "Exclusivo para vendedores de Tienda Monjarrez";
  if (candado) candado.style.display = puedeEntrar ? "none" : "inline-block";
}
window.actualizarMiTienda = actualizarMiTienda;

document.getElementById("btnAccederVendedor")?.addEventListener("click", function () {
  const cedula = document.getElementById("cedulaVendedor").value;
  const contraseña = document.getElementById("passwordVendedor").value;
  fetch("LoginVendedorServlet", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `cedula=${encodeURIComponent(cedula)}&contraseña=${encodeURIComponent(contraseña)}`
  })
  .then(response => {
    if (response.ok) {
      // 🔧 Se quitó el alert("Inicio de sesión exitoso"): la pantalla ya
      // cambia inmediatamente al panel del vendedor, así que el mensaje
      // era un clic extra innecesario. Los errores sí se siguen
      // mostrando (ver el .catch de abajo).
      ocultarTodo();
      document.getElementById("modalVendedor")?.style.setProperty("display", "none");
      actualizarMiTienda("vendedor");
      iniciarSesion("vendedor");
    } else {
      return response.text().then(text => { throw new Error(text); });
    }
  })
  .catch(error => { alert(error.message); });
  document.getElementById("olvideVendedor").style.display = "block";
});

document.getElementById("btnAccederComprador")?.addEventListener("click", function () {
  const correo = document.getElementById("correoComprador").value;
  const contraseña = document.getElementById("passwordComprador").value;
  fetch("LoginCompradorServlet", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `correo=${encodeURIComponent(correo)}&contraseña=${encodeURIComponent(contraseña)}`
  })
  .then(response => {
    if (response.ok) {
      // 🔧 Se quitó el alert("Inicio de sesión exitoso") por la misma
      // razón que en el login de vendedor: la pantalla ya refleja el
      // cambio al instante.
      ocultarTodo();
      document.getElementById("modalComprador")?.style.setProperty("display", "none");
      document.getElementById("contenidoCompradorActivo").style.display = "block";
      actualizarMiTienda("comprador");
      iniciarSesion("comprador");
    } else {
      return response.text().then(text => { throw new Error(text); });
    }
  })
  .catch(error => { alert(error.message); });
  document.getElementById("olvideComprador").style.display = "block";
});

const metodoPago = document.getElementById('metodoPago');
const tarjetaCampos = document.getElementById('tarjetaCampos');
const numeroSimpe = document.getElementById('numeroSimpe');
const depositoCampos = document.getElementById('depositoCampos');
const contenedor = document.getElementById('contenedorPagoExtra');

metodoPago.addEventListener('change', function () {
  tarjetaCampos.classList.add('oculto');
  numeroSimpe.classList.add('oculto');
  depositoCampos.classList.add('oculto');
  contenedor.style.height = contenedor.scrollHeight + 'px';
  setTimeout(() => {
    let nuevoContenido;
    if (this.value === 'tarjeta') { tarjetaCampos.classList.remove('oculto'); nuevoContenido = tarjetaCampos; }
    else if (this.value === 'simpe') { numeroSimpe.classList.remove('oculto'); nuevoContenido = numeroSimpe; }
    else if (this.value === 'deposito') { depositoCampos.classList.remove('oculto'); nuevoContenido = depositoCampos; }
    setTimeout(() => {
      const nuevaAltura = nuevoContenido ? nuevoContenido.scrollHeight + 20 : 0;
      contenedor.style.height = nuevaAltura + 'px';
    }, 50);
  }, 50);
});

const radiosSuscripcion = document.querySelectorAll('input[name="suscripcion"]');
const divBasica = document.getElementById('caracteristicasBasica');
const divAvanzada = document.getElementById('caracteristicasAvanzada');

radiosSuscripcion.forEach(radio => {
  radio.addEventListener('change', function () {
    if (this.value === 'Básica') { divBasica.style.display = 'block'; divAvanzada.style.display = 'none'; }
    else if (this.value === 'Avanzada') { divBasica.style.display = 'none'; divAvanzada.style.display = 'block'; }
  });
});

const solicitudFormEl = document.getElementById('solicitudForm');
if (solicitudFormEl) {
  solicitudFormEl.addEventListener('submit', function(e) {
    e.preventDefault();
    const formData = new FormData(this);
    fetch('/guardarSolicitud', { method: 'POST', body: formData })
    .then(resp => { if (!resp.ok) throw resp; return resp.text(); })
    .then(data => alert(data))
    .catch(async err => { const msg = await err.text(); alert('Error: ' + msg); });
  });
}

const productGridWrapper = document.getElementById('productGridWrapper');
const misProductosGrid = document.getElementById('misProductosGrid');
function sincronizarFondoWrapper(color) {
  if (productGridWrapper) productGridWrapper.style.backgroundColor = color;
}

const starsCanvas = document.getElementById('starsCanvas');
const starThemeBtn = document.getElementById('starThemeBtn');
let starsCtx, starParticles = [], starsAnimId;

function resizeStars() {
  if (!starsCanvas) return;
  const wrapper = starsCanvas.parentElement;
  const anchoCss = wrapper ? wrapper.clientWidth : window.innerWidth;
  const altoCss = wrapper ? wrapper.clientHeight : window.innerHeight;
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  starsCanvas.width = anchoCss * dpr;
  starsCanvas.height = altoCss * dpr;
  starsCanvas.style.width = anchoCss + "px";
  starsCanvas.style.height = altoCss + "px";
  starsCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
  starParticles = Array.from({ length: 90 }, () => ({
    x: Math.random() * anchoCss, y: Math.random() * altoCss,
    r: Math.random() * 1.4 + 0.3, a: Math.random(), speed: Math.random() * 0.015 + 0.003
  }));
}

function drawStars() {
  if (!document.body.classList.contains('theme-stars')) return;
  const anchoCss = parseFloat(starsCanvas.style.width) || starsCanvas.width;
  const altoCss = parseFloat(starsCanvas.style.height) || starsCanvas.height;
  starsCtx.clearRect(0, 0, anchoCss, altoCss);
  starParticles.forEach(s => {
    s.a += s.speed;
    const twinkle = (Math.sin(s.a * Math.PI * 2) + 1) / 2;
    starsCtx.beginPath();
    starsCtx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
    starsCtx.fillStyle = `rgba(255,255,255,${0.15 + twinkle * 0.7})`;
    starsCtx.fill();
  });
  starsAnimId = requestAnimationFrame(drawStars);
}

if (starsCanvas) {
  starsCtx = starsCanvas.getContext('2d');
  window.addEventListener('resize', resizeStars);
}

function activarEstrellado(guardar = true) {
  desactivarRosado(false);
  document.body.classList.add('theme-stars');
  if (starThemeBtn) starThemeBtn.classList.add('active');
  if (productGrid) { productGrid.style.backgroundColor = 'transparent'; productGrid.style.color = '#fff'; }
  if (misProductosGrid) {
    misProductosGrid.style.backgroundColor = '';
    misProductosGrid.style.backgroundImage =
      'radial-gradient(ellipse at 50% 15%, #2a1e52 0%, transparent 55%), linear-gradient(180deg, #080b24 0%, #151a4d 55%, #080b24 100%)';
    misProductosGrid.style.color = '#fff';
  }
  sincronizarFondoWrapper('transparent');
  resizeStars();
  drawStars();
  if (guardar) guardarTema('estrellado');
}

function desactivarEstrellado(restaurar = true) {
  if (document.body.classList.contains('theme-stars')) {
    document.body.classList.remove('theme-stars');
    if (starThemeBtn) starThemeBtn.classList.remove('active');
    cancelAnimationFrame(starsAnimId);
    if (restaurar && productGrid) aplicarColorGrid(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
  }
}

const pinkThemeBtn = document.getElementById('pinkThemeBtn');

function activarRosado(guardar = true) {
  desactivarEstrellado(false);
  document.body.classList.add('theme-pink');
  if (pinkThemeBtn) pinkThemeBtn.classList.add('active');
  if (productGrid) { productGrid.style.backgroundColor = ''; productGrid.style.color = ''; }
  if (misProductosGrid) {
    misProductosGrid.style.backgroundImage = 'linear-gradient(160deg, #ffeef3 0%, #ffe0ea 100%)';
    misProductosGrid.style.color = '#5a2f43';
  }
  sincronizarFondoWrapper('#ffeef3');
  if (guardar) guardarTema('rosado');
}

function desactivarRosado(restaurar = true) {
  if (document.body.classList.contains('theme-pink')) {
    document.body.classList.remove('theme-pink');
    if (pinkThemeBtn) pinkThemeBtn.classList.remove('active');
    if (restaurar && productGrid) aplicarColorGrid(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
  }
}

if (starThemeBtn) {
  starThemeBtn.addEventListener('click', () => {
    if (document.body.classList.contains('theme-stars')) {
      desactivarEstrellado();
      guardarTema(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
    } else {
      activarEstrellado();
    }
  });
}

if (pinkThemeBtn) {
  pinkThemeBtn.addEventListener('click', () => {
    if (document.body.classList.contains('theme-pink')) {
      desactivarRosado();
      guardarTema(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
    } else {
      activarRosado();
    }
  });
}

function aplicarColorGrid(modo) {
  if (!productGrid) return;
  if (modo === 'claro') {
    productGrid.style.backgroundColor = '#f0f0f0';
    productGrid.style.color = '#000';
    sincronizarFondoWrapper('#f0f0f0');
    if (misProductosGrid) { misProductosGrid.style.backgroundImage = ''; misProductosGrid.style.backgroundColor = '#f0f0f0'; misProductosGrid.style.color = '#000'; }
  } else {
    productGrid.style.backgroundColor = 'black';
    productGrid.style.color = '#fff';
    sincronizarFondoWrapper('black');
    if (misProductosGrid) { misProductosGrid.style.backgroundImage = ''; misProductosGrid.style.backgroundColor = 'black'; misProductosGrid.style.color = '#fff'; }
  }
}

function guardarTema(nombre) { localStorage.setItem('temaTienda', nombre); }

function activarClaro(guardar = true) {
  desactivarEstrellado(false);
  desactivarRosado(false);
  themeToggle.checked = true;
  aplicarColorGrid('claro');
  if (guardar) guardarTema('claro');
}

function activarOscuro(guardar = true) {
  desactivarEstrellado(false);
  desactivarRosado(false);
  themeToggle.checked = false;
  aplicarColorGrid('oscuro');
  if (guardar) guardarTema('oscuro');
}

(function restaurarTemaGuardado() {
  // 🔧 Antes se recordaba el último tema elegido (localStorage). Ahora
  // la página siempre arranca en claro, sin importar lo que el usuario
  // haya elegido en una visita anterior. El switch de Tema sigue
  // funcionando con normalidad durante la sesión, solo que ya no se
  // guarda para la próxima carga.
  activarClaro(false);
})();

const welcomeContainer = document.querySelector('.welcome-container');
if (welcomeContainer) {
  setTimeout(() => {
    // 🔧 Antes se pasaba de max-height "auto" a "0" directamente: como no
    // había un valor numérico de partida, el navegador no podía animarlo
    // y el espacio se colapsaba de golpe (los productos de abajo subían
    // de un salto aunque la opacidad sí se veía desvanecer). Ahora se fija
    // primero el alto real en píxeles y se fuerza un reflow antes de
    // animar a 0, para que el colapso sea progresivo.
    welcomeContainer.style.overflow = 'hidden';
    welcomeContainer.style.maxHeight = welcomeContainer.scrollHeight + 'px';
    void welcomeContainer.offsetHeight;
    welcomeContainer.style.transition = 'opacity 0.6s ease, max-height 0.6s ease, margin 0.6s ease, padding 0.6s ease';
    welcomeContainer.style.opacity = '0';
    welcomeContainer.style.maxHeight = '0';
    welcomeContainer.style.margin = '0';
    welcomeContainer.style.padding = '0';
    setTimeout(() => { welcomeContainer.style.display = 'none'; }, 650);
  },25000);
}

});

(function () {
  const btnAbrir = document.getElementById("btnAbrirPerfil");
  const panel = document.getElementById("perfilPanel");
  const overlay = document.getElementById("perfilOverlay");
  const btnCerrar = document.getElementById("cerrarPerfilBtn");
  const cargando = document.getElementById("perfilCargando");
  const datos = document.getElementById("perfilDatos");
  const badge = document.getElementById("perfilBadge");
  const inputNombre = document.getElementById("perfilNombre");
  const inputCorreo = document.getElementById("perfilCorreo");
  const divTipo = document.getElementById("perfilTipo");
  const planWrap = document.getElementById("perfilPlanWrap");
  const divPlan = document.getElementById("perfilPlan");
  const mensaje = document.getElementById("perfilMensaje");
  const btnGuardar = document.getElementById("perfilGuardarBtn");
  const btnBorrar = document.getElementById("perfilBorrarCuentaBtn");

  const checksInteres = {
    Damas: document.getElementById("interesDamas"),
    Caballeros: document.getElementById("interesCaballeros"),
    "Niños": document.getElementById("interesNinos"),
    Servicios: document.getElementById("interesServicios"),
  };

  const checksProvincia = {
    "San José": document.getElementById("provSanJose"),
    Alajuela: document.getElementById("provAlajuela"),
    Cartago: document.getElementById("provCartago"),
    Heredia: document.getElementById("provHeredia"),
    Guanacaste: document.getElementById("provGuanacaste"),
    Puntarenas: document.getElementById("provPuntarenas"),
    "Limón": document.getElementById("provLimon"),
  };

  if (!btnAbrir || !panel || !overlay) return;

  function abrirPanel() {
    const infoModal = document.getElementById("infoModal");
    const sideOverlay = document.getElementById("sideOverlay");
    if (infoModal && infoModal.classList.contains("open")) {
      infoModal.classList.remove("open");
      infoModal.setAttribute("aria-hidden", "true");
    }
    if (sideOverlay && sideOverlay.classList.contains("visible")) {
      sideOverlay.classList.remove("visible");
      sideOverlay.setAttribute("aria-hidden", "true");
    }
    panel.classList.add("open");
    panel.setAttribute("aria-hidden", "false");
    overlay.classList.add("visible");
    overlay.setAttribute("aria-hidden", "false");
  }

  function cerrarPanel() {
    panel.classList.remove("open");
    panel.setAttribute("aria-hidden", "true");
    overlay.classList.remove("visible");
    overlay.setAttribute("aria-hidden", "true");
  }

  function mostrarMensaje(texto, tipo) {
    mensaje.textContent = texto;
    mensaje.style.color = tipo === "error" ? "#d32f2f" : "#2e7d32";
    mensaje.style.display = "block";
  }

  async function cargarPerfil() {
    cargando.style.display = "block";
    datos.style.display = "none";
    mensaje.style.display = "none";
    try {
      const res = await fetch("/api/perfil");
      if (!res.ok) { cargando.textContent = "Debes iniciar sesión para ver tu perfil."; return; }
      const perfil = await res.json();
      inputNombre.value = perfil.nombre || "";
      inputCorreo.value = perfil.correo || "";
      divTipo.textContent = perfil.tipo || "—";
      badge.style.display = perfil.esDestacado ? "inline-block" : "none";
      if (perfil.tipo && perfil.tipo.toLowerCase() === "vendedor" && perfil.tipoSuscripcion) {
        divPlan.textContent = perfil.tipoSuscripcion + (perfil.suscrito ? "" : " (inactiva)");
        planWrap.style.display = "block";
      } else {
        planWrap.style.display = "none";
      }
      const interesesGuardados = Array.isArray(perfil.intereses) ? perfil.intereses : [];
      Object.entries(checksInteres).forEach(([categoria, checkbox]) => {
        if (checkbox) checkbox.checked = interesesGuardados.includes(categoria);
      });
      const provinciasGuardadas = Array.isArray(perfil.provincias) ? perfil.provincias : [];
      Object.entries(checksProvincia).forEach(([provincia, checkbox]) => {
        if (checkbox) checkbox.checked = provinciasGuardadas.includes(provincia);
      });
      cargando.style.display = "none";
      datos.style.display = "block";
    } catch (err) {
      cargando.textContent = "No se pudo cargar el perfil. Intenta de nuevo.";
    }
  }

  btnAbrir.addEventListener("click", () => {
    const menu = document.getElementById("menuContent");
    if (menu) menu.style.display = "none";
    abrirPanel();
    cargarPerfil();
  });

  if (btnCerrar) btnCerrar.addEventListener("click", cerrarPanel);
  overlay.addEventListener("click", cerrarPanel);

  if (btnGuardar) {
    btnGuardar.addEventListener("click", async () => {
      const nuevoNombre = inputNombre.value.trim();
      const nuevoCorreo = inputCorreo.value.trim();
      if (!nuevoNombre) return mostrarMensaje("El nombre no puede estar vacío.", "error");
      if (!nuevoCorreo || !nuevoCorreo.includes("@")) return mostrarMensaje("Ingresa un correo válido.", "error");

      const interesesSeleccionados = Object.entries(checksInteres)
        .filter(([, checkbox]) => checkbox && checkbox.checked)
        .map(([categoria]) => categoria);
      const provinciasSeleccionadas = Object.entries(checksProvincia)
        .filter(([, checkbox]) => checkbox && checkbox.checked)
        .map(([provincia]) => provincia);
      const textoIntereses = interesesSeleccionados.length ? interesesSeleccionados.join(", ") : "Ninguna";
      const textoProvincias = provinciasSeleccionadas.length ? provinciasSeleccionadas.join(", ") : "Ninguna (sin filtrar por zona)";

      if (!confirm(`¿Confirmas guardar estos cambios?\n\nNombre: ${nuevoNombre}\nCorreo: ${nuevoCorreo}\nCategorías: ${textoIntereses}\nProvincias: ${textoProvincias}`)) return;

      try {
        const res = await fetch("/api/perfil/actualizar", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            nombre: nuevoNombre,
            correo: nuevoCorreo,
            intereses: interesesSeleccionados.join(","),
            provincias: provinciasSeleccionadas.join(",")
          }).toString()
        });
        const texto = await res.text();
        if (res.ok) mostrarMensaje("✅ Cambios guardados correctamente.", "ok");
        else mostrarMensaje(texto || "No se pudo guardar. Intenta de nuevo.", "error");
      } catch (err) {
        mostrarMensaje("Error de conexión al guardar.", "error");
      }
    });
  }

  if (btnBorrar) {
    btnBorrar.addEventListener("click", async () => {
      if (!confirm("¿Seguro que deseas borrar tu cuenta? Esta acción no se puede deshacer.")) return;
      if (!confirm("Última confirmación: se eliminarán tu cuenta y tus datos asociados de forma permanente. ¿Continuar?")) return;
      try {
        const res = await fetch("/api/perfil/eliminar", { method: "POST" });
        if (res.ok) {
          alert("Tu cuenta fue eliminada. Serás redirigido al inicio.");
          localStorage.removeItem("sesionUsuario");
          window.location.href = "/";
        } else {
          const texto = await res.text();
          mostrarMensaje(texto || "No se pudo eliminar la cuenta.", "error");
        }
      } catch (err) {
        mostrarMensaje("Error de conexión al eliminar la cuenta.", "error");
      }
    });
  }

  const inputPassActual = document.getElementById("perfilPasswordActual");
  const inputPassNueva = document.getElementById("perfilPasswordNueva");
  const inputPassConfirmar = document.getElementById("perfilPasswordConfirmar");
  const btnCambiarPassword = document.getElementById("perfilCambiarPasswordBtn");
  const mensajePassword = document.getElementById("perfilPasswordMensaje");

  function mostrarMensajePassword(texto, tipo) {
    if (!mensajePassword) return;
    mensajePassword.textContent = texto;
    mensajePassword.style.color = tipo === "error" ? "#d32f2f" : "#2e7d32";
    mensajePassword.style.display = "block";
  }

  if (btnCambiarPassword) {
    btnCambiarPassword.addEventListener("click", async () => {
      const actual = inputPassActual.value;
      const nueva = inputPassNueva.value;
      const confirmar = inputPassConfirmar.value;
      if (!actual) return mostrarMensajePassword("Ingresa tu contraseña actual.", "error");
      if (!nueva || nueva.length < 6) return mostrarMensajePassword("La nueva contraseña debe tener al menos 6 caracteres.", "error");
      if (nueva !== confirmar) return mostrarMensajePassword("Las contraseñas nuevas no coinciden.", "error");

      btnCambiarPassword.disabled = true;
      btnCambiarPassword.textContent = "Verificando...";
      try {
        const res = await fetch("/api/perfil/cambiar-contrasena", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ actual: actual, nueva: nueva }).toString()
        });
        const texto = await res.text();
        if (res.ok) {
          mostrarMensajePassword("✅ Contraseña actualizada correctamente.", "ok");
          inputPassActual.value = "";
          inputPassNueva.value = "";
          inputPassConfirmar.value = "";
        } else {
          mostrarMensajePassword(texto || "La contraseña actual no es correcta.", "error");
        }
      } catch (err) {
        mostrarMensajePassword("Error de conexión al actualizar la contraseña.", "error");
      } finally {
        btnCambiarPassword.disabled = false;
        btnCambiarPassword.textContent = "Actualizar contraseña";
      }
    });
  }
})();

const modalRecuperar = document.getElementById("modalRecuperar");
const recuperarPaso1 = document.getElementById("recuperarPaso1");
const recuperarPaso2 = document.getElementById("recuperarPaso2");
const recuperarMensaje = document.getElementById("recuperarMensaje");
let recuperarModalOrigen = null;

function mostrarMensajeRecuperar(texto, color) {
  recuperarMensaje.textContent = texto;
  recuperarMensaje.style.color = color;
}

function abrirRecuperar(correoPrellenado, origenModal) {
  recuperarModalOrigen = origenModal;
  hideModal(origenModal);
  recuperarPaso1.style.display = "block";
  recuperarPaso2.style.display = "none";
  mostrarMensajeRecuperar("", "");
  document.getElementById("recuperarCorreo").value = correoPrellenado || "";
  document.getElementById("recuperarCodigo").value = "";
  document.getElementById("recuperarNueva").value = "";
  document.getElementById("recuperarConfirmar").value = "";
  showModal(modalRecuperar);
}

document.getElementById("olvideVendedor")?.addEventListener("click", () => {
  abrirRecuperar("", modalVendedor);
});

document.getElementById("olvideComprador")?.addEventListener("click", () => {
  const correo = document.getElementById("correoComprador").value;
  abrirRecuperar(correo, modalComprador);
});

document.getElementById("btnVolverRecuperar")?.addEventListener("click", () => {
  hideModal(modalRecuperar);
  showModal(recuperarModalOrigen || loginModal);
});

document.getElementById("btnEnviarCodigo")?.addEventListener("click", async () => {
  const correo = document.getElementById("recuperarCorreo").value.trim();
  if (!correo || !correo.includes("@")) { mostrarMensajeRecuperar("Ingresa un correo válido.", "#ff8a8a"); return; }
  const btn = document.getElementById("btnEnviarCodigo");
  btn.disabled = true;
  btn.textContent = "Enviando...";
  try {
    const res = await fetch("/api/recuperar/solicitar", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ correo }).toString()
    });
    const texto = await res.text();
    mostrarMensajeRecuperar(texto, "#8fd68f");
    recuperarPaso1.style.display = "none";
    recuperarPaso2.style.display = "block";
  } catch (err) {
    mostrarMensajeRecuperar("Error de conexión. Intenta de nuevo.", "#ff8a8a");
  } finally {
    btn.disabled = false;
    btn.textContent = "Enviar código";
  }
});

document.getElementById("btnCambiarPassword")?.addEventListener("click", async () => {
  const correo = document.getElementById("recuperarCorreo").value.trim();
  const codigo = document.getElementById("recuperarCodigo").value.trim();
  const nueva = document.getElementById("recuperarNueva").value;
  const confirmar = document.getElementById("recuperarConfirmar").value;
  if (!/^\d{6}$/.test(codigo)) { mostrarMensajeRecuperar("Ingresa el código de 6 dígitos.", "#ff8a8a"); return; }
  if (!nueva || nueva.length < 6) { mostrarMensajeRecuperar("La nueva contraseña debe tener al menos 6 caracteres.", "#ff8a8a"); return; }
  if (nueva !== confirmar) { mostrarMensajeRecuperar("Las contraseñas no coinciden.", "#ff8a8a"); return; }

  const btn = document.getElementById("btnCambiarPassword");
  btn.disabled = true;
  btn.textContent = "Verificando...";
  try {
    const res = await fetch("/api/recuperar/confirmar", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ correo, codigo, "nuevaContraseña": nueva }).toString()
    });
    const texto = await res.text();
    if (res.ok) {
      alert("Contraseña actualizada. Ya puedes iniciar sesión.");
      hideModal(modalRecuperar);
      showModal(loginModal);
    } else {
      mostrarMensajeRecuperar(texto, "#ff8a8a");
    }
  } catch (err) {
    mostrarMensajeRecuperar("Error de conexión. Intenta de nuevo.", "#ff8a8a");
  } finally {
    btn.disabled = false;
    btn.textContent = "Cambiar contraseña";
  }
});

/* 🆕 Auto-ocultar el bloque "¡Bienvenido Invitado!" pasados 25 segundos.
   Hay varios lugares del código que hacen
   contenidoInvitado.style.display = "block" (botón "Invitado" del login,
   el flujo con loader, etc.), así que en vez de tocar cada uno, se
   observa el propio elemento: cada vez que se vuelve visible, arranca
   un temporizador de 25s que lo oculta; si se oculta antes por otro
   motivo, el temporizador se cancela. */
(function () {
  const contenidoInvitado = document.getElementById("contenidoInvitado");
  if (!contenidoInvitado) return;

  let timeoutInvitado = null;
  let ocultando = false;

  // 🆕 En vez de display="none" de golpe, se anima un colapso suave
  // (opacidad + alto máximo) antes de ocultarlo, para que los productos
  // de abajo suban de forma progresiva y no de un salto brusco.
  function ocultarSuave() {
    if (ocultando) return;
    ocultando = true;

    contenidoInvitado.style.overflow = "hidden";
    contenidoInvitado.style.maxHeight = contenidoInvitado.scrollHeight + "px";
    // Fuerza al navegador a registrar el maxHeight inicial antes de animar a 0.
    void contenidoInvitado.offsetHeight;
    contenidoInvitado.style.transition =
      "opacity 0.5s ease, max-height 0.5s ease, margin 0.5s ease, padding 0.5s ease";
    contenidoInvitado.style.opacity = "0";
    contenidoInvitado.style.maxHeight = "0";
    contenidoInvitado.style.margin = "0";
    contenidoInvitado.style.padding = "0";

    setTimeout(() => {
      contenidoInvitado.style.display = "none";
      contenidoInvitado.style.removeProperty("opacity");
      contenidoInvitado.style.removeProperty("max-height");
      contenidoInvitado.style.removeProperty("margin");
      contenidoInvitado.style.removeProperty("padding");
      contenidoInvitado.style.removeProperty("overflow");
      contenidoInvitado.style.removeProperty("transition");
      ocultando = false;
    }, 520);
  }

  const observer = new MutationObserver(() => {
    const visible = contenidoInvitado.style.display === "block";

    if (visible && !timeoutInvitado && !ocultando) {
      timeoutInvitado = setTimeout(() => {
        timeoutInvitado = null;
        ocultarSuave();
      }, 25000);
    } else if (!visible && timeoutInvitado) {
      clearTimeout(timeoutInvitado);
      timeoutInvitado = null;
    }
  });

  observer.observe(contenidoInvitado, { attributes: true, attributeFilter: ["style"] });
})();