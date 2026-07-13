package com.storelense.inventory.dto;

import java.math.BigDecimal;

public record BillItemDto(
        String      ean,
        String      productName,
        int         qty,
        BigDecimal  unitPrice
) {}
