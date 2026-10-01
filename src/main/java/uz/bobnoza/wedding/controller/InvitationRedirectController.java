package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.service.GuestService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 *
 * The one exception is the link preview: invitation.html's static
 * <title>/Open Graph tags describe the Tashkent celebration, and messenger
 * crawlers (Telegram etc.) don't run the page's JS, so a Samarkand guest's
 * link gets a copy of the page with those head tags rewritten for Samarkand.
 */
@Controller
public class InvitationRedirectController {

    // Tashkent text in invitation.html's <head> → its Samarkand equivalent.
    // Keep in sync with invitation.html and HALL_STRINGS.SAMARKAND in i18n.js.
    private static final Map<String, String> SAMARKAND_HEAD = Map.of(
            "Бобур и Дильноза — 2 октября 2026", "Бобур и Дильноза — 10 октября 2026",
            "2 октября 2026, Ташкент, ресторан Santini", "10 октября 2026, Самарканд, ресторан Богишамол"
    );

    private final GuestService guestService;
    private final String samarkandPage;

    public InvitationRedirectController(GuestService guestService) {
        this.guestService = guestService;
        this.samarkandPage = buildSamarkandPage();
    }

    @GetMapping("/i/{slug}")
    public Object forwardToInvitationPage(@PathVariable String slug) {
        if (guestService.findHallBySlug(slug).orElse(Hall.TASHKENT) == Hall.SAMARKAND) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(samarkandPage);
        }
        return new ModelAndView("forward:/invitation.html");
    }

    /** Fails startup if invitation.html's head text drifted, rather than silently serving Tashkent previews again. */
    private static String buildSamarkandPage() {
        String html;
        try {
            html = new ClassPathResource("static/invitation.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read static/invitation.html", e);
        }
        int headEnd = html.indexOf("</head>");
        String head = html.substring(0, headEnd);
        for (Map.Entry<String, String> replacement : SAMARKAND_HEAD.entrySet()) {
            if (!head.contains(replacement.getKey())) {
                throw new IllegalStateException("invitation.html <head> no longer contains \"" + replacement.getKey()
                        + "\" — update InvitationRedirectController.SAMARKAND_HEAD");
            }
            head = head.replace(replacement.getKey(), replacement.getValue());
        }
        return head + html.substring(headEnd);
    }
}
