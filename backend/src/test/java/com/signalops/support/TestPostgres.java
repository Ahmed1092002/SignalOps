package com.signalops.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/** Singleton PostgreSQL container shared by all integration tests in this JVM. */
public final class TestPostgres {

  private static PostgreSQLContainer<?> instance;

  private TestPostgres() {}

  public static synchronized PostgreSQLContainer<?> instance() {
    if (instance == null) {
      if (!isDockerAvailable()) {
        throw new IllegalStateException("Docker is not available. Integration tests require Docker.");
      }
      var container = new PostgreSQLContainer<>("postgres:16-alpine");
      container.start();
      instance = container;
    }
    return instance;
  }

  public static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }
}
