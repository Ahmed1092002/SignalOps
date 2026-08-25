package com.signalops.support;

import org.testcontainers.containers.PostgreSQLContainer;

/** Singleton PostgreSQL container shared by all integration tests in this JVM. */
public final class TestPostgres {

  private static final PostgreSQLContainer<?> INSTANCE;

  static {
    INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");
    INSTANCE.start();
  }

  private TestPostgres() {}

  public static PostgreSQLContainer<?> instance() {
    return INSTANCE;
  }
}
