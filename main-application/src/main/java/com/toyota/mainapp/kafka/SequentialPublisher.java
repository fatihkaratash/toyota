package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;

/**
 * Sıralı kur yayınlama işlemleri için arayüz
 */
public interface SequentialPublisher {
    
    /**
     * Bir kuru yayınla
     * @param rate Yayınlanacak kur
     */
    void publishRate(BaseRateDto rate);
}