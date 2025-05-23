package com.toyota.consumer.repository.opensearch;

import com.toyota.consumer.model.RateDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateDocumentRepository extends ElasticsearchRepository<RateDocument, String> {
    // Additional query methods can be added as needed
}
