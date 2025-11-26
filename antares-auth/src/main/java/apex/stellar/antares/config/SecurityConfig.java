package apex.stellar.antares.config;

import static org.springframework.security.config.Customizer.withDefaults;

import apex.stellar.antares.model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Main web security configuration for the application.
 *
 * <p>This setup is designed for a stateless API using HttpOnly JWT cookies and "Double Submit
 * Cookie" CSRF protection.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables method-level security (e.g., @PreAuthorize)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtProperties jwtProperties;
  private final UserDetailsService userDetailsService;

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * Configures the primary security filter chain that applies to all HTTP requests.
   *
   * @param http The HttpSecurity object to configure.
   * @return The configured SecurityFilterChain.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {

    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(builder -> builder.secure(true).sameSite("Strict"));

    // Handler to ensure CSRF token is accessible by JavaScript (for the header)
    // but not sent as a request attribute.
    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
    requestHandler.setCsrfRequestAttributeName(null);

    http.cors(withDefaults()) // Enable CORS using the corsConfigurationSource bean
        .csrf(
            csrf ->
                csrf
                    // Use CookieCsrfTokenRepository, setting HttpOnly=false so Angular can read it.
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
                    // Disable CSRF protection for public auth endpoints and actuators
                    .ignoringRequestMatchers("/antares/auth/**", "/actuator/**"))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Secure Swagger/OpenAPI endpoints (admin only)
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    // Public endpoints
                    .requestMatchers("/antares/auth/**", "/actuator/**")
                    .permitAll()
                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session ->
                // Configure the application to be stateless (no HttpSession)
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Add the custom JWT filter before the standard username/password filter
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    // 1. Utilisation du resolver de cookie
                    .bearerTokenResolver(bearerTokenResolver())
                    // 2. Conversion du JWT en User entity
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(
                                    token -> {
                                      // On charge l'utilisateur depuis la DB via le 'sub' (email)
                                      // du token
                                      String email = token.getSubject();
                                      User user =
                                          (User) userDetailsService.loadUserByUsername(email);
                                      // On retourne un token standard Spring Security contenant
                                      // notre User
                                      return new UsernamePasswordAuthenticationToken(
                                          user, token, user.getAuthorities());
                                    })));

    return http.build();
  }

  /**
   * Custom Token Resolver qui cherche d'abord dans le cookie, puis dans le header (fallback
   * standard).
   */
  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    return new BearerTokenResolver() {
      private final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

      @Override
      public String resolve(HttpServletRequest request) {
        // Stratégie 1 : Chercher dans les cookies
        if (request.getCookies() != null) {
          for (Cookie cookie : request.getCookies()) {
            if (jwtProperties.accessToken().name().equals(cookie.getName())) {
              return cookie.getValue();
            }
          }
        }
        // Stratégie 2 : Fallback sur le header Authorization (utile pour Swagger/Postman)
        return defaultResolver.resolve(request);
      }
    };
  }

  /**
   * Creates and configures a {@link JwtDecoder} bean for decoding JWT tokens.
   *
   * <p>This method reads the secret key from {@link JwtProperties}, decodes it, and configures a
   * {@link NimbusJwtDecoder} with the specified secret key and HMAC SHA-256 algorithm.
   *
   * @return A {@link JwtDecoder} instance configured for processing JWT tokens.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secretKey());
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

    return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /**
   * Creates and configures a {@link JwtEncoder} bean for encoding JWT tokens.
   *
   * <p>This method uses the secret key stored in {@link JwtProperties} to initialize a {@link
   * NimbusJwtEncoder} with an {@link ImmutableSecret} key implementation.
   *
   * @return A {@link JwtEncoder} instance configured for encoding JWT tokens.
   */
  @Bean
  public JwtEncoder jwtEncoder() {
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secretKey());
    return new NimbusJwtEncoder(new ImmutableSecret<>(keyBytes));
  }

  /**
   * Defines the Cross-Origin Resource Sharing (CORS) configuration.
   *
   * <p>This bean allows the Angular frontend (running on '<a
   * href="https://antares.local">https://antares.local</a>') to make requests to this API.
   *
   * @return The CorsConfigurationSource.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of(this.allowedOrigins, "https://*.stellar.apex"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(Boolean.TRUE); // Crucial for sending/receiving cookies

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
