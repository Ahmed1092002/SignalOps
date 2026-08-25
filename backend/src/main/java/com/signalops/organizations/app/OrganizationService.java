package com.signalops.organizations.app;

import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.shared.domain.Role;
import com.signalops.shared.domain.SlugGenerator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final MembershipRepository membershipRepository;
  private final SlugGenerator slugGenerator;

  @Transactional
  public Organization createWithOwner(String name, UUID ownerUserId) {
    Organization organization = new Organization();
    organization.setName(name);
    organization.setSlug(
        slugGenerator.uniqueSlug(slugGenerator.slugify(name), organizationRepository::existsBySlug));
    organizationRepository.save(organization);

    Membership membership = new Membership();
    membership.setOrganizationId(organization.getId());
    membership.setUserId(ownerUserId);
    membership.setRole(Role.OWNER);
    membership.setStatus(com.signalops.organizations.domain.MembershipStatus.ACTIVE);
    membershipRepository.save(membership);
    return organization;
  }
}
