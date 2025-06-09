package com.toyota.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Toyota Financial Data Platform - Rate Entity for OpenSearch
 * 
 * Data model representing financial rate information for OpenSearch indexing.
 * Lightweight structure optimized for search and analytics operations
 * within the Toyota financial data platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateEntity {
    private Long id;
    private String rateName;
    private BigDecimal bid;
    private BigDecimal ask;
    private LocalDateTime rateUpdatetime;
    private LocalDateTime dbUpdatetime;
    private String pipelineId;
    private String rateCategory;
}