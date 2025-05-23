package com.toyota.consumer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a financial rate record in the database
 */
@Entity
@Table(name = "tbl_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rate_name", length = 20, nullable = false)
    private String rateName;
    
    @Column(name = "bid", precision = 19, scale = 8, nullable = false)
    private BigDecimal bid;
    
    @Column(name = "ask", precision = 19, scale = 8, nullable = false)
    private BigDecimal ask;
    
    @Column(name = "rate_updatetime", nullable = false)
    private LocalDateTime rateUpdatetime;
    
    @Column(name = "db_updatetime", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime dbUpdatetime;
}
