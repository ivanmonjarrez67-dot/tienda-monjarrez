document.addEventListener("DOMContentLoaded", function () {
  // Mostrar modal
  document.getElementById("addProductButton").onclick = function () {
    document.getElementById("addProductModal").style.display = "block";
  };

  // Cerrar modal
  document.querySelector(".close").onclick = function () {
    document.getElementById("addProductModal").style.display = "none";
  };



const themeToggle = document.getElementById('themeToggle');
const productGrid = document.getElementById('productGrid');

// 🆕 El listener ahora delega en las funciones centralizadas
// activarClaro()/activarOscuro() (definidas más abajo, junto con el resto
// del sistema de temas), que además se encargan de apagar Estrellado y
// Rosado suave, y de guardar el tema elegido para que quede permanente.
themeToggle.addEventListener('change', () => {
  if (themeToggle.checked) {
    activarClaro();
  } else {
    activarOscuro();
  }
});

















// Elementos del DOM
const inputFile = document.getElementById("imagenProducto"); // Input tipo file
const inputUrl = document.getElementById("imageUrl");        // Input donde se guardará la URL para la DB
const previewImg = document.getElementById("previewImagen"); // Imagen de preview

// Escuchar cuando se selecciona un archivo
inputFile.addEventListener("change", () => {
  if (inputFile.files.length === 0) return; // No hay archivo seleccionado

  const file = inputFile.files[0];

  // Mostrar preview en la página
  previewImg.src = URL.createObjectURL(file);
  previewImg.style.display = "inline-block"; // Asegurarse que se vea

  // Crear FormData solo con la imagen
  const formData = new FormData();
  formData.append("imagenProducto", file);

  // Subir archivo al servlet
  fetch("/proyecto-web/GuardarProductoArchivo", {
    method: "POST",
    body: formData
  })
  .then(resp => {
    if (!resp.ok) 
      return resp.text().then(msg => { throw new Error(msg); }); // Manejo de errores
    return resp.text();
  })
  .then(data => {
    console.log("Archivo subido con éxito:", data);

    // El servlet ahora devuelve directamente la URL pública de Cloudinary
    const imageUrl = data.trim();

    // Guardar esta URL en el input que se usará para la DB
    inputUrl.value = imageUrl;

    console.log("URL lista para la DB:", inputUrl.value);
  })
  .catch(err => {
    console.error("Error al subir la imagen:", err);
    alert("Error al subir la imagen: " + err.message);
  });
});













// ✅ Función para cargar productos de la tienda desde MisProductosServlet
function cargarProductosMiTienda() {
  // ✅ Obtener el ID visible (por ejemplo: "ID de usuario: 23")
  const idDiv = document.getElementById("usuarioIdVisibleMitienda");
  if (!idDiv) {
    console.error("No se encontró el elemento con el ID visible del usuario.");
    return;
  }

  const idText = idDiv.textContent.trim();
  const usuarioId = idText.replace("ID de usuario:", "").trim();

  // ✅ Validación
  if (!usuarioId || isNaN(usuarioId)) {
    console.error("⚠️ ID de usuario inválido o no definido:", usuarioId);
    return;
  }

  // ✅ Hacer la solicitud al servlet con el usuario_id
  fetch(`/proyecto-web/MisProductosServlet?usuario_id=${usuarioId}`)
    .then(res => {
      if (!res.ok) {
        throw new Error(`Error al obtener productos (status ${res.status})`);
      }
      return res.json();
    })
    .then(productos => {
      const grid = document.getElementById("misProductosGrid");
      if (!grid) {
        console.error("No se encontró el contenedor de productos (misProductosGrid).");
        return;
      }

      grid.innerHTML = "";
      productoSeleccionado = null;

      if (!productos || productos.length === 0) {
        grid.innerHTML = '<p style="color:white;">No tienes productos registrados.</p>';
        return;
      }

      productos.forEach(producto => {
        const card = document.createElement("div");
        card.className = "producto";

        card.innerHTML = `
          <img src="${producto.imagen || ''}" alt="${producto.nombre || ''}" style="width:150px;height:150px;object-fit:cover;border-radius:8px;">
          <h3>${producto.nombre || ''} (ID: ${producto.id})</h3>
          <button class="more-info-btn"
            data-id="${producto.id}" 
            data-empresa="${producto.empresa || ''}"
            data-imagen="${producto.imagen || ''}"
            data-nombre="${producto.nombre || ''}"
            data-descripcion="${producto.descripcion || ''}"
            data-provincia="${producto.provincia || ''}"
            data-ciudad="${producto.ciudad || ''}"
            data-telefono="${producto.telefono || ''}"
            data-correo="${producto.correo || ''}"
            data-precio="${producto.precio || ''}"
            data-categoria="${producto.categoria || ''}">
            Ver detalles
          </button>
        `;
        grid.appendChild(card);
      });
    })
    .catch(err => console.error("❌ Error al cargar productos de la tienda:", err));
}

























  const menuBtn = document.getElementById('menuBtn');
  const menuContent = document.getElementById('menuContent');

  if (menuBtn && menuContent) { // ✅ evita errores si no existen
    menuBtn.addEventListener('click', () => {
      menuContent.style.display = menuContent.style.display === 'block' ? 'none' : 'block';
    });

    // Cerrar el menú si se hace clic fuera
    window.addEventListener('click', (e) => {
      if (!menuBtn.contains(e.target) && !menuContent.contains(e.target)) {
        menuContent.style.display = 'none';
      }
    });
  }

// 🔧 mostrarAyuda() se movió aquí (fuera del if de arriba) porque estaba
// definida dentro de ese bloque y por eso no era visible globalmente cuando
// el HTML la llamaba con onclick="mostrarAyuda()" (ReferenceError: mostrarAyuda
// is not defined). Además se actualizó el mensaje para que sea consistente
// con el de "Mi Tienda pendiente de aprobación".
// 🔧 mostrarAyuda() se asigna explícitamente a window porque, aunque está
// escrita aquí, JavaScript la sigue considerando "local" a este bloque
// DOMContentLoaded que envuelve todo el archivo. Al asignarla a window,
// el onclick="mostrarAyuda()" del HTML (que corre en ámbito global) sí la encuentra.
window.mostrarAyuda = function mostrarAyuda() {
  const modal = document.getElementById("ayudaModal");
  if (modal) modal.classList.add("activo");
}

// 🆕 Cerrar el modal de ayuda: con el botón X o haciendo clic fuera de la
// tarjeta. NOTA: ya estamos dentro del DOMContentLoaded principal del
// archivo (línea 1), así que se conecta directo — envolver esto en otro
// "document.addEventListener('DOMContentLoaded', ...)" nunca se ejecutaría,
// porque ese evento ya estaría disparado para cuando se registrara.
const ayudaModal = document.getElementById("ayudaModal");
const cerrarAyuda = document.getElementById("cerrarAyudaModal");
if (cerrarAyuda) {
  cerrarAyuda.addEventListener("click", () => ayudaModal.classList.remove("activo"));
}
if (ayudaModal) {
  ayudaModal.addEventListener("click", (e) => {
    if (e.target === ayudaModal) ayudaModal.classList.remove("activo");
  });
}











// Procesar formulario
document.getElementById("addProductForm").onsubmit = function (event) {
  event.preventDefault();

  // ✅ Extraer el número del texto "ID de usuario: 23"
  const idText = document.getElementById("usuarioIdVisibleMitienda").textContent.trim();
  const usuarioId = idText.replace("ID de usuario:", "").trim(); // → "23"

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

  // ✅ Validación del ID
  if (!usuarioId || isNaN(usuarioId)) {
    alert("No se pudo obtener el ID del usuario. Vuelva a iniciar sesión o revise el campo.");
    return;
  }

  // 🆕 Validar límite de productos según tipo de suscripción
  const limite = LIMITE_PRODUCTOS[miTiendaTipoSuscripcion];
  if (limite) {
    const cantidadActual = document.querySelectorAll("#misProductosGrid .producto").length;
    if (cantidadActual >= limite) {
      alert(
        `Alcanzó el límite de ${limite} productos para su suscripción "${miTiendaTipoSuscripcion}". ` +
        `Elimine algún producto o mejore su plan para agregar más.`
      );
      return;
    }
  }

  // Validaciones
  if (!empresa || empresa.length < 2) {
    alert("Por favor, ingrese un nombre válido para su empresa (mínimo 2 caracteres).");
    return;
  }

  if (!correo) {
    alert("Por favor, ingrese un correo electrónico válido.");
    return;
  }

  // Validación simple de correo
  const correoRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!correoRegex.test(correo)) {
    alert("El correo ingresado no tiene un formato válido.");
    return;
  }

  if (!price || isNaN(price) || Number(price) <= 0) {
    alert("Por favor, ingrese un precio válido mayor que 0.");
    return;
  }

  // Log para depuración
  console.log("Correo a enviar:", correo);
  console.log("Usuario ID a enviar:", usuarioId);

  // ✅ Enviar también el usuario_id al servlet
  fetch("/proyecto-web/GuardarProducto", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      usuario_id: usuarioId, // 👈 ID extraído del campo visible
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
    }),
  })
    .then((response) => {
      if (!response.ok) {
        return response.text().then((msg) => {
          throw new Error("Error del servidor: " + msg);
        });
      }
      // 🔄 Antes se llamaba a addProductToView(...) para fabricar una tarjeta
      // "de mentira" en el navegador, pero esa tarjeta no tenía data-id, así
      // que si intentabas eliminarla justo después de agregarla, fallaba.
      // Ahora recargamos los productos reales desde el servidor, que sí
      // vienen con su id correcto.
      cargarProductosMiTienda();
      alert("Producto guardado con éxito.");
      document.getElementById("addProductModal").style.display = "none";
      document.getElementById("addProductForm").reset();
      window.Monji?.exito("¡Tu producto ya está publicado! 🎉");
    })
    .catch((error) => {
      console.error("Error en la solicitud:", error);
      alert("Error al conectar con el servidor: " + error.message);
    });
};







  // Agregar producto visualmente 
  function addProductToView(
    name,
    description,
    price,
    imageUrl,
    empresa,
    telefono,
    correo,
    provincia,
    ciudad
  ) {
    const productGrid = document.querySelector(".product-grid");

    const productCard = document.createElement("div");
    productCard.classList.add("producto");
    productCard.setAttribute("data-categoria", "todo");
    productCard.innerHTML = `
            <img src="${imageUrl}" alt="${name}" />
            <h3>${name}</h3>
            <p><strong>Empresa:</strong> ${empresa}</p>
            <p>${description}</p>
            <p><strong>Precio:</strong> ₡${price}</p>
           <button class="more-info-btn" 
      data-telefono="${telefono}" 
      data-correo="${correo}" 
      data-ubicacion="${provincia}, ${ciudad}">
      Ver detalles
    </button>
        `;
    productGrid.appendChild(productCard);
  }









































// 🆕 MOSAICO POR JAVASCRIPT (reemplaza el layout de columnas por CSS)
// Antes el "mosaico" lo armaba el navegador con la propiedad CSS
// `columns`, pero esa técnica puede colapsar todo a una sola columna
// cuando las fotos de los productos tienen alturas muy distintas entre
// sí (justo lo que pasa en el catálogo general, que mezcla ropa,
// servicios, juguetes, etc.). Esta función arma un número fijo de
// columnas (divs .masonry-col) y reparte cada tarjeta en la columna que
// tenga menos tarjetas en ese momento — así el resultado es siempre
// estable, sin importar cuántos productos haya ni qué tan distintas
// sean sus fotos.
function pintarMosaico(grid, productos, construirTarjetaHTML) {
  // 🆕 Se agrega la clase acá mismo por código (no solo en el HTML), para
  // que el layout de columnas por JS quede activado siempre, sin
  // depender de que index.html tenga la clase "mosaic-grid" puesta a mano.
  grid.classList.add("mosaic-grid");
  grid.innerHTML = "";

  if (!productos || productos.length === 0) return false;

  const anchoDisponible = grid.clientWidth || window.innerWidth;
  const anchoMinColumna = 210; // ancho aproximado de una tarjeta + separación
  let numColumnas = window.innerWidth <= 500
    ? 2 // siempre 2 columnas en móvil
    : Math.max(1, Math.floor(anchoDisponible / anchoMinColumna));
  numColumnas = Math.min(numColumnas, productos.length); // nunca más columnas que productos

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

    // Se agrega a la columna que tenga menos tarjetas hasta ahora
    // (reparto parejo, sin depender de medir alturas de imágenes que
    // todavía pueden no haber terminado de cargar).
    let colMasCorta = columnas[0];
    columnas.forEach((col) => {
      if (col.children.length < colMasCorta.children.length) colMasCorta = col;
    });
    colMasCorta.appendChild(card);
  });

  return true;
}

function construirTarjetaProductoHTML(producto) {
  return `
        <img src="${producto.imagen || ''}" alt="${producto.nombre || ''}" style="width:150px;height:150px;">
        <h3>${producto.nombre || ''}</h3>
        <button class="more-info-btn"
          data-empresa="${producto.empresa || ''}"
          data-imagen="${producto.imagen || ''}"
          data-nombre="${producto.nombre || ''}"
          data-descripcion="${producto.descripcion || ''}"
          data-provincia="${producto.provincia || ''}"
          data-ciudad="${producto.ciudad || ''}"
          data-telefono="${producto.telefono || ''}"
          data-correo="${producto.correo || ''}"
          data-precio="${producto.precio || ''}"
        >
          Ver detalles
        </button>
      `;
}

// Mostrar productos al cargar la página
fetch("/proyecto-web/api/productos")
  .then((response) => response.json())
  .then((productos) => {
    const grid = document.getElementById("productGrid");
    pintarMosaico(grid, productos, construirTarjetaProductoHTML);
  })
  .catch((error) => {
    console.error("Error al cargar productos:", error);
  });










    

  // Efecto parallax en imagen de bienvenida
  window.addEventListener("scroll", function () {
    const image = document.querySelector(".welcome-image");
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    if (image) {
      image.style.transform = `translateY(${scrollTop * 0.3}px)`;
    }
  });






// --- Búsqueda de productos ---
const searchButton = document.getElementById("searchButton");
const searchInput = document.getElementById("search");
const provinciaInput = document.getElementById("provincia");
const ciudadInput = document.getElementById("ciudad");

function buscarProductos() {
  const query = searchInput.value.trim();
  const provincia = provinciaInput.value.trim();
  const ciudad = ciudadInput.value.trim();

  let url = `/proyecto-web/api/busqueda-productos?`;
  const params = [];
  if (query) params.push(`q=${encodeURIComponent(query)}`);
  if (provincia) params.push(`provincia=${encodeURIComponent(provincia)}`);
  if (ciudad) params.push(`ciudad=${encodeURIComponent(ciudad)}`);
  url += params.join("&");

  fetch(url)
    .then((response) => response.json())
    .then((productos) => {
      const grid = document.getElementById("productGrid");

      if (productos.length === 0) {
        grid.innerHTML = "<p style='color: white;'>No se encontraron productos.</p>";
        window.Monji?.buscando("No encontré productos con esa búsqueda. Prueba con otras palabras 🔍");
        return;
      }

      pintarMosaico(grid, productos, construirTarjetaProductoHTML);
    })
    .catch((error) => console.error("Error al buscar productos:", error));
}

if (searchButton) {
  searchButton.addEventListener("click", buscarProductos);
}

searchInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    buscarProductos();
  }
});

provinciaInput.addEventListener("change", buscarProductos);
ciudadInput.addEventListener("input", buscarProductos);


// --- Eventos de búsqueda ---
searchButton.addEventListener("click", buscarProductos);

// Ejecutar búsqueda al presionar Enter en el input
searchInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    buscarProductos();
  }
});

// Ejecutar búsqueda al cambiar provincia o ciudad
provinciaInput.addEventListener("change", buscarProductos);
ciudadInput.addEventListener("input", buscarProductos);

  searchInput.addEventListener("keypress", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      searchButton.click();
    }
  });















// Capturar clics en filtros de categoría
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
  btn.addEventListener("click", () => {
    categoriaSeleccionada = btn.dataset.category;
    document.querySelectorAll(".filter").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");

    // 🔧 "Mi tienda" usa su propio contenedor (#miTiendaContainer /
    // #misProductosGrid), separado del catálogo (#productGridWrapper).
    // Antes el catálogo nunca se ocultaba al entrar a "Mi tienda": se
    // quedaba vacío ("No se encontraron productos..") pero su wrapper
    // (con el canvas de estrellas activo, si ese era el tema) seguía
    // ahí, aplastado, debajo de los productos del vendedor. Y al revés,
    // #miTiendaContainer nunca se ocultaba al salir de "Mi tienda".
    const wrapperCatalogo = document.getElementById("productGridWrapper");
    const miTiendaContainer = document.getElementById("miTiendaContainer");
    if (categoriaSeleccionada === "Mi tienda") {
      if (wrapperCatalogo) wrapperCatalogo.style.display = "none";
    } else {
      if (wrapperCatalogo) wrapperCatalogo.style.display = "";
      if (miTiendaContainer) miTiendaContainer.style.display = "none";
      cargarProductos();
    }

    // 🔹 Solo abrir modal si es "Mi tienda"
    if (categoriaSeleccionada === "Mi tienda") {
      document.getElementById("miTiendaModal").style.display = "block";
    }
    // 🗑️ Se quitó el "else" que intentaba ocultar "accionesTienda" (ese id
    // no existe en el HTML; se disparaba en cada cambio de categoría y
    // rompía silenciosamente el flujo con un error en consola).
  });
});

function cargarProductos() {
  const params = new URLSearchParams();
  if (filtroPrincipal) params.append("filtro", filtroPrincipal);
  if (categoriaSeleccionada) params.append("categoria", categoriaSeleccionada);

  fetch(`/proyecto-web/api/productos-filtrados?${params.toString()}`)
    .then((response) => response.json())
    .then((productos) => {
      const grid = document.getElementById("productGrid");

      if (productos.length === 0) {
        grid.innerHTML = '<p style="color:white;">No se encontraron productos..</p>';
        window.Monji?.buscando("No encontré productos en esta categoría 🔍");
        return;
      }

      pintarMosaico(grid, productos, construirTarjetaProductoHTML);
    })
    .catch((error) => console.error("Error al cargar productos filtrados:", error));
}



















// 🆕 Guarda el tipo de suscripción del vendedor logueado en "Mi Tienda"
// (se llena tras un login exitoso; usado para limitar cuántos productos
// puede agregar: 7 si es "Básica", 14 si es "Avanzada").
let miTiendaTipoSuscripcion = null;
const LIMITE_PRODUCTOS = { "Básica": 7, "Avanzada": 14 };

// Cerrar modal
document.getElementById("cerrarMiTiendaModal").addEventListener("click", () => {
  document.getElementById("miTiendaModal").style.display = "none";
  // 🔧 Si cierra el login sin entrar, no debe quedar la pantalla vacía:
  // se restaura el catálogo normal (el filtro "Mi tienda" lo había
  // ocultado al abrir este modal).
  const wrapperCatalogo = document.getElementById("productGridWrapper");
  if (wrapperCatalogo) wrapperCatalogo.style.display = "";
  cargarProductos();
  // 🗑️ Se quitó la línea de "accionesTienda" (ese id no existe en el HTML y rompía el flujo)
});

// Manejo del formulario (login)
document.getElementById("miTiendaForm").addEventListener("submit", function (e) {
  e.preventDefault();

  const cedula = document.getElementById("miTiendaCedula").value;
  const password = document.getElementById("miTiendaPassword").value;
  const errorMsg = document.getElementById("errorMsgMiTienda");
  errorMsg.style.display = "none";

  fetch("/proyecto-web/api/mi-tienda-login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `cedula=${encodeURIComponent(cedula)}&password=${encodeURIComponent(password)}`
  })
  .then(response => {
    return response.text().then(text => {
      if (response.ok && text.startsWith("OK:")) {
        // ✅ Extraer el ID de usuario y el tipo de suscripción que devuelve el servlet
        const partes = text.split(":");
        const usuarioId = partes[1].trim();
        miTiendaTipoSuscripcion = partes[2] ? partes[2].trim() : null; // "Básica" o "Avanzada"

        const idDiv = document.getElementById("usuarioIdVisibleMitienda");
        if (idDiv) {
          idDiv.textContent = `ID de usuario: ${usuarioId}`;
          idDiv.style.display = "block";
        }

        document.getElementById("miTiendaModal").style.display = "none";

        // 🔧 Ahora sí se muestra el contenedor de "Mi Tienda" (con los botones
        // Agregar/Eliminar Producto), pero SOLO después de un login exitoso —
        // antes se mostraba apenas se hacía clic en la pestaña, sin validar nada.
        document.getElementById("miTiendaContainer").style.display = "block";

        // Cargar productos del vendedor (lee el ID desde el div de arriba)
        cargarProductosMiTienda(cedula);
      } else {
        errorMsg.textContent = text || "Credenciales inválidas";
        errorMsg.style.display = "block";
      }
    });
  })
  .catch(err => {
    console.error("Error:", err);
    errorMsg.textContent = "Error en la conexión";
    errorMsg.style.display = "block";
  });
});







































































let productoSeleccionado = null;

// Seleccionar producto al hacer clic (dentro de "Mis Productos", no del catálogo general)
document.getElementById("misProductosGrid").addEventListener("click", (e) => {
  const card = e.target.closest(".producto");
  if (!card) return;

  // Deseleccionar anterior
  document.querySelectorAll("#misProductosGrid .producto").forEach(p => p.classList.remove("selected"));

  // Seleccionar actual
  card.classList.add("selected");

  // Guardar atributos del producto usando el ID
  const btn = card.querySelector(".more-info-btn");
  productoSeleccionado = {
    id: btn.dataset.id,   // 🔹 ahora usamos ID
    nombre: btn.dataset.nombre,
    descripcion: btn.dataset.descripcion
    // resto de atributos opcionales si los necesitas
  };
});

// Botón Eliminar Producto
document.getElementById("deleteProductButton").addEventListener("click", () => {
  if (!productoSeleccionado) {
    alert("Seleccione un producto para eliminar.");
    return;
  }

  if (!confirm(`¿Desea eliminar el producto "${productoSeleccionado.nombre}" con descripción "${productoSeleccionado.descripcion}"?`)) {
    return;
  }

  // Llamada a la API usando solo el ID
  fetch("/proyecto-web/EliminarProducto", {
    method: "POST", // usamos POST para mantener compatibilidad con tu servlet
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      id: productoSeleccionado.id
    })
  })
  .then(response => {
    if (response.ok) {
      alert("Producto eliminado con éxito.");
      // recargar productos usando la cédula guardada en el input
      const cedula = document.getElementById("miTiendaCedula").value;
      cargarProductosMiTienda(cedula);
      productoSeleccionado = null;
    } else {
      alert("No se pudo eliminar el producto.");
    }
  })
  .catch(err => console.error("Error en la eliminación:", err));
});



  // 🗑️ ELIMINADO: listener duplicado y obsoleto de "más información".
  // Este bloque quedó de una versión anterior del panel y usaba
  // document.getElementById("infoModal").style.display = "none" / "block"
  // directamente. El problema: el botón de cerrar del panel de Perfil
  // (#cerrarPerfilBtn) también tiene la clase "close-info", así que este
  // listener se disparaba igual al cerrar Perfil y le dejaba un estilo
  // inline "display:none" pegado al panel de "más información" (infoModal),
  // aunque ese panel nunca se hubiera abierto. Ese estilo inline gana
  // siempre sobre la clase CSS ".open" que usa el sistema nuevo (definido
  // en index.html), así que el panel de "más información" quedaba
  // invisible para siempre hasta recargar la página. Toda esta lógica de
  // abrir/cerrar/arrastrar ya la maneja correctamente el script al final
  // de index.html (openPanel/closePanel con la clase "open").




























// --- Depuración: ver qué elementos se encontraron
console.log("DEBUG roles:", {
  rolesContainer: document.getElementById("rolesContainer"),
  btnVendedor: document.getElementById("btnVendedor"),
  btnComprador: document.getElementById("btnComprador"),
  volverRolesBtn: document.getElementById("volverRolesBtn"),
  registroVendedorUnificado: document.getElementById("registroVendedorUnificado"),
  formularioComprador: document.getElementById("formularioComprador"),
  pasoUsuario: document.getElementById("pasoUsuario"),
  btnAtrasVendedor: document.getElementById("btnAtrasVendedor"),
  btnAtrasComprador: document.getElementById("btnAtrasComprador")
});

// --- Variables principales
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

// --- Funciones reutilizables
function openVendedorFlow() {
  console.log("openVendedorFlow");
  rolesContainer.style.display = "none";
  registroVendedorUnificado.style.display = "block";
  pasoUsuario.style.display = "block";
}

function openCompradorFlow() {
  console.log("openCompradorFlow");
  rolesContainer.style.display = "none";
  formularioComprador.style.display = "block";
}

function closeRegistroModal() {
  console.log("closeRegistroModal");
  const registroModal = document.getElementById("registroModal");
  if (registroModal) registroModal.style.display = "none";
  const loginModal = document.getElementById("loginModal");
  if (loginModal) loginModal.style.display = "block"; // ✅ Mostrar login al cerrar registro
}

function backFromVendedor() {
  console.log("backFromVendedor");
  registroVendedorUnificado.style.display = "none";
  rolesContainer.style.display = "block";
}

function backFromComprador() {
  console.log("backFromComprador");
  formularioComprador.style.display = "none";
  rolesContainer.style.display = "block";
}

// --- Eventos principales
btnVendedor?.addEventListener("click", openVendedorFlow);
btnComprador?.addEventListener("click", openCompradorFlow);
volverRolesBtn?.addEventListener("click", closeRegistroModal);
btnAtrasVendedor?.addEventListener("click", backFromVendedor);
btnAtrasComprador?.addEventListener("click", backFromComprador);
btnAtrasSolicitud?.addEventListener("click", backFromSolicitud);
btnAtrasSuscripcion?.addEventListener("click", backFromSuscripcion);


volverRolesBtn?.addEventListener("click", () => {
  console.log("volverRolesBtn → ir al login");
  
  // Cerrar el modal de registro
  const registroModal = document.getElementById("registroModal");
  if (registroModal) registroModal.style.display = "none";
  
  // Asegurar que todos los subformularios se oculten
  rolesContainer.style.display = "none";
  registroVendedorUnificado.style.display = "none";
  formularioComprador.style.display = "none";
  
  // Abrir el modal de login
  const loginModal = document.getElementById("loginModal");
  if (loginModal) {
    loginModal.style.display = "block";
  } else {
    console.warn("⚠️ No se encontró el loginModal en el DOM");
  }
});

function backFromSolicitud() {
  console.log("Volviendo de solicitud a usuario...");
  pasoSolicitud.style.display = "none";
  pasoUsuario.style.display = "block";
}

function backFromSuscripcion() {
  console.log("Volviendo de suscripción a solicitud...");
  pasoSuscripcion.style.display = "none";
  pasoSolicitud.style.display = "block";
}










// -----------------------
// PASO 1 → Registro usuario vendedor
// -----------------------
btnSiguienteUsuario?.addEventListener("click", async () => {
  const nombre = document.getElementById("nombreV").value;
  const correo = document.getElementById("correoV").value;
  const contraseña = document.getElementById("contraseñaV").value;
  if (!nombre || !correo || !contraseña) return alert("Complete todos los campos.");

  try {
    const formData = new URLSearchParams({ nombre, correo, contraseña });
    const res = await fetch("/proyecto-web/registroVendedor", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: formData.toString()
    });

    const data = await res.json();
    usuarioId = data.usuarioId;
    usuarioIdVisible.textContent = "ID de usuario: " + usuarioId;

    pasoUsuario.style.display = "none";
    pasoSolicitud.style.display = "block";
  } catch (err) {
    console.error(err);
    alert("Error al guardar usuario");
  }
});

// -----------------------
// PASO 2 → Solicitud vendedor
// -----------------------
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
    const solicitudData = new URLSearchParams({
      provincia,
      canton,
      descripcion,
      precio_promedio,
      telefono,
      usuario_id: usuarioIdFromDiv
    });

    await fetch("/proyecto-web/guardarSolicitud", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: solicitudData.toString()
    });

    // 🔥 Ocultar modales que interfieran
    document.getElementById("loginModal").style.display = "none";
    rolesContainer.style.display = "none";
    pasoUsuario.style.display = "none";
    pasoSolicitud.style.display = "none";
    pasoSuscripcion.style.display = "block";
  } catch (err) {
    console.error(err);
    alert("Error al guardar solicitud");
  }
});

// -----------------------
// PASO 3 → Suscripción (Finalizar Registro)
// -----------------------
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

    const res = await fetch("/proyecto-web/guardarSuscripcion", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: suscripcionData.toString()
    });

    if (!res.ok) {
      throw new Error("El servidor respondió con estado " + res.status);
    }

    alert("¡Registro completado! Su solicitud como vendedor quedó pendiente de aprobación.");

    // Cerrar todos los modales del flujo de registro
    ["registroModal", "loginModal", "rolesContainer", "registroVendedorUnificado",
     "pasoUsuario", "pasoSolicitud", "pasoSuscripcion"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.style.display = "none";
    });

    // Mostrar mensaje de bienvenida, igual que en el flujo de comprador
    const contenidoPrincipal = document.getElementById("contenidoVendedorRegistrado");
    if (contenidoPrincipal) {
      contenidoPrincipal.style.display = "block";
    }

    // 🆕 Ya quedó registrado como vendedor: ocultar la invitación a vender
    const heroBanner = document.getElementById("heroBanner");
    if (heroBanner) heroBanner.style.display = "none";
  } catch (err) {
    console.error(err);
    alert("Error al guardar la suscripción: " + err.message);
  }
});
// -----------------------
// REGISTRO COMPRADOR (versión corregida)
// -----------------------
const registroFormComprador = document.getElementById("registroFormComprador");

registroFormComprador?.addEventListener("submit", async (e) => {
  e.preventDefault();

  const nombre = document.getElementById("nombreC").value;
  const correo = document.getElementById("correoC").value;
  const contraseña = document.getElementById("contraseñaC").value;

  if (!nombre || !correo || !contraseña) {
    alert("Complete todos los campos.");
    return;
  }

  try {
    const formData = new URLSearchParams({ nombre, correo, contraseña });
    const res = await fetch("/proyecto-web/registroComprador", {
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
    if (!usuarioId || parseInt(usuarioId) <= 0)
      throw new Error("No se pudo registrar el usuario");

    usuarioIdVisible.textContent = "ID de usuario: " + usuarioId;
    alert("¡Comprador registrado con éxito! ID: " + usuarioId);

    // 🔥 Cerrar todos los modales y mostrar el contenido principal
    ["registroModal", "loginModal", "formularioComprador", "rolesContainer",
     "pasoUsuario", "pasoSolicitud", "pasoSuscripcion"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.style.display = "none";
    });

    // ✅ Mostrar contenido principal o contenedor visible tras registro
    const contenidoPrincipal =
      document.getElementById("contenidoCompradorRegistrado") ||
      document.getElementById("contenidoPrincipal") ||
      document.getElementById("contenidoVendedorRegistrado");

    if (contenidoPrincipal) {
      contenidoPrincipal.style.display = "block";
    } else {
      console.warn("⚠️ No se encontró contenedor de contenido principal");
    }

  } catch (err) {
    console.error(err);
    alert("Error al conectar con el servidor: " + err.message);
  }
});






























































// ==========================
// Variables modales y botones (igual que antes)
// ==========================
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

// Elemento donde guardamos el id (pequeño/oculto)

const usuarioIdVisibleEl = document.getElementById("usuarioIdVisible");

// Guardar ID (lo hace visible y en sessionStorage)
function setUsuarioId(id) {
  if(!id) return;
  if(usuarioIdVisibleEl) {
    usuarioIdVisibleEl.textContent = id;
    usuarioIdVisibleEl.style.display = "block"; // aunque muy pequeño y transparente
  }
  sessionStorage.setItem("usuarioId", String(id));
}

// Obtener ID (desde el div o sessionStorage)
function getUsuarioId() {
  const fromEl = usuarioIdVisibleEl?.textContent?.trim();
  if(fromEl && /^\d+$/.test(fromEl)) return fromEl;
  const fromSession = sessionStorage.getItem("usuarioId");
  if(fromSession && /^\d+$/.test(fromSession)) return fromSession;
  return null;
}












// ==========================
// Funciones generales
// ==========================
function showModal(modal) { if(modal){ modal.style.display = "flex"; document.body.classList.add("modal-open"); } }
function hideModal(modal) { if(modal){ modal.style.display = "none"; document.body.classList.remove("modal-open"); } }
// 🔧 Igual que mostrarAyuda(): showModal/hideModal quedan "atrapadas" dentro
// del DOMContentLoaded de este archivo. Los <script> sueltos de index.html
// (recuperar contraseña, cerrarSesion, etc.) corren en el ámbito global y
// no las encontraban → "hideModal is not defined". Se exponen en window.
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



// ==========================
// Mostrar login al cargar (solo si NO hay usuarioIdVisible con número válido)
// ==========================
window.addEventListener("DOMContentLoaded", () => {
  const usuarioIdVisible = document.getElementById("usuarioIdVisible");
  const texto = usuarioIdVisible?.textContent?.trim() || "";

  // Verifica si el texto contiene un número (ID válido)
  const tieneId = /\d+/.test(texto);

  // Si NO hay ID, muestra el login
  if (!tieneId && loginModal) {
    loginModal.style.display = "flex";
  } else {
    // Si hay ID, NO reinicia el flujo ni toca el modal actual
    console.log("Usuario activo detectado, no se muestra login.");
  }
});














// ==========================
// Login: botones (igual que antes)
// ==========================
cancelarBtn?.addEventListener("click", () => hideModal(loginModal));
continuarBtn?.addEventListener("click", () => { alert("Has continuado con tu selección"); hideModal(loginModal); });

// ==========================
// Registro general / abrir modal registro (igual)
// ==========================
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

// ==========================
// Selección de rol (mantener comportamiento original)
// ==========================
btnVendedor?.addEventListener("click", () => { hideModal(loginModal); showModal(modalVendedor); });
btnComprador?.addEventListener("click", () => { hideModal(loginModal); showModal(modalComprador); });
btnInvitado?.addEventListener("click", () => { ocultarTodo(); hideModal(loginModal); document.getElementById("contenidoInvitado")?.style.setProperty("display","block"); actualizarMiTienda("invitado"); });

btnVolverVendedor?.addEventListener("click", () => { hideModal(modalVendedor); showModal(loginModal); });
btnVolverComprador?.addEventListener("click", () => { hideModal(modalComprador); showModal(loginModal); });









// ==========================
// Cancelar formularios (mantén todo igual)
// ==========================
document.getElementById("btnCancelar")?.addEventListener("click",()=>cancelarFormulario("formularioComprador","registroForm"));
document.getElementById("btnCancelarVendedor")?.addEventListener("click",()=>cancelarFormulario("formularioVendedor","registroFormVendedor"));
document.getElementById("btnCancelarSolicitud")?.addEventListener("click",()=>cancelarFormulario("formularioSolicitud","solicitudForm"));
document.getElementById("btnCancelarSuscripcion")?.addEventListener("click",()=>cancelarFormulario("formularioSuscripcion","suscripcionForm"));

// ==========================
// 🗑️ Se eliminaron 4 líneas que llamaban a avanzarFlujo(), una función que
// nunca fue definida en ningún lado. Apuntaban a ids de una versión antigua
// del formulario (formularioVendedor, formularioSuscripcion, etc.) que ya
// no existen en el HTML actual. btnVendedor y btnComprador ya tienen sus
// manejadores reales más arriba (openVendedorFlow / openCompradorFlow).
// ==========================

// ==========================
// Helpers para usuarioId
// ==========================
function setUsuarioId(id) {
  if(!id) return;
  // guardamos visible (pero tiny) y en sessionStorage
  if(usuarioIdVisibleEl) {
    usuarioIdVisibleEl.textContent = id;
    usuarioIdVisibleEl.style.display = "block"; // ya lo definiste muy pequeño/transparent
  }
  sessionStorage.setItem("usuarioId", String(id));
}

function getUsuarioId() {
  // priorizar el elemento visible, si no fallback a sessionStorage
  const fromEl = usuarioIdVisibleEl?.textContent?.trim();
  if(fromEl && /^\d+$/.test(fromEl)) return fromEl;
  const fromSession = sessionStorage.getItem("usuarioId");
  if(fromSession && /^\d+$/.test(fromSession)) return fromSession;
  return null;
}














































// 🔹 Bloquear "Mi tienda" según tipo de usuario
function actualizarMiTienda(usuario) {
  // 🆕 El bloque "Compra y vende con confianza" (hero) solo debe verse
  // para invitados y compradores; un vendedor ya está registrado como
  // tal y no necesita ver la invitación a vender. Va antes del "return"
  // de abajo para que se aplique siempre, incluso si por algún motivo
  // no se encuentra el botón "Mi tienda".
  const heroBanner = document.getElementById("heroBanner");
  if (heroBanner) {
    const esVendedor = typeof usuario === "string" && usuario.startsWith("vendedor");
    heroBanner.style.display = esVendedor ? "none" : "";
  }

  // 🆕 Banner motivacional para vendedores: espejo del de arriba, solo
  // visible cuando el usuario en sesión SÍ es vendedor.
  const heroBannerVendedor = document.getElementById("heroBannerVendedor");
  if (heroBannerVendedor) {
    const esVendedor = typeof usuario === "string" && usuario.startsWith("vendedor");
    heroBannerVendedor.style.display = esVendedor ? "" : "none";
  }

  // 🔧 Cada vez que cambia el tipo de usuario, el banner "correcto" a
  // mostrar puede cambiar (comprador/invitado ↔ vendedor). Si la campana
  // de notificaciones tenía guardado un banner viejo para reaparecerlo,
  // hay que descartarlo — si no, un clic en la campana puede reaparecer
  // un banner que ya no corresponde a la sesión actual, mostrando los dos
  // banners a la vez.
  if (typeof window.resetNotifBanner === "function") {
    window.resetNotifBanner();
  }

  const miTiendaBtn = document.querySelector('.filter[data-category="Mi tienda"]');
  if (!miTiendaBtn) return;

  if (usuario === "vendedor" || usuario === "vendedor-recién-registrado") {
    miTiendaBtn.disabled = false;
    miTiendaBtn.style.opacity = 1;
  } else {
    miTiendaBtn.disabled = true;
    miTiendaBtn.style.opacity = 0.5;
  }
}
// 🔧 Misma causa que showModal/hideModal: actualizarMiTienda vive dentro
// del DOMContentLoaded de script.js, pero se llama desde los <script>
// sueltos de index.html (window.addEventListener("load", ...) y
// cerrarSesion()), que corren en ámbito global → "actualizarMiTienda is
// not defined". Se expone en window para que esas llamadas la encuentren.
window.actualizarMiTienda = actualizarMiTienda;

// 🔹 Acceder Vendedor (LOGIN, sin bienvenida)
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
      alert("Inicio de sesión exitoso");
      ocultarTodo();
      document.getElementById("modalVendedor")?.style.setProperty("display", "none");
      // ❌ quitamos la bienvenida de vendedor registrado
      actualizarMiTienda("vendedor");
      iniciarSesion("vendedor"); // 🔧 nuevo: ahora la sesión local se marca
      // recién aquí, cuando el servidor ya confirmó el login (response.ok)
      // y ya existe sesión real en el backend. Así /api/perfil coincide
      // con lo que el front cree.
    } else {
      return response.text().then(text => { throw new Error(text); });
    }
  })
  .catch(error => { alert(error.message); });
  document.getElementById("olvideVendedor").style.display = "block";
});





  



// 🔹 Acceder Comprador (LOGIN, sin bienvenida)
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
      alert("Inicio de sesión exitoso");
      ocultarTodo();
      document.getElementById("modalComprador")?.style.setProperty("display", "none");
      document.getElementById("contenidoCompradorActivo").style.display = "block";
      actualizarMiTienda("comprador");  // 🔹 Ahora sí se bloquea correctamente
      iniciarSesion("comprador"); // 🔧 nuevo: mismo motivo que en Vendedor —
      // solo marcamos sesión local cuando el servidor ya confirmó el login.
    } else {
      return response.text().then(text => { throw new Error(text); });
    }
  })
  .catch(error => { alert(error.message); });
  document.getElementById("olvideComprador").style.display = "block";
});



// Control método de pago paginaComprador
const metodoPago = document.getElementById('metodoPago');
const tarjetaCampos = document.getElementById('tarjetaCampos');
const numeroSimpe = document.getElementById('numeroSimpe');
const depositoCampos = document.getElementById('depositoCampos'); // ✅ nuevo
const contenedor = document.getElementById('contenedorPagoExtra');

metodoPago.addEventListener('change', function () {
  // Ocultamos todos primero
  tarjetaCampos.classList.add('oculto');
  numeroSimpe.classList.add('oculto');
  depositoCampos.classList.add('oculto'); // ✅ ocultar depósito también

  contenedor.style.height = contenedor.scrollHeight + 'px';

  setTimeout(() => {
    let nuevoContenido;

    if (this.value === 'tarjeta') {
      tarjetaCampos.classList.remove('oculto');
      nuevoContenido = tarjetaCampos;
    } else if (this.value === 'simpe') {
      numeroSimpe.classList.remove('oculto');
      nuevoContenido = numeroSimpe;
    } else if (this.value === 'deposito') { // ✅ depósito
      depositoCampos.classList.remove('oculto');
      nuevoContenido = depositoCampos;
    }

    setTimeout(() => {
      const nuevaAltura = nuevoContenido ? nuevoContenido.scrollHeight + 20 : 0;
      contenedor.style.height = nuevaAltura + 'px';
    }, 50);
  }, 50);
});

// Mostrar características según suscripción
const radiosSuscripcion = document.querySelectorAll('input[name="suscripcion"]');
const divBasica = document.getElementById('caracteristicasBasica');
const divAvanzada = document.getElementById('caracteristicasAvanzada');

radiosSuscripcion.forEach(radio => {
  radio.addEventListener('change', function () {
    if (this.value === 'Básica') {
      divBasica.style.display = 'block';
      divAvanzada.style.display = 'none';
    } else if (this.value === 'Avanzada') {
      divBasica.style.display = 'none';
      divAvanzada.style.display = 'block';
    }
  });
});

// Submit formulario solicitud
const solicitudFormEl = document.getElementById('solicitudForm');
if (solicitudFormEl) {
  solicitudFormEl.addEventListener('submit', function(e) {
    e.preventDefault();
    const formData = new FormData(this);

    fetch('/proyecto-web/guardarSolicitud', {
      method: 'POST',
      body: formData
    })
    .then(resp => {
      if (!resp.ok) throw resp;
      return resp.text();
    })
    .then(data => alert(data))
    .catch(async err => {
      const msg = await err.text();
      alert('Error: ' + msg);
    });
  });
}













/* 🔧 #productGridWrapper tenía un fondo fijo (#f4f4f6) que nunca cambiaba
   con el tema. Eso causaba dos cosas: 1) en modo oscuro se veía un borde
   blanco feo alrededor del grid (el padding del wrapper mostrando su
   fondo claro fijo), y 2) en el tema Estrellado, ese fondo opaco tapaba
   por completo el canvas de estrellas (que ocupa todo el wrapper, no solo
   el grid), así que las estrellas nunca se llegaban a ver. Esta función
   centraliza el color del wrapper para que siempre haga juego con el
   tema activo. */
const productGridWrapper = document.getElementById('productGridWrapper');
// 🆕 El grid de "Mi tienda" (#misProductosGrid) es un contenedor aparte del
// catálogo — ninguna de las funciones de tema lo tocaba, así que se quedaba
// siempre con su fondo blanco por defecto sin importar el tema elegido.
const misProductosGrid = document.getElementById('misProductosGrid');
function sincronizarFondoWrapper(color) {
  if (productGridWrapper) productGridWrapper.style.backgroundColor = color;
}

/* ============ TEMA ESTRELLADO ============ */
const starsCanvas = document.getElementById('starsCanvas');
const starThemeBtn = document.getElementById('starThemeBtn');
let starsCtx, starParticles = [], starsAnimId;

function resizeStars() {
  if (!starsCanvas) return;
  // 🔧 Antes el canvas se dimensionaba al tamaño de TODA la ventana
  // (window.innerWidth/innerHeight), aunque ahora vive dentro de
  // #productGridWrapper (más chico). Eso significaba dibujar y limpiar un
  // lienzo mucho más grande de lo necesario, 60 veces por segundo, sin
  // límite de resolución en pantallas retina — la causa más probable de
  // la lentitud. Ahora usamos el tamaño real del contenedor y limitamos
  // el devicePixelRatio a 2 como máximo.
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
    x: Math.random() * anchoCss,
    y: Math.random() * altoCss,
    r: Math.random() * 1.4 + 0.3,
    a: Math.random(),
    speed: Math.random() * 0.015 + 0.003
  }));
}

function drawStars() {
  if (!document.body.classList.contains('theme-stars')) return;
  // 🔧 clearRect ahora en coordenadas CSS (no en píxeles físicos), porque
  // el contexto ya está escalado por devicePixelRatio con setTransform.
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

// Se declaran con "function" (no const/arrow) para que estén disponibles
// más arriba en el archivo (donde está el switch claro/oscuro) sin
// importar el orden en que aparezcan en el código.
function activarEstrellado(guardar = true) {
  desactivarRosado(false);
  document.body.classList.add('theme-stars');
  if (starThemeBtn) starThemeBtn.classList.add('active');
  // Solo #productGrid se vuelve transparente — así el fondo estrellado
  // se ve nada más donde están los productos, el resto se queda negro.
  if (productGrid) {
    productGrid.style.backgroundColor = 'transparent';
    productGrid.style.color = '#fff';
  }
  // "Mi tienda" no tiene canvas propio: le damos el mismo fondo nocturno
  // en estático (sin animación), en vez de dejarlo transparente y perdido
  // sobre el negro de la página.
  if (misProductosGrid) {
    misProductosGrid.style.backgroundColor = '';
    misProductosGrid.style.backgroundImage =
      'radial-gradient(ellipse at 50% 15%, #2a1e52 0%, transparent 55%), ' +
      'linear-gradient(180deg, #080b24 0%, #151a4d 55%, #080b24 100%)';
    misProductosGrid.style.color = '#fff';
  }
  // El wrapper también debe volverse transparente: el canvas cubre TODO
  // el wrapper (no solo #productGrid), así que si el wrapper se queda con
  // su fondo blanco de siempre, tapa las estrellas por completo.
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
    // Restaurar el color de productGrid según el switch claro/oscuro actual
    if (restaurar && productGrid) {
      aplicarColorGrid(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
    }
  }
}

/* ============ TEMA ROSADO SUAVE ============ */
const pinkThemeBtn = document.getElementById('pinkThemeBtn');

function activarRosado(guardar = true) {
  desactivarEstrellado(false);
  document.body.classList.add('theme-pink');
  if (pinkThemeBtn) pinkThemeBtn.classList.add('active');
  // El color real del catálogo lo pone la regla CSS "body.theme-pink
  // #productGrid"; acá solo limpiamos cualquier estilo inline previo
  // (por si veníamos del modo claro/oscuro) para que la regla CSS mande.
  if (productGrid) {
    productGrid.style.backgroundColor = '';
    productGrid.style.color = '';
  }
  if (misProductosGrid) {
    misProductosGrid.style.backgroundImage = 'linear-gradient(160deg, #ffeef3 0%, #ffe0ea 100%)';
    misProductosGrid.style.color = '#5a2f43';
  }
  // Mismo rosado suave que el catálogo, para que no quede un borde de
  // otro color alrededor.
  sincronizarFondoWrapper('#ffeef3');
  if (guardar) guardarTema('rosado');
}

function desactivarRosado(restaurar = true) {
  if (document.body.classList.contains('theme-pink')) {
    document.body.classList.remove('theme-pink');
    if (pinkThemeBtn) pinkThemeBtn.classList.remove('active');
    if (restaurar && productGrid) {
      aplicarColorGrid(themeToggle && themeToggle.checked ? 'claro' : 'oscuro');
    }
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

/* ============ SISTEMA CENTRAL DE TEMAS (con persistencia) ============
   Un solo lugar que sabe pintar #productGrid en claro/oscuro, y que
   guarda/lee el tema elegido en localStorage para que se mantenga
   permanente entre visitas, hasta que el usuario lo cambie de nuevo. */
function aplicarColorGrid(modo) {
  if (!productGrid) return;
  if (modo === 'claro') {
    productGrid.style.backgroundColor = '#f0f0f0';
    productGrid.style.color = '#000';
    sincronizarFondoWrapper('#f0f0f0');
    if (misProductosGrid) {
      misProductosGrid.style.backgroundImage = '';
      misProductosGrid.style.backgroundColor = '#f0f0f0';
      misProductosGrid.style.color = '#000';
    }
  } else {
    productGrid.style.backgroundColor = 'black';
    productGrid.style.color = '#fff';
    sincronizarFondoWrapper('black');
    if (misProductosGrid) {
      misProductosGrid.style.backgroundImage = '';
      misProductosGrid.style.backgroundColor = 'black';
      misProductosGrid.style.color = '#fff';
    }
  }
}

function guardarTema(nombre) {
  localStorage.setItem('temaTienda', nombre);
}

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

// Al cargar la página, se restaura el último tema elegido por el usuario
// (guardado en localStorage). Si nunca eligió ninguno, se queda el tema
// oscuro original por defecto.
(function restaurarTemaGuardado() {
  // 🔧 Antes caía en 'oscuro' cuando no había tema guardado, y 'oscuro'
  // pinta #productGrid de negro (aplicarColorGrid), tapando el fondo
  // blanco de #productGridWrapper y dejando solo el padding visible como
  // un "borde". Por defecto el catálogo debe verse blanco/claro.
  const temaGuardado = localStorage.getItem('temaTienda') || 'claro';
  switch (temaGuardado) {
    case 'claro':
      activarClaro(false);
      break;
    case 'estrellado':
      activarEstrellado(false);
      break;
    case 'rosado':
      activarRosado(false);
      break;
    default:
      activarOscuro(false);
      break;
  }
})();



/* ============ OCULTAR IMAGEN DE BIENVENIDA DESPUÉS DE UNOS SEGUNDOS ============ */
// NOTA: ya estamos dentro del DOMContentLoaded principal del archivo (línea 2),
// así que este código corre directo, sin volver a esperar ese evento
// (ese evento ya pasó, por eso el timer nunca se ejecutaba antes).
const welcomeContainer = document.querySelector('.welcome-container');
if (welcomeContainer) {
  setTimeout(() => {
    welcomeContainer.style.transition = 'opacity 0.6s ease, max-height 0.6s ease, margin 0.6s ease';
    welcomeContainer.style.opacity = '0';
    welcomeContainer.style.maxHeight = '0';
    welcomeContainer.style.margin = '0';
    welcomeContainer.style.overflow = 'hidden';

    setTimeout(() => {
      welcomeContainer.style.display = 'none';
    }, 650);
},25000);//25 segundos visible antes de desaparecer — ajusta este número si quieres más/menos tiempo
}



























});
 

// ============================================================
// 🆕 PANEL DE PERFIL
// ============================================================
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

  // 🆕 Checks de intereses de notificación (Damas/Caballeros/Niños/Servicios).
  // Se muestran para cualquier tipo de cuenta, ya que un Vendedor también
  // puede querer que le avisen de productos nuevos como comprador.
  const checksInteres = {
    Damas: document.getElementById("interesDamas"),
    Caballeros: document.getElementById("interesCaballeros"),
    "Niños": document.getElementById("interesNinos"),
    Servicios: document.getElementById("interesServicios"),
  };

  // 🆕 Checks de provincia(s) de preferencia, mismo patrón que checksInteres.
  const checksProvincia = {
    "San José": document.getElementById("provSanJose"),
    Alajuela: document.getElementById("provAlajuela"),
    Cartago: document.getElementById("provCartago"),
    Heredia: document.getElementById("provHeredia"),
    Guanacaste: document.getElementById("provGuanacaste"),
    Puntarenas: document.getElementById("provPuntarenas"),
    "Limón": document.getElementById("provLimon"),
  };

  if (!btnAbrir || !panel || !overlay) return; // evita errores si algo no existe

  function abrirPanel() {
    // 🔧 nuevo: si el panel de "Más información" estaba abierto, lo
    // cerramos primero — es el mismo arreglo simétrico que se hizo en
    // openPanel() de infoModal, para que nunca queden los dos abiertos
    // a la vez.
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
      const res = await fetch("/proyecto-web/api/perfil");
      if (!res.ok) {
        cargando.textContent = "Debes iniciar sesión para ver tu perfil.";
        return;
      }
      const perfil = await res.json();

      inputNombre.value = perfil.nombre || "";
      inputCorreo.value = perfil.correo || "";
      divTipo.textContent = perfil.tipo || "—";
      badge.style.display = perfil.esDestacado ? "inline-block" : "none";

      // 🔧 nuevo: mostrar el plan de suscripción solo si es Vendedor y el
      // servidor mandó un tipoSuscripcion (igual que se usa al agregar producto).
      if (perfil.tipo && perfil.tipo.toLowerCase() === "vendedor" && perfil.tipoSuscripcion) {
        divPlan.textContent = perfil.tipoSuscripcion + (perfil.suscrito ? "" : " (inactiva)");
        planWrap.style.display = "block";
      } else {
        planWrap.style.display = "none";
      }

      // 🆕 Marcar los checks según los intereses guardados del usuario.
      // El backend debe devolver perfil.intereses como un arreglo de
      // strings, por ejemplo: ["Damas", "Niños"]. Si no viene nada
      // (undefined o arreglo vacío), todos los checks quedan sin marcar.
      const interesesGuardados = Array.isArray(perfil.intereses) ? perfil.intereses : [];
      Object.entries(checksInteres).forEach(([categoria, checkbox]) => {
        if (checkbox) checkbox.checked = interesesGuardados.includes(categoria);
      });

      // 🆕 Marcar los checks de provincia según lo guardado (perfil.provincias).
      const provinciasGuardadas = Array.isArray(perfil.provincias) ? perfil.provincias : [];
      Object.entries(checksProvincia).forEach(([provincia, checkbox]) => {
        if (checkbox) checkbox.checked = provinciasGuardadas.includes(provincia);
      });

      cargando.style.display = "none";
      datos.style.display = "block";
    } catch (err) {
      console.error("Error al cargar perfil:", err);
      cargando.textContent = "No se pudo cargar el perfil. Intenta de nuevo.";
    }
  }

  btnAbrir.addEventListener("click", () => {
    // 🔧 nuevo: el botón "Perfil" vive DENTRO de #menuContent, así que el
    // listener que cierra el menú al hacer clic "afuera" nunca se disparaba
    // (el clic fue adentro). Eso dejaba el menú desplegable abierto detrás
    // del panel, y luego interceptaba clics de otras partes de la página
    // (por eso "Más información" dejaba de responder).
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
      if (!nuevoCorreo || !nuevoCorreo.includes("@")) {
        return mostrarMensaje("Ingresa un correo válido.", "error");
      }

      // 🆕 Categorías marcadas por el usuario para recibir notificaciones
      // de productos nuevos. Si no marcó ninguna, se manda vacío y el
      // backend no debe enviarle ningún correo de producto nuevo.
      const interesesSeleccionados = Object.entries(checksInteres)
        .filter(([, checkbox]) => checkbox && checkbox.checked)
        .map(([categoria]) => categoria);

      // 🆕 Provincias marcadas por el usuario para recibir notificaciones
      // de productos nuevos en esas zonas. Mismo patrón que las categorías.
      const provinciasSeleccionadas = Object.entries(checksProvincia)
        .filter(([, checkbox]) => checkbox && checkbox.checked)
        .map(([provincia]) => provincia);

      const textoIntereses = interesesSeleccionados.length
        ? interesesSeleccionados.join(", ")
        : "Ninguna";
      const textoProvincias = provinciasSeleccionadas.length
        ? provinciasSeleccionadas.join(", ")
        : "Ninguna (sin filtrar por zona)";

      if (!confirm(`¿Confirmas guardar estos cambios?\n\nNombre: ${nuevoNombre}\nCorreo: ${nuevoCorreo}\nCategorías: ${textoIntereses}\nProvincias: ${textoProvincias}`)) {
        return;
      }

      try {
        const res = await fetch("/proyecto-web/api/perfil/actualizar", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            nombre: nuevoNombre,
            correo: nuevoCorreo,
            // 🆕 se manda como lista separada por comas, ej: "Damas,Niños"
            intereses: interesesSeleccionados.join(","),
            // 🆕 igual pero para provincias, ej: "San José,Heredia"
            provincias: provinciasSeleccionadas.join(",")
          }).toString()
        });
        const texto = await res.text();

        if (res.ok) {
          mostrarMensaje("✅ Cambios guardados correctamente.", "ok");
        } else {
          mostrarMensaje(texto || "No se pudo guardar. Intenta de nuevo.", "error");
        }
      } catch (err) {
        console.error("Error al guardar perfil:", err);
        mostrarMensaje("Error de conexión al guardar.", "error");
      }
    });
  }

  if (btnBorrar) {
    btnBorrar.addEventListener("click", async () => {
      if (!confirm("¿Seguro que deseas borrar tu cuenta? Esta acción no se puede deshacer.")) {
        return;
      }
      if (!confirm("Última confirmación: se eliminarán tu cuenta y tus datos asociados de forma permanente. ¿Continuar?")) {
        return;
      }

      try {
        const res = await fetch("/proyecto-web/api/perfil/eliminar", { method: "POST" });
        if (res.ok) {
          alert("Tu cuenta fue eliminada. Serás redirigido al inicio.");
          localStorage.removeItem("sesionUsuario");
          window.location.href = "/proyecto-web/";
        } else {
          const texto = await res.text();
          mostrarMensaje(texto || "No se pudo eliminar la cuenta.", "error");
        }
      } catch (err) {
        console.error("Error al eliminar cuenta:", err);
        mostrarMensaje("Error de conexión al eliminar la cuenta.", "error");
      }
    });
  }

  // 🆕 Cambiar contraseña: primero se verifica la contraseña actual en el
  // backend y, si es correcta, se guarda la nueva. Usa su propio mensaje
  // (perfilPasswordMensaje) para no mezclarse con el de "Guardar cambios".
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

      if (!actual) {
        return mostrarMensajePassword("Ingresa tu contraseña actual.", "error");
      }
      if (!nueva || nueva.length < 6) {
        return mostrarMensajePassword("La nueva contraseña debe tener al menos 6 caracteres.", "error");
      }
      if (nueva !== confirmar) {
        return mostrarMensajePassword("Las contraseñas nuevas no coinciden.", "error");
      }

      btnCambiarPassword.disabled = true;
      btnCambiarPassword.textContent = "Verificando...";

      try {
        // 🔧 El backend debe: 1) verificar que "actual" sea la contraseña
        // vigente del usuario en sesión, y solo si es correcta, 2) guardar
        // "nueva" como la nueva contraseña. Debe responder 401/400 con un
        // mensaje de texto si "actual" no coincide.
        const res = await fetch("/proyecto-web/api/perfil/cambiar-contrasena", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            actual: actual,
            nueva: nueva
          }).toString()
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
        console.error("Error al cambiar contraseña:", err);
        mostrarMensajePassword("Error de conexión al actualizar la contraseña.", "error");
      } finally {
        btnCambiarPassword.disabled = false;
        btnCambiarPassword.textContent = "Actualizar contraseña";
      }
    });
  }
})();

// ==========================
// Recuperar contraseña
// ==========================
const modalRecuperar = document.getElementById("modalRecuperar");
const recuperarPaso1 = document.getElementById("recuperarPaso1");
const recuperarPaso2 = document.getElementById("recuperarPaso2");
const recuperarMensaje = document.getElementById("recuperarMensaje");
let recuperarModalOrigen = null; // desde qué modal (vendedor/comprador) se abrió

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
  // El vendedor entra con cédula, no con correo, así que no prellenamos nada.
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

// Paso 1: pedir código
document.getElementById("btnEnviarCodigo")?.addEventListener("click", async () => {
  const correo = document.getElementById("recuperarCorreo").value.trim();

  if (!correo || !correo.includes("@")) {
    mostrarMensajeRecuperar("Ingresa un correo válido.", "#ff8a8a");
    return;
  }

  const btn = document.getElementById("btnEnviarCodigo");
  btn.disabled = true;
  btn.textContent = "Enviando...";

  try {
    const res = await fetch("/proyecto-web/api/recuperar/solicitar", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ correo }).toString()
    });
    const texto = await res.text();

    mostrarMensajeRecuperar(texto, "#8fd68f");
    // Pasamos al paso 2 siempre, exista o no el correo (no delatamos nada)
    recuperarPaso1.style.display = "none";
    recuperarPaso2.style.display = "block";
  } catch (err) {
    console.error("Error solicitando código:", err);
    mostrarMensajeRecuperar("Error de conexión. Intenta de nuevo.", "#ff8a8a");
  } finally {
    btn.disabled = false;
    btn.textContent = "Enviar código";
  }
});

// Paso 2: confirmar código + nueva contraseña
document.getElementById("btnCambiarPassword")?.addEventListener("click", async () => {
  const correo = document.getElementById("recuperarCorreo").value.trim();
  const codigo = document.getElementById("recuperarCodigo").value.trim();
  const nueva = document.getElementById("recuperarNueva").value;
  const confirmar = document.getElementById("recuperarConfirmar").value;

  if (!/^\d{6}$/.test(codigo)) {
    mostrarMensajeRecuperar("Ingresa el código de 6 dígitos.", "#ff8a8a");
    return;
  }
  if (!nueva || nueva.length < 6) {
    mostrarMensajeRecuperar("La nueva contraseña debe tener al menos 6 caracteres.", "#ff8a8a");
    return;
  }
  if (nueva !== confirmar) {
    mostrarMensajeRecuperar("Las contraseñas no coinciden.", "#ff8a8a");
    return;
  }
  

  const btn = document.getElementById("btnCambiarPassword");
  btn.disabled = true;
  btn.textContent = "Verificando...";

  try {
    const res = await fetch("/proyecto-web/api/recuperar/confirmar", {
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
    console.error("Error confirmando código:", err);
    mostrarMensajeRecuperar("Error de conexión. Intenta de nuevo.", "#ff8a8a");
  } finally {
    btn.disabled = false;
    btn.textContent = "Cambiar contraseña";
  }
});