package com.signalops.publicstatus.web;

import com.signalops.publicstatus.app.PublicStatusService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated status endpoints. Payloads are allowlisted by the app service. */
@RestController
public class PublicStatusController {

  private final PublicStatusService publicStatusService;

  public PublicStatusController(PublicStatusService publicStatusService) {
    this.publicStatusService = publicStatusService;
  }

  @GetMapping("/api/v1/public/status/{orgSlug}")
  public PublicStatusService.PublicStatusView status(@PathVariable String orgSlug) {
    return publicStatusService.status(orgSlug);
  }

  @GetMapping("/api/v1/public/status/{orgSlug}/incidents")
  public List<PublicStatusService.PublicPastIncident> history(
      @PathVariable String orgSlug, @RequestParam(defaultValue = "0") int page) {
    return publicStatusService.history(orgSlug, page);
  }
}
