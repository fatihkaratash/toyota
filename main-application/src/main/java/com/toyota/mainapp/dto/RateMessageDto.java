package com.toyota.mainapp.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.toyota.mainapp.dto.payload.CalculatedRatePayloadDto; // Placeholder for Faz 4+
import com.toyota.mainapp.dto.payload.RawRatePayloadDto;
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
    private String rateType; // "RAW" or "CALCULATED"

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "rateType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RawRatePayloadDto.class, name = "RAW"),
            @JsonSubTypes.Type(value = CalculatedRatePayloadDto.class, name = "CALCULATED") // Placeholder for Faz 4+
    })
    private Object payload;

    private Map<String, String> metadata;
}
