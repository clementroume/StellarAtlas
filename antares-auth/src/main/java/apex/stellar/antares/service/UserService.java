package apex.stellar.antares.service;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.InvalidPasswordException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for user-related operations, including profile and preferences management. All
 * methods that modify data are transactional.
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  /**
   * Updates the core profile information (name, email) of a user.
   *
   * @param currentUser The user entity to update (from security context).
   * @param request The DTO with the new profile data.
   * @return The updated {@link UserResponse} DTO.
   */
  @Transactional
  public UserResponse updateProfile(User currentUser, ProfileUpdateRequest request) {

    userMapper.updateFromProfile(request, currentUser);

    return userMapper.toUserResponse(userRepository.save(currentUser));
  }

  /**
   * Updates the preferences (locale, theme) of a user.
   *
   * @param currentUser The user entity to update (from security context).
   * @param request The DTO with the new preferences data.
   * @return The updated {@link UserResponse} DTO.
   */
  @Transactional
  public UserResponse updatePreferences(User currentUser, PreferencesUpdateRequest request) {

    userMapper.updateFromPreferences(request, currentUser);

    return userMapper.toUserResponse(userRepository.save(currentUser));
  }

  /**
   * Changes the password for the currently authenticated user.
   *
   * @param request The DTO containing passwords.
   * @param currentUser The user entity to update (from security context).
   * @throws InvalidPasswordException if the current password is incorrect or if the new passwords
   *     do not match.
   */
  @Transactional
  public void changePassword(ChangePasswordRequest request, User currentUser) {

    if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPassword())) {
      throw new InvalidPasswordException("error.password.incorrect");
    }

    if (!request.newPassword().equals(request.confirmationPassword())) {
      throw new InvalidPasswordException("error.password.mismatch");
    }

    currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(currentUser);
  }
}
