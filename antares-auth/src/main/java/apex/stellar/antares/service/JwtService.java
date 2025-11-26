package apex.stellar.antares.service;

import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.exception.InvalidTokenException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

/** Service for handling JWT (JSON Web Token) creation, validation, and extraction logic. */
@Service
@Getter
@Slf4j
@RequiredArgsConstructor
public class JwtService {

  private final JwtProperties jwtProperties;
  private JWSSigner signer;
  private JWSVerifier verifier;

  public String getAccessTokenCookieName() {
    return jwtProperties.accessToken().name();
  }

  public String getRefreshTokenCookieName() {
    return jwtProperties.refreshToken().name();
  }

  public long getAccessTokenDurationMs() {
    return jwtProperties.accessToken().expiration();
  }

  public long getRefreshTokenDurationMs() {
    return jwtProperties.refreshToken().expiration();
  }

  /** Initializes the service by decoding the Base64 secret key. */
  @PostConstruct
  public void init() throws JOSEException {

    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secretKey());
    this.signer = new MACSigner(keyBytes);
    this.verifier = new MACVerifier(keyBytes);
  }

  /**
   * Retrieves a JWT token from the cookies of an HTTP request.
   *
   * @param request The HTTP request.
   * @param cookieName The name of the cookie to retrieve.
   * @return The JWT token string if present, otherwise null.
   */
  public String getJwtFromCookies(HttpServletRequest request, String cookieName) {
    Cookie cookie = WebUtils.getCookie(request, cookieName);
    return cookie != null ? cookie.getValue() : null;
  }

  /**
   * Extracts the username (subject) from the JWT token.
   *
   * @param token The JWT token.
   * @return The username (email).
   */
  public String extractUsername(String token) {
    return extractClaim(token, JWTClaimsSet::getSubject);
  }

  /**
   * Extracts a specific claim from the JWT token using a claims resolver function.
   *
   * @param token The JWT token.
   * @param claimsResolver A function to extract the desired claim.
   * @param <T> The type of the claim.
   * @return The extracted claim.
   */
  public <T> T extractClaim(String token, Function<JWTClaimsSet, T> claimsResolver) {
    final JWTClaimsSet claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  /**
   * Generates a standard access token for the given user.
   *
   * @param userDetails The user details.
   * @return The generated JWT access token.
   */
  public String generateToken(UserDetails userDetails) {
    return buildToken(new HashMap<>(), userDetails, getAccessTokenDurationMs());
  }

  /**
   * Builds a JWT token with the specified claims, subject, and expiration.
   *
   * @param extraClaims Additional claims to include.
   * @param userDetails The user (subject) of the token.
   * @param expiration The expiration time in milliseconds.
   * @return The compact, signed JWT string.
   */
  @SuppressWarnings("checkstyle:CatchParameterName")
  public String buildToken(
      Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
    try {
      long now = System.currentTimeMillis();

      JWTClaimsSet.Builder claimsBuilder =
          new JWTClaimsSet.Builder()
              .subject(userDetails.getUsername())
              .issuer(jwtProperties.issuer())
              .audience(jwtProperties.audience())
              .jwtID(UUID.randomUUID().toString())
              .issueTime(new Date(now))
              .expirationTime(new Date(now + expiration));

      extraClaims.forEach(claimsBuilder::claim);

      SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsBuilder.build());
      signedJwt.sign(signer);

      return signedJwt.serialize();
    } catch (JOSEException _) {
      throw new InvalidTokenException("error.token.creation");
    }
  }

  /**
   * Validates the JWT token against the provided user details.
   *
   * @param token The JWT token to validate.
   * @param userDetails The user details to validate against.
   * @return true if the token is valid and belongs to the user.
   */
  public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
  }

  /**
   * Checks if the JWT token is expired.
   *
   * @param token The JWT token.
   * @return true if the token is expired.
   * @throws InvalidTokenException if the token is malformed or invalid.
   */
  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  /**
   * Extracts the expiration date from the JWT token.
   *
   * @param token The JWT token.
   * @return The expiration date.
   */
  private Date extractExpiration(String token) {
    return extractClaim(token, JWTClaimsSet::getExpirationTime);
  }

  /**
   * Extracts all claims from the JWT token after verifying its signature, issuer, and audience.
   *
   * @param token The JWT token.
   * @return The Claims object.
   * @throws InvalidTokenException if the token fails' validation.
   */
  @SuppressWarnings("checkstyle:CatchParameterName")
  private JWTClaimsSet extractAllClaims(String token) {
    try {
      SignedJWT signedJwt = SignedJWT.parse(token);

      if (!signedJwt.verify(verifier)) {
        throw new InvalidTokenException("error.token.invalid.signature");
      }

      JWTClaimsSet claims = signedJwt.getJWTClaimsSet();

      if (!jwtProperties.issuer().equals(claims.getIssuer())) {
        throw new InvalidTokenException("error.token.invalid.issuer");
      }
      // Nimbus gère l'audience comme une liste
      if (claims.getAudience() == null
          || !claims.getAudience().contains(jwtProperties.audience())) {
        throw new InvalidTokenException("error.token.invalid.audience");
      }

      return claims;
    } catch (ParseException | JOSEException _) {
      throw new InvalidTokenException("error.token.invalid");
    }
  }
}
