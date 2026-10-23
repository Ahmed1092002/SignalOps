package com.signalops.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Skips integration tests when a local Docker daemon is not running or accessible. */
public class DockerAvailableCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (TestPostgres.isDockerAvailable()) {
      return ConditionEvaluationResult.enabled("Docker is available");
    }
    return ConditionEvaluationResult.disabled(
        "Docker is unavailable; skipping Docker-dependent integration test");
  }
}
