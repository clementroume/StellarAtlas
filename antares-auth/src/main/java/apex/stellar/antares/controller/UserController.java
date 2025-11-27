package apex.stellar.antares.controller;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing the currently authenticated user's account. All endpoints in this
 * controller are protected and require a valid session.
 */
@RestController
@RequestMapping("/antares/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final UserMapper userMapper;

  /**
   * Handles GET requests to retrieve the profile of the currently authenticated user. The user is
   * identified via the Authentication principal injected by Spring Security.
   *
   * @param authentication The authentication object containing the user's principal.
   * @return A ResponseEntity containing the {@link UserResponse} for the current user.
   */
  @GetMapping("/me")
  public ResponseEntity<@NonNull UserResponse> getAuthenticatedUser(Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userMapper.toUserResponse(currentUser));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PUT requests to update the authenticated user's core profile information.
   *
   * @param request The DTO with the updated user data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PutMapping("/me/profile")
  public ResponseEntity<@NonNull UserResponse> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userService.updateProfile(currentUser, request));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PATCH requests to partially update the authenticated user's preferences.
   *
   * @param request The DTO with the updated preferences data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PatchMapping("/me/preferences")
  public ResponseEntity<@NonNull UserResponse> updatePreferences(
      @Valid @RequestBody PreferencesUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userService.updatePreferences(currentUser, request));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PUT requests to change the authenticated user's password.
   *
   * @param request The DTO with the current, new, and confirmation passwords, validated.
   * @param authentication The current user's authentication principal.
   * @return An empty ResponseEntity (200 OK) confirming success.
   */
  @PutMapping("/me/password")
  public ResponseEntity<@NonNull Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      userService.changePassword(request, currentUser);
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}
