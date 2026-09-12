package uz.bobnoza.wedding.config;

import uz.bobnoza.wedding.security.CustomUserDetailsService;
import uz.bobnoza.wedding.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService, JwtAuthFilter jwtAuthFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is fine for this scale; swap for Argon2PasswordEncoder if preferred.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API — no cookie-based session to protect
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Static frontend shell (HTML/CSS/JS) — no secrets live here;
                // real protection is enforced by the API calls each page makes,
                // which still require a valid JWT exactly as before.
                .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                // Pretty invitation URL (/i/{slug}) — forwards to invitation.html
                // (see InvitationRedirectController). Same "no secrets here" logic
                // as the block above; must be public since guests aren't logged in.
                .requestMatchers("/i/**").permitAll()
                .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Without this, Spring Security's default for a request with no
            // (or an expired/invalid — see JwtAuthFilter) JWT is
            // Http403ForbiddenEntryPoint: a bare 403. api.js only treats 401
            // as "not logged in" and clears the session/redirects to
            // login.html on that — so a stale token left the guest stuck on
            // a page that could never recover on its own. 403 is kept for
            // AccessDeniedException (authenticated but lacking a role, e.g.
            // a regular admin hitting /api/super-admin/**), which is a
            // different problem the guest can't fix by re-logging in.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, authException) ->
                    writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                            "Your session has expired. Please sign in again."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                            "You do not have permission to perform this action"))
            );

        return http.build();
    }

    /**
     * Hand-written rather than routed through GlobalExceptionHandler: these
     * two responses come from the security filter chain itself, before the
     * request ever reaches DispatcherServlet, so @RestControllerAdvice can't
     * see them. error/message here are always our own fixed strings, never
     * request-derived, so no JSON-escaping is needed.
     */
    private static void writeJsonError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                        + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"details\":[]}");
    }
}
