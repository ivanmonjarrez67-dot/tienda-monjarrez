package entidades.controladores;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Le dice al navegador si hay sesión de comprador en el servidor.
 * SesionPersistenteFilter ya restauró la sesión desde la cookie (si existía)
 * antes de llegar aquí.
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
            response.getWriter().write("{\"logueado\":true,\"usuarioId\":" + s.getAttribute("usuarioId") + "}");
        } else {
            response.getWriter().write("{\"logueado\":false}");
        }
    }
}