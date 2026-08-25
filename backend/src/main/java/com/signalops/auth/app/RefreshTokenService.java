package com.signalops.auth.app;

import com.signalops.auth.domain.RefreshTokenSession;
import com.signalops.auth.domain.RefreshTokenSessionRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  public record IssuedToken(String rawToken, RefreshTokenSession session) {}

  private final RefreshTokenSessionRepository repository;
  private final AppProperties properties;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();
  private final PlatformTransactionManager transactionManager;

  public RefreshTokenService(
      RefreshTokenSessionRepository repository, AppProperties properties, Clock clock,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.transactionManager = transactionManager;
  }

  @Transactional
  public IssuedToken issueFamily(UUID userId, String userAgent) {
    UUID familyId = UUID.randomUUID();
    return issueMemberOfFamily(userId, familyId, userAgent);
  }

  @Transactional
  public IssuedToken issueMemberOfFamily(UUID userId, UUID familyId, String userAgent) {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshTokenSession session = new RefreshTokenSession();
    session.setUserId(userId);
    session.setFamilyId(familyId);
    session.setTokenHash(hash(rawToken));
    session.setExpiresAt(Instant.now(clock).plus(properties.security().refreshTtlOrDefault()));
    session.setUserAgent(truncateUserAgent(userAgent));
    repository.save(session);
    return new IssuedToken(rawToken, session);
  }

  /**
   * Rotates the presented token. Reuse of a revoked token revokes the entire family (R22).
   * The revocation commits in its own transaction so it survives the rejecting request.
   */
  @Transactional
  public IssuedToken rotate(String rawToken, String userAgent) {
    RefreshTokenSession session =
        repository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.UNAUTHENTICATED, "Invalid refresh token.", 401));
    Instant now = Instant.now(clock);
    if (session.isRevoked()) {
      revokeFamilySurvivingRollback(session.getFamilyId());
      throw new ApiException(
          ErrorCodes.REFRESH_REUSE_DETECTED,
          "Refresh token was already used; the session has been revoked.",
          401);
    }
    if (session.isExpired(now)) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Refresh token expired.", 401);
    }
    IssuedToken next = issueMemberOfFamily(session.getUserId(), session.getFamilyId(), userAgent);
    session.setRevokedAt(now);
    session.setReplacedById(next.session().getId());
    repository.save(session);
    return next;
  }

  /**
   * Revokes a token family in an independent, immediately-committed transaction. Called on
   * reuse detection: the revocation must survive the (rolled back) rejecting request. A direct
   * self-invocation of a REQUIRES_NEW method would be ignored by the Spring proxy, so this uses
   * a TransactionTemplate explicitly.
   */
  public void revokeFamilySurvivingRollback(UUID familyId) {
    org.springframework.transaction.support.TransactionTemplate template =
        new org.springframework.transaction.support.TransactionTemplate(
            transactionManager,
            new org.springframework.transaction.support.DefaultTransactionDefinition(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    template.executeWithoutResult(
        status -> repository.revokeFamily(familyId, Instant.now(clock)));
  }

  @Transactional
  public void revokeFamilyOf(String rawToken) {
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            session -> repository.revokeFamily(session.getFamilyId(), Instant.now(clock)));
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private String truncateUserAgent(String userAgent) {
    if (userAgent == null) {
      return null;
    }
    return userAgent.length() <= 180 ? userAgent : userAgent.substring(0, 180);
  }
}
