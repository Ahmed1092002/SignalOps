package com.signalops.organizations.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signalops.organizations.api.OrganizationDtos.MembershipView;
import com.signalops.organizations.app.InvitationService;
import com.signalops.organizations.app.MemberService;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.domain.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizationControllerTest {

  private MemberService memberService;
  private InvitationService invitationService;
  private OrganizationController controller;

  @BeforeEach
  void setUp() {
    memberService = mock(MemberService.class);
    invitationService = mock(InvitationService.class);
    controller = new OrganizationController(memberService, invitationService);
  }

  private List<MembershipView> sampleMembers(int count) {
    List<MembershipView> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(
          new MembershipView(
              UUID.randomUUID(),
              UUID.randomUUID(),
              "User " + i,
              "user" + i + "@example.com",
              Role.RESPONDER,
              "ACTIVE"));
    }
    return list;
  }

  @Test
  void negativePageNormalizedToZero() {
    when(memberService.listMembers()).thenReturn(sampleMembers(5));

    PageResponse<MembershipView> response = controller.members(-1, 10);
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.items()).hasSize(5);
  }

  @Test
  void zeroOrNegativeSizeNormalizedToOne() {
    when(memberService.listMembers()).thenReturn(sampleMembers(5));

    PageResponse<MembershipView> responseZero = controller.members(0, 0);
    assertThat(responseZero.size()).isEqualTo(1);
    assertThat(responseZero.items()).hasSize(1);

    PageResponse<MembershipView> responseNeg = controller.members(0, -10);
    assertThat(responseNeg.size()).isEqualTo(1);
    assertThat(responseNeg.items()).hasSize(1);
  }

  @Test
  void sizeCappedAtHundred() {
    when(memberService.listMembers()).thenReturn(sampleMembers(150));

    PageResponse<MembershipView> response = controller.members(0, 500);
    assertThat(response.size()).isEqualTo(100);
    assertThat(response.items()).hasSize(100);
    assertThat(response.totalElements()).isEqualTo(150);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void pageBeyondFinalPageReturnsEmptyList() {
    when(memberService.listMembers()).thenReturn(sampleMembers(5));

    PageResponse<MembershipView> response = controller.members(10, 10);
    assertThat(response.page()).isEqualTo(10);
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.items()).isEmpty();
    assertThat(response.totalElements()).isEqualTo(5);
    assertThat(response.totalPages()).isEqualTo(1);
  }

  @Test
  void hugeValuesDoNotOverflowInteger() {
    when(memberService.listMembers()).thenReturn(sampleMembers(5));

    PageResponse<MembershipView> response = controller.members(Integer.MAX_VALUE, Integer.MAX_VALUE);
    assertThat(response.page()).isEqualTo(Integer.MAX_VALUE);
    assertThat(response.size()).isEqualTo(100);
    assertThat(response.items()).isEmpty();
    assertThat(response.totalElements()).isEqualTo(5);
  }

  @Test
  void normalPagingSlicesCorrectly() {
    when(memberService.listMembers()).thenReturn(sampleMembers(25));

    PageResponse<MembershipView> page0 = controller.members(0, 10);
    assertThat(page0.items()).hasSize(10);
    assertThat(page0.page()).isEqualTo(0);
    assertThat(page0.totalPages()).isEqualTo(3);

    PageResponse<MembershipView> page2 = controller.members(2, 10);
    assertThat(page2.items()).hasSize(5);
    assertThat(page2.page()).isEqualTo(2);
  }
}
