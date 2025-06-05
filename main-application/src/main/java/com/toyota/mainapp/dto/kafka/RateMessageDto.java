package com.toyota.mainapp.dto.kafka;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateMessageDto {
    private String messageId;
    private long messageTimestamp;
    private String rateType; 

    // Updated to use unified RatePayloadDto instead of type-specific DTOs
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "rateType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RatePayloadDto.class, name = "RAW_RATE"),
            @JsonSubTypes.Type(value = RatePayloadDto.class, name = "CALCULATED_RATE"),
            @JsonSubTypes.Type(value = RatePayloadDto.class, name = "RATE_STATUS")
    })
    private RatePayloadDto payload;

    private Map<String, String> metadata;
}