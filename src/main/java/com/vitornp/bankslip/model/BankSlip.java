package com.vitornp.bankslip.model;

import com.vitornp.bankslip.dto.BankSlipStatus;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Getter
public class BankSlip {

    @Default
    private UUID id = UUID.randomUUID();

    private LocalDate dueDate;

    private BigDecimal totalInCents;

    private String customer;

    @Default
    private BankSlipStatus status = BankSlipStatus.PENDING;

    @Default
    private Instant createdAt = Instant.now();

}
