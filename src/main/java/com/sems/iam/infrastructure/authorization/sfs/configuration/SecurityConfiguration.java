package com.sems.iam.infrastructure.authorization.sfs.configuration;

import com.sems.iam.infrastructure.authorization.sfs.pipeline.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Value("${app.security.auth-required:true}")
    private boolean authRequired;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var authConfigurer = http
                // Sin esto, Spring Security nunca inserta su filtro de CORS y
                // el navegador recibe 403 en la peticion de comprobacion
                // (OPTIONS) de CUALQUIER origen, incluido localhost. Desde el
                // navegador parece que el backend esta caido, pero responde
                // perfectamente a curl: curl no manda esa comprobacion previa.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/health").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers("/api/v1/auth/**").permitAll();
                    // Stripe llama al webhook sin token: la peticion se
                    // autentica por la firma del cuerpo, no por JWT. Si esta
                    // ruta exige token, los cobros nunca se confirman.
                    auth.requestMatchers("/api/v1/webhooks/**").permitAll();
                    auth.requestMatchers(
                            "/swagger-ui/**",
                            "/webjars/**",
                            "/v3/api-docs/**",
                            "/v3/api-docs",
                            "/swagger-ui.html")
                        .permitAll();
                    if (authRequired) {
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                });

        if (authRequired) {
            authConfigurer.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Origenes que pueden llamar a esta API desde un navegador.
     *
     * <p>Spring Security recoge este bean por su nombre al declarar
     * {@code .cors(...)} en la cadena de filtros. Los origenes salen de
     * {@code app.cors.allowed-origins}, que a su vez lee la variable de entorno
     * ALLOWED_ORIGINS.</p>
     *
     * <p>La lista es explicita y no un comodin porque se permiten credenciales:
     * el navegador rechaza la combinacion de "*" con
     * {@code allowCredentials(true)}.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                      .map(String::trim)
                      .filter(origin -> !origin.isEmpty())
                      .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
