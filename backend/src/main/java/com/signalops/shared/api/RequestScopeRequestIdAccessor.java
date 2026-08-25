package com.signalops.shared.api;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestScopeRequestIdAccessor implements RequestIdAccessor {

  @Override
  public String current() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      Object requestId = attributes.getRequest().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
      return requestId instanceof String value ? value : null;
    }
    return null;
  }
}
