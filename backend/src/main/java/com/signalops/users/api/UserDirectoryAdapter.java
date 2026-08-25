package com.signalops.users.api;

import com.signalops.users.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserDirectoryAdapter implements UserDirectory {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserSummary> findSummary(UUID userId) {
    return userRepository
        .findById(userId)
        .map(user -> new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.isActive()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserSummary> findSummaryByEmail(String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(user -> new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.isActive()));
  }
}
