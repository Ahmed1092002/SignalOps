package com.signalops.incidents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IncidentKeySequenceTest {

  private JdbcTemplate jdbcTemplate;
  private IncidentKeySequence sequence;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    sequence = new IncidentKeySequence(jdbcTemplate);
  }

  @Test
  void nextNumberExecutesAtomicUpsertAndReturnsSequence() {
    UUID orgId = UUID.randomUUID();

    when(jdbcTemplate.queryForObject(
            any(String.class), eq(Integer.class), eq(orgId), eq(orgId)))
        .thenReturn(42);

    int number = sequence.nextNumber(orgId);

    assertThat(number).isEqualTo(42);
  }

  @Test
  void nextNumberDefaultsToOneWhenNullReturned() {
    UUID orgId = UUID.randomUUID();

    when(jdbcTemplate.queryForObject(
            any(String.class), eq(Integer.class), eq(orgId), eq(orgId)))
        .thenReturn(null);

    int number = sequence.nextNumber(orgId);

    assertThat(number).isEqualTo(1);
  }
}
