package com.toyota.mainapp.mapper;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.kafka.RatePayloadDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Efficient and unified mapper for all rate DTOs
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RateMapper {

    @Mapping(target = "rateType", constant = "RAW")
    @Mapping(target = "bid", expression = "java(stringToBigDecimal(providerRateDto.getBid()))")
    @Mapping(target = "ask", expression = "java(stringToBigDecimal(providerRateDto.getAsk()))")
    @Mapping(target = "timestamp", expression = "java(safelyConvertTimestamp(providerRateDto.getTimestamp()))")
    @Mapping(target = "receivedAt", expression = "java(currentTimeMillis())")
    @Mapping(target = "validatedAt", ignore = true)
    @Mapping(target = "calculationInputs", ignore = true)
    @Mapping(target = "calculatedByStrategy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusMessage", ignore = true)
    BaseRateDto toBaseRateDto(ProviderRateDto providerRateDto);

    @Mapping(target = "eventType", expression = "java(determineEventType(baseRateDto.getRateType()))")
    @Mapping(target = "eventTime", expression = "java(currentTimeMillis())")
    @Mapping(target = "sourceReceivedAt", source = "receivedAt")
    @Mapping(target = "sourceValidatedAt", source = "validatedAt")
    @Mapping(target = "rateTimestamp", source = "timestamp")
    RatePayloadDto toRatePayloadDto(BaseRateDto baseRateDto);

    @Named("createStatusDto")
    default BaseRateDto createStatusDto(String symbol, String providerName,
                                     BaseRateDto.RateStatusEnum status,
                                     String statusMessage) {
        return BaseRateDto.builder()
                .rateType(RateType.STATUS)
                .symbol(symbol)
                .providerName(providerName)
                .status(status)
                .statusMessage(statusMessage)
                .timestamp(currentTimeMillis())
                .build();
    }

    @Mapping(target = "rateType", constant = "STATUS")
    @Mapping(target = "status", source = "source", qualifiedByName = "determineStatus")
    @Mapping(target = "statusMessage", constant = "Rate processed successfully")
    @Mapping(target = "calculationInputs", ignore = true)
    @Mapping(target = "calculatedByStrategy", ignore = true)
    BaseRateDto toStatusDto(BaseRateDto source);

    @Named("determineStatus")
    default BaseRateDto.RateStatusEnum determineStatus(BaseRateDto source) {
        if (source == null) {
            return BaseRateDto.RateStatusEnum.ERROR;
        }
        
        if (source.getValidatedAt() != null && source.getValidatedAt() > 0) {
            return BaseRateDto.RateStatusEnum.ACTIVE;
        }
        
        return BaseRateDto.RateStatusEnum.PENDING;
    }
    
    default BigDecimal stringToBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    default Long safelyConvertTimestamp(Object timestamp) {
        if (timestamp == null) {
            return currentTimeMillis();
        }
        
        if (timestamp instanceof Long) {
            return (Long) timestamp;
        }
        
        if (timestamp instanceof String) {
            String strTimestamp = (String) timestamp;
            if (strTimestamp.trim().isEmpty()) {
                return currentTimeMillis();
            }
            
            try {
                return Long.parseLong(strTimestamp);
            } catch (NumberFormatException e) {
                try {
                    return Instant.parse(strTimestamp).toEpochMilli();
                } catch (Exception parseException) {
                    return currentTimeMillis();
                }
            }
        }
        
        return currentTimeMillis();
    }
    
    default long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    default String determineEventType(com.toyota.mainapp.dto.model.RateType rateType) {
        if (rateType == null) {
            return "UNKNOWN";
        }
        
        return switch (rateType) {
            case RAW -> "RATE_RECEIVED";
            case CALCULATED -> "RATE_CALCULATED";
            case STATUS -> "RATE_STATUS";
            default -> "RATE_PROCESSED";
        };
    }
}
