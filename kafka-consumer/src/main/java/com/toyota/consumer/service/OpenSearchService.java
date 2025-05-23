package com.toyota.consumer.service;

import com.toyota.consumer.model.RateDocument;
import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.repository.opensearch.RateDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenSearchService {

    private final RateDocumentRepository rateDocumentRepository;
    
    public void indexRate(RateEntity rateEntity) {
        try {
            // Provide the required String argument, e.g., a document ID or other relevant value
            RateDocument document = RateDocument.fromEntity(rateEntity, "defaultId");
            RateDocument savedDocument = rateDocumentRepository.save(document);
            log.debug("Indexed rate in OpenSearch with ID: {}", savedDocument.getId());
        } catch (Exception e) {
            log.error("Error indexing rate in OpenSearch", e);
            // Don't throw the exception, as this is a secondary operation
        }
    }
}
