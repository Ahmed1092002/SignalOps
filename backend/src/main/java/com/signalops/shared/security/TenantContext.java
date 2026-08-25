package com.signalops.shared.security;

public final class TenantContext {

  private static final ThreadLocal<TenantContextValue> HOLDER = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(TenantContextValue value) {
    HOLDER.set(value);
  }

  public static TenantContextValue require() {
    TenantContextValue value = HOLDER.get();
    if (value == null) {
      throw new IllegalStateException("Tenant context is not available for this request");
    }
    return value;
  }

  public static TenantContextValue get() {
    return HOLDER.get();
  }

  public static void clear() {
    HOLDER.remove();
  }
}
