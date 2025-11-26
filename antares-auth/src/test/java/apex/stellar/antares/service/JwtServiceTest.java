package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;

import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.model.Role;
import apex.stellar.antares.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unit tests for {@link JwtService}. Verifies token generation, validation, and claim extraction.
 */
class JwtServiceTest {
  private JwtService jwtService;
  private UserDetails userDetails;
  private String secretKey;

  @BeforeEach
  void setUp() throws JOSEException {
    // Given
    secretKey =
        "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==";

    JwtProperties jwtProperties =
        new JwtProperties(
            secretKey,
            "test-issuer",
            "test-audience",
            new JwtProperties.AccessToken(900000L, "access_token"),
            new JwtProperties.RefreshToken(604800000L, "refresh_token"),
            new JwtProperties.CookieProperties(false, "stellar.apex"));

    jwtService = new JwtService(jwtProperties);
    jwtService.init(); // Initialise le signer/verifier Nimbus

    userDetails =
        User.builder().email("test@example.com").password("password").role(Role.ROLE_USER).build();
  }

  @Test
  @DisplayName("generateToken: should create a valid token with correct claims")
  void testGenerateToken_shouldCreateValidToken() throws Exception {
    // When
    String token = jwtService.generateToken(userDetails);

    // Then
    assertNotNull(token);

    SignedJWT signedJWT = SignedJWT.parse(token);
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

    assertEquals("test@example.com", claims.getSubject());
    assertEquals("test-issuer", claims.getIssuer());
    assertTrue(claims.getAudience().contains("test-audience"));
    assertNotNull(claims.getJWTID());
    assertNotNull(claims.getIssueTime());
    assertNotNull(claims.getExpirationTime());
  }

  @Test
  @DisplayName("isTokenValid: should return true for a valid token")
  void testIsTokenValid_withValidToken_shouldReturnTrue() {
    // Given
    String token = jwtService.generateToken(userDetails);

    // When & Then
    // This test primarily asserts that no exception is thrown
    assertDoesNotThrow(() -> jwtService.isTokenValid(token, userDetails));
  }

  @Test
  @DisplayName("isTokenValid: should return false (not throw) for an expired token")
  void testIsTokenValid_withExpiredToken_shouldReturnFalse() throws Exception {
    // Given: A token manually created with an expiration date in the past.
    Date past = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userDetails.getUsername())
            .issuer("test-issuer")
            .audience("test-audience")
            .issueTime(past)
            .expirationTime(past) // Expiration dans le passé
            .jwtID(UUID.randomUUID().toString())
            .build();

    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    signedJWT.sign(new MACSigner(Base64.getDecoder().decode(secretKey)));

    String expiredToken = signedJWT.serialize();

    // When & Then
    assertFalse(jwtService.isTokenValid(expiredToken, userDetails));
  }

  @Test
  @DisplayName("isTokenValid: should return false for a token belonging to a different user")
  void testIsTokenValid_withDifferentUser_shouldReturnFalse() {
    // Given
    String token = jwtService.generateToken(userDetails);
    UserDetails anotherUser = User.builder().email("another@example.com").build();

    // When & Then
    assertFalse(jwtService.isTokenValid(token, anotherUser));
  }
}
