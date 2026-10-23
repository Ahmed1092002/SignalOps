package com.signalops.organizations.web;

import com.signalops.organizations.api.OrganizationDtos;
import com.signalops.organizations.app.InvitationService;
import com.signalops.organizations.app.MemberService;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

  private final MemberService memberService;
  private final InvitationService invitationService;

  public OrganizationController(MemberService memberService, InvitationService invitationService) {
    this.memberService = memberService;
    this.invitationService = invitationService;
  }

  @GetMapping("/organizations/mine")
  public List<OrganizationDtos.OrganizationView> mine(@AuthenticationPrincipal UserPrincipal principal) {
    return memberService.myOrganizations(principal.userId());
  }

  @GetMapping("/organizations/current")
  public OrganizationDtos.OrganizationView current() {
    return memberService.currentOrganization();
  }

  @PatchMapping("/organizations/current")
  public OrganizationDtos.OrganizationView updateCurrent(
      @Valid @RequestBody OrganizationDtos.UpdateOrganizationRequest request) {
    return memberService.updateOrganization(request.name());
  }

  @GetMapping("/organizations/current/members")
  public PageResponse<OrganizationDtos.MembershipView> members(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.clamp(size, 1, 100);
    List<OrganizationDtos.MembershipView> all = memberService.listMembers();
    int total = all.size();
    long fromIndex = (long) safePage * safeSize;
    if (fromIndex >= total) {
      return PageResponse.of(List.of(), safePage, safeSize, total);
    }
    int from = (int) fromIndex;
    int to = (int) Math.min((long) total, fromIndex + safeSize);
    return PageResponse.of(all.subList(from, to), safePage, safeSize, total);
  }

  @PatchMapping("/members/{memberId}")
  public OrganizationDtos.MembershipView updateMember(
      @PathVariable UUID memberId,
      @Valid @RequestBody OrganizationDtos.UpdateMemberRequest request) {
    return memberService.updateMember(
        memberId, request.role(), request.status() == null ? null : request.status().name());
  }

  @DeleteMapping("/members/{memberId}")
  public ResponseEntity<Void> removeMember(@PathVariable UUID memberId) {
    memberService.removeMember(memberId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/invitations")
  public ResponseEntity<OrganizationDtos.InvitationView> createInvitation(
      @Valid @RequestBody OrganizationDtos.InvitationCreateRequest request) {
    return ResponseEntity.status(201).body(invitationService.create(request));
  }

  @GetMapping("/invitations")
  public List<OrganizationDtos.InvitationView> pendingInvitations() {
    return invitationService.listPending();
  }

  @DeleteMapping("/invitations/{invitationId}")
  public ResponseEntity<Void> revokeInvitation(@PathVariable UUID invitationId) {
    invitationService.revoke(invitationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/invitations/accept")
  public OrganizationDtos.InvitationAcceptResponse acceptInvitation(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody OrganizationDtos.InvitationAcceptRequest request) {
    return invitationService.accept(principal.userId(), request);
  }


}
