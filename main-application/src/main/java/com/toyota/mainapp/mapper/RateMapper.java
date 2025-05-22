package com.toyota.mainapp.mapper;

import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.dto.RateStatusDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.dto.payload.CalculatedRatePayloadDto;
import com.toyota.mainapp.dto.payload.RawRatePayloadDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Veri nesnelerini dönüştürmek için MapStruct mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RateMapper {

    /**
     * Sağlayıcı verisini standart formata dönüştür
     */
    @Mapping(target = "bid", expression = "java(stringToBigDecimal(providerRateDto.getBid()))")
    @Mapping(target = "ask", expression = "java(stringToBigDecimal(providerRateDto.getAsk()))")
    @Mapping(target = "timestamp", expression = "java(convertTimestamp(providerRateDto.getTimestamp()))")
    NormalizedRateDto toNormalizedDto(ProviderRateDto providerRateDto);

    /**
     * Standart kuru ham kura dönüştür
     */
    @Mapping(target = "receivedAt", ignore = true)
    @Mapping(target = "validatedAt", ignore = true)
    RawRateDto toRawDto(NormalizedRateDto normalizedRateDto);

    /**
     * Ham kuru Kafka yük verisi formatına dönüştür
     */
    @Mapping(target = "sourceReceivedAt", source = "receivedAt")
    @Mapping(target = "sourceValidatedAt", source = "validatedAt")
    @Mapping(target = "eventType", constant = "RAW_RATE")
    @Mapping(target = "eventTime", expression = "java(currentTimeMillis())")
    RawRatePayloadDto toRawRatePayloadDto(RawRateDto rawRateDto);

    /**
     * Hesaplanmış kuru Kafka yük verisi formatına dönüştür
     */
    @Mapping(target = "rateTimestamp", source = "timestamp")
    @Mapping(target = "eventType", constant = "CALCULATED_RATE")
    @Mapping(target = "eventTime", expression = "java(currentTimeMillis())")
    CalculatedRatePayloadDto toCalculatedRatePayloadDto(CalculatedRateDto calculatedRateDto);

    /**
     * Kur durum nesnesi oluştur
     */
    @Mapping(target = "timestamp", expression = "java(currentTimeMillis())")
    RateStatusDto createRateStatusDto(String symbol, String providerName,
                                     RateStatusDto.RateStatusEnum status,
                                     String statusMessage);

    /**
     * Ham kuru durum nesnesine dönüştür
     */
    @Mapping(target = "status", source = "rawRate", qualifiedByName = "determineStatus")
    @Mapping(target = "statusMessage", constant = "Kur başarıyla işlendi")
    @Mapping(target = "timestamp", expression = "java(currentTimeMillis())")
    RateStatusDto toRateStatusDto(RawRateDto rawRate);

    /**
     * Ham kur verisine göre durum belirle
     */
    @Named("determineStatus")
    default RateStatusDto.RateStatusEnum determineStatus(RawRateDto rawRate) {
        if (rawRate == null) {
            return RateStatusDto.RateStatusEnum.ERROR;
        }
        
        if (rawRate.getValidatedAt() != null && rawRate.getValidatedAt() > 0) {
            return RateStatusDto.RateStatusEnum.ACTIVE;
        }
        
        return RateStatusDto.RateStatusEnum.PENDING;
    }
    
    /**
     * String değeri BigDecimal'e dönüştür
     */
    default BigDecimal stringToBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            // Loglama yapılabilir
            return null;
        }
    }
    
    /**
     * Timestamp'i milisaniye cinsinden long'a dönüştür
     */
    default Long convertTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return currentTimeMillis();
        }
        try {
            // Basit bir dönüşüm - gerçek uygulamada daha karmaşık olabilir
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            // Loglama yapılabilir
            return currentTimeMillis();
        }
    }
    
    /**
     * Mevcut zamanı milisaniye cinsinden al
     */
    default long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }
}
