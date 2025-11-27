package apex.stellar.antares.controller;

import static java.nio.charset.StandardCharsets.UTF_8;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.dto.TokenRefreshResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.model.Role;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.AuthenticationService;
import apex.stellar.antares.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling user authentication endpoints such as registration, login, logout,
 * and token refresh.
 */
@RestController
@RequestMapping("/antares/auth")
@RequiredArgsConstructor
public class AuthenticationController {

  private final AuthenticationService authenticationService;
  private final JwtService jwtService;

  @Value("${application.frontend.login.url}")
  private String loginBaseUrl;

  /**
   * Handles POST requests to register a new user.
   *
   * @param request The registration request DTO, validated.
   * @param response The HTTP response, used to set auth cookies.
   * @return A ResponseEntity with status 201 (Created) and the new {@link UserResponse}.
   */
  @PostMapping("/register")
  public ResponseEntity<@NonNull UserResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authenticationService.register(request, response));
  }

  /**
   * Handles POST requests to authenticate (login) a user.
   *
   * @param request The authentication request DTO, validated.
   * @param response The HTTP response, used to set auth cookies.
   * @return A ResponseEntity with status 200 (OK) and the authenticated {@link UserResponse}.
   */
  @PostMapping("/login")
  public ResponseEntity<@NonNull UserResponse> login(
      @Valid @RequestBody AuthenticationRequest request, HttpServletResponse response) {

    return ResponseEntity.ok(authenticationService.login(request, response));
  }

  /**
   * Handles POST requests to log out the currently authenticated user.
   *
   * @param authentication The Spring Security authentication object (injected).
   * @param response The HTTP response, used to clear auth cookies.
   * @return A ResponseEntity with status 200 (OK).
   */
  @PostMapping("/logout")
  public ResponseEntity<@NonNull Void> logout(
      Authentication authentication, HttpServletResponse response) {

    User currentUser = (User) authentication.getPrincipal();
    authenticationService.logout(currentUser, response);

    return ResponseEntity.ok().build();
  }

  /**
   * Advanced Forward Auth Endpoint.
   *
   * <p>This method acts as a gatekeeper for infrastructure services (Traefik Dashboard, Vega
   * Admin).
   *
   * <p>Logic Flow:
   *
   * <ol>
   *   <li>If Unauthenticated -> Return 302 Redirect to the Sirius Login Page.
   *   <li>If Authenticated but not ADMIN -> Return 403 Forbidden.
   *   <li>If Authenticated and ADMIN -> Return 200 OK (Access Granted).
   * </ol>
   */
  @GetMapping("/verify")
  public ResponseEntity<@NonNull Void> verify(
      HttpServletRequest request, Authentication authentication) {

    if (authentication == null || !authentication.isAuthenticated()) {

      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, buildLoginRedirectUrl(request))
          .build();
    }

    if (authentication.getPrincipal() instanceof User user) {

      return user.getRole() == Role.ROLE_ADMIN
          ? ResponseEntity.ok().build()
          : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  /**
   * Handles POST requests to refresh an expired access token using a refresh token. The refresh
   * token is read from an HttpOnly cookie.
   *
   * @param request The HTTP request, used to read cookies.
   * @param response The HTTP response, used to set new auth cookies.
   * @return A ResponseEntity with status 200 (OK) and the {@link TokenRefreshResponse}.
   * @throws ResourceNotFoundException if the refresh token cookie is missing.
   */
  @PostMapping("/refresh-token")
  public ResponseEntity<@NonNull TokenRefreshResponse> refreshToken(
      HttpServletRequest request, HttpServletResponse response) {

    String oldRefreshToken =
        jwtService.getJwtFromCookies(request, jwtService.getRefreshTokenCookieName());

    if (oldRefreshToken == null) {
      throw new ResourceNotFoundException("error.token.refresh.missing");
    }

    return ResponseEntity.ok(authenticationService.refreshToken(oldRefreshToken, response));
  }

  /**
   * Helper to construct the Angular login URL with a returnUrl parameter. It uses the X-Forwarded-*
   * headers provided by Traefik to know where the user came from.
   */
  private String buildLoginRedirectUrl(HttpServletRequest request) {

    String proto = request.getHeader("X-Forwarded-Proto");
    String host = request.getHeader("X-Forwarded-Host");
    String uri = request.getHeader("X-Forwarded-Uri");

    if (proto != null && host != null) {
      String originalUrl = proto + "://" + host + (uri != null ? uri : "");
      String encodedUrl = URLEncoder.encode(originalUrl, UTF_8);
      return loginBaseUrl + "?returnUrl=" + encodedUrl;
    }

    return loginBaseUrl;
  }
}
