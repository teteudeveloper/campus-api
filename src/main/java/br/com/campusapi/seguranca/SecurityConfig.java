package br.com.campusapi.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Cadeia de segurança stateless com negação por padrão.
 *
 * <p>CSRF: desabilitado de forma deliberada. As credenciais trafegam somente no cabeçalho Authorization
 * (access token) e no corpo JSON (refresh token); a API não usa cookies nem sessão, portanto o navegador não
 * anexa credenciais automaticamente a requisições forjadas, que é a condição para um ataque CSRF. Se algum
 * dia o refresh token passar a usar cookie, a proteção CSRF deve ser reativada para /auth/refresh e /auth/logout.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SegurancaProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder jwtDecoder,
                                            JwtUsuarioConverter jwtUsuarioConverter,
                                            ProblemaSegurancaHandler problemaHandler,
                                            @Value("${springdoc.api-docs.enabled:false}") boolean documentacaoHabilitada)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> {
                    regras.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/logout").permitAll();
                    regras.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                            "/actuator/health/readiness").permitAll();
                    if (documentacaoHabilitada) {
                        regras.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    regras.requestMatchers("/error").permitAll();
                    regras.requestMatchers("/auth/**", "/usuarios/**", "/alunos/**", "/professores/**",
                            "/disciplinas/**", "/matriculas/**").authenticated();
                    regras.anyRequest().denyAll();
                })
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtUsuarioConverter))
                        .authenticationEntryPoint(problemaHandler)
                        .accessDeniedHandler(problemaHandler))
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(problemaHandler)
                        .accessDeniedHandler(problemaHandler));
        return http.build();
    }

    /**
     * Ignora o cabeçalho Authorization em /auth/login, /auth/refresh e /auth/logout: um access token vencido
     * enviado por engano não deve impedir a renovação ou o logout.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver padrao = new DefaultBearerTokenResolver();
        Set<String> publicos = Set.of("/auth/login", "/auth/refresh", "/auth/logout");
        return request -> publicos.contains(request.getRequestURI()) ? null : padrao.resolve(request);
    }

    /** BCrypt via DelegatingPasswordEncoder: o prefixo {bcrypt} permite migrar o algoritmo no futuro. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SegurancaProperties properties) {
        List<String> origens = properties.cors().allowedOrigins();
        if (origens.stream().anyMatch(origem -> origem.contains("*"))) {
            throw new IllegalStateException("app.security.cors.allowed-origins não aceita curingas; liste as origens explicitamente");
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origens.isEmpty()) {
            return source;
        }
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origens);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuracao.setExposedHeaders(List.of("X-Correlation-Id", "Location", "Retry-After"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/**", configuracao);
        return source;
    }
}
