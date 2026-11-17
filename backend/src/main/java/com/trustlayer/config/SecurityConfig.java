package com.trustlayer.config;

import com.trustlayer.security.JwtAuthenticationFilter;
import com.trustlayer.security.RestAccessDeniedHandler;
import com.trustlayer.security.RestAuthenticationEntryPoint;
import com.trustlayer.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * JWT bearer authentication with an HttpOnly refresh-token cookie protected by a CSRF token.
 * Every non-public route fails closed.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(SecurityProperties securityProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(securityProperties.cookies().secure())
                .sameSite(securityProperties.cookies().sameSite()));
        return repository;
    }

    /**
     * Browser extension requests are cross-origin by definition. This source deliberately grants
     * access only to the one installed extension origin configured by the operator. Normal web
     * browser traffic uses the same-origin Vite/reverse-proxy route and receives no CORS grant.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(ExtensionProperties extensionProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!extensionProperties.enabled() || !extensionProperties.hasRequiredConfiguration()) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(extensionProperties.origin()));
        configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(600L);
        source.registerCorsConfiguration("/api/extension/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CsrfTokenRepository csrfTokenRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        // The React client reads the non-HttpOnly XSRF cookie and sends its
                        // exact value in X-XSRF-TOKEN. The default XOR handler expects a
                        // masked value, which is appropriate for server-rendered forms but
                        // would reject this documented SPA cookie/header contract.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // These six endpoints are deliberately anonymous entry points. Every
                        // state-changing endpoint that relies on the browser refresh cookie
                        // (refresh, logout, logout-all, profile changes, deletion) still
                        // requires a valid CSRF token.
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                // The token exchange and scoped extension scan never rely on a
                                // browser cookie. They use one-time PKCE or a bearer token.
                                "/api/extension/token",
                                "/api/extension/scan-url"
                        ))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/extension/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/extension/token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/extension/authorizations").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/extension/scan-url").hasAuthority("SCOPE_URL_SCAN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout-all").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers("/api/scanner/**").authenticated()
                        .requestMatchers("/api/transactions/**").authenticated()
                        .requestMatchers("/api/threats/**").authenticated()
                        .requestMatchers("/api/dashboard/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(contentSecurityPolicy -> contentSecurityPolicy
                                .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .referrerPolicy(referrerPolicy -> referrerPolicy.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissionsPolicy -> permissionsPolicy
                                .policy("geolocation=(), microphone=(), camera=()"))
                        .contentTypeOptions(Customizer.withDefaults()))
                .build();
    }
}
