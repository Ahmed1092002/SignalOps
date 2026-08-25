package com.signalops.users.app;

import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.users.domain.User;
import com.signalops.users.domain.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Transactional
  public void changePassword(UUID userId, String newPasswordHash) {
    User user = load(userId);
    user.setPasswordHash(newPasswordHash);
  }

  @Transactional
  public void changeFullName(UUID userId, String fullName) {
    User user = load(userId);
    user.setFullName(fullName);
  }

  @Transactional(readOnly = true)
  public String passwordHashOf(UUID userId) {
    return load(userId).getPasswordHash();
  }

  private User load(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> ApiException.notFound("User not found."));
  }
}
