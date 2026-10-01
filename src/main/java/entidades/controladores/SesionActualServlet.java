package entidades.controladores;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Le dice al navegador si hay sesión en el servidor y de qué tipo.
 * SesionPersistenteFilter ya restauró la sesión desde la cookie (si existía)
 * antes de llegar aquí.
 *
 * Respuesta: {"logueado":true,"tipo":"comprador"|"vendedor","usuarioId":N}
 */
@WebServlet("/SesionActualServlet")
public class SesionActualServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        HttpSession s = request.getSession(false);
        if (s != null && s.getAttribute("usuarioId") != null) {
            // Los compradores guardan nombreUsuario; los vendedores solo nombreVendedor.
            boolean esVendedor = s.getAttribute("nombreUsuario") == null && s.getAttribute("nombreVendedor") != null;
            response.getWriter().write("{\"logueado\":true,\"tipo\":\"" + (esVendedor ? "vendedor" : "comprador")
                    + "\",\"usuarioId\":" + s.getAttribute("usuarioId") + "}");
        } else {
            response.getWriter().write("{\"logueado\":false}");
        }
    }
}