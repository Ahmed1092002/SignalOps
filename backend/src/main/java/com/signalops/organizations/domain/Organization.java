package com.signalops.organizations.domain;

import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;
}
