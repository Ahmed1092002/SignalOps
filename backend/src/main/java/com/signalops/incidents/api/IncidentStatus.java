package com.signalops.incidents.api;

public enum IncidentStatus {
  DETECTED,
  INVESTIGATING,
  IDENTIFIED,
  MONITORING,
  RESOLVED;

  public boolean isActive() {
    return this != RESOLVED;
  }
}
