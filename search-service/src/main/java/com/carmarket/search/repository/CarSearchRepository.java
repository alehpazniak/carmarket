package com.carmarket.search.repository;

import com.carmarket.search.document.CarDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CarSearchRepository extends ElasticsearchRepository<CarDocument, String> {

    Page<CarDocument> findByStatus(String status, Pageable pageable);

    Page<CarDocument> findByMakeIgnoreCaseAndStatus(String make, String status, Pageable pageable);
}
