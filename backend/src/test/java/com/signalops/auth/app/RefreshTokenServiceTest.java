package com.signalops.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.auth.domain.RefreshTokenSession;
import com.signalops.auth.domain.RefreshTokenSessionRepository;
import com.signalops.shared.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class RefreshTokenServiceTest {

  private RefreshTokenSessionRepository repository;
  private PlatformTransactionManager transactionManager;
  private Clock clock;
  private RefreshTokenService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID familyId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(RefreshTokenSessionRepository.class);
    when(repository.save(any(RefreshTokenSession.class)))
        .thenAnswer(inv -> {
          RefreshTokenSession s = inv.getArgument(0);
          org.springframework.test.util.ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
          return s;
        });
    transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    clock = Clock.systemUTC();

    AppProperties properties =
        new AppProperties(
            new AppProperties.Security("dummy-secret", Duration.ofMinutes(15), Duration.ofDays(7), false, java.util.List.of()),
            AppProperties.Seed.defaults(),
            AppProperties.RateLimit.defaults());

    service = new RefreshTokenService(repository, properties, clock, transactionManager);
  }

  @Test
  void rotateUsesPessimisticLockAndFlushesSuccessor() {
    String rawToken = "valid-token-123";
    String tokenHash = service.hash(rawToken);

    RefreshTokenSession session = new RefreshTokenSession();
    session.setUserId(userId);
    session.setFamilyId(familyId);
    session.setTokenHash(tokenHash);
    session.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

    when(repository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(session));

    var next = service.rotate(rawToken, "test-agent");

    assertThat(next.rawToken()).isNotEmpty();
    assertThat(session.isRevoked()).isTrue();
    assertThat(session.getReplacedById()).isNotNull();
    verify(repository).saveAndFlush(session);
  }

  @Test
  void concurrentRotationSimulatingLockSerializationOnlyOneSucceedsAndFamilyRevoked() throws Exception {
    String rawToken = "concurrent-token";
    String tokenHash = service.hash(rawToken);

    RefreshTokenSession session = new RefreshTokenSession();
    session.setUserId(userId);
    session.setFamilyId(familyId);
    session.setTokenHash(tokenHash);
    session.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

    // Under database-level locking, the row lock is held until the transaction commits/flushes.
    // Thread 1 acquires lock on findByTokenHashWithLock and releases on saveAndFlush.
    // Thread 2 waits on the lock, and upon acquiring it sees session.isRevoked() == true.
    java.util.concurrent.locks.ReentrantLock rowLock = new java.util.concurrent.locks.ReentrantLock();
    when(repository.findByTokenHashWithLock(tokenHash))
        .thenAnswer(
            invocation -> {
              rowLock.lock();
              return Optional.of(session);
            });
    when(repository.saveAndFlush(any(RefreshTokenSession.class)))
        .thenAnswer(
            invocation -> {
              RefreshTokenSession s = invocation.getArgument(0);
              if (rowLock.isHeldByCurrentThread()) {
                rowLock.unlock();
              }
              return s;
            });

    int count = 2;
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger reuseCount = new AtomicInteger();

    Future<?> f1 =
        executor.submit(
            () -> {
              try {
                ready.countDown();
                start.await();
                service.rotate(rawToken, "agent-1");
                successCount.incrementAndGet();
              } catch (RefreshTokenService.RefreshTokenReuseException ex) {
                service.revokeFamilySurvivingRollback(ex.familyId());
                reuseCount.incrementAndGet();
              } catch (Exception ignored) {
              } finally {
                if (rowLock.isHeldByCurrentThread()) {
                  rowLock.unlock();
                }
              }
            });

    Future<?> f2 =
        executor.submit(
            () -> {
              try {
                ready.countDown();
                start.await();
                service.rotate(rawToken, "agent-2");
                successCount.incrementAndGet();
              } catch (RefreshTokenService.RefreshTokenReuseException ex) {
                service.revokeFamilySurvivingRollback(ex.familyId());
                reuseCount.incrementAndGet();
              } catch (Exception ignored) {
              } finally {
                if (rowLock.isHeldByCurrentThread()) {
                  rowLock.unlock();
                }
              }
            });

    ready.await();
    start.countDown();

    f1.get(5, TimeUnit.SECONDS);
    f2.get(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(reuseCount.get()).isEqualTo(1);
    verify(repository).revokeFamily(eq(familyId), any());
  }
}
