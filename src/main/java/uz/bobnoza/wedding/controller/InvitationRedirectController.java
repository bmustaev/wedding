package uz.bobnoza.wedding.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Makes the "pretty" invitation URL (/i/{slug}) actually resolve to the
 * real page, by forwarding it to the static invitation.html file — an
 * internal servlet forward, so the browser's address bar keeps showing
 * /i/{slug} rather than jumping to a query-string URL.
 *
 * invitation.html's own JS reads the slug from either the query string or
 * this path form (see SLUG in its <script>), so this controller only needs
 * to get the request to the right file — it doesn't need to rewrite
 * anything itself.
 */
@Controller
public class InvitationRedirectController {

    @GetMapping("/i/{slug}")
    public ModelAndView forwardToInvitationPage() {
        return new ModelAndView("forward:/invitation.html");
    }
}
