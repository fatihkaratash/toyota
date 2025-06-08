package com.toyota.consumer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rates",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"rate_name", "rate_updatetime"})
       }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_name", nullable = false)
    private String rateName;  // Will store PF1_USDTRY, PF2_EURUSD, USD/TRY_AVG etc.

    @Column(precision = 19, scale = 8)
    private BigDecimal bid;

    @Column(precision = 19, scale = 8)
    private BigDecimal ask;

    @Column(name = "rate_updatetime")
    private LocalDateTime rateUpdatetime;  // The timestamp from the rate message
    
    @Column(name = "db_updatetime")
    private LocalDateTime dbUpdatetime;    // When it was stored in DB

    @Column(name = "pipeline_id")
    private String pipelineId;

    @Column(name = "rate_category")
    private String rateCategory; // RAW, AVERAGE, CROSS, OTHER

    public String getPipelineId() {
        return pipelineId;
    }
    
    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }
    
    public String getRateCategory() {
        return rateCategory;
    }
    
    public void setRateCategory(String rateCategory) {
        this.rateCategory = rateCategory;
    }
}