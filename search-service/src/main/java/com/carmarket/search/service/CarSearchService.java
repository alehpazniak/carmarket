package com.carmarket.search.service;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.dto.SearchRequest;
import com.carmarket.search.repository.CarSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarSearchService {

    private final CarSearchRepository searchRepository;
    private final ElasticsearchOperations esOperations;

    // ─── INDEX (called from Kafka consumer) ───────────────────────────────────

    @CacheEvict(value = "car-search", allEntries = true)
    public void indexCar(CarDocument document) {
        searchRepository.save(document);
        log.debug("Indexed carId: {}", document.getId());
    }

    @CacheEvict(value = "car-search", allEntries = true)
    public void removeCar(String carId) {
        searchRepository.deleteById(carId);
        log.info("Removed carId from index: {}", carId);
    }

    // ─── SEARCH ───────────────────────────────────────────────────────────────

    /**
     * Dynamic multi-criteria search.
     * Builds Criteria query from whatever filters are provided in SearchRequest.
     */
        @Cacheable(value = "car-search", key = "#req.toString() + #pageable.pageNumber")
    public List<CarDocument> search(SearchRequest req, Pageable pageable) {
        Criteria criteria = new Criteria();

        // Only return ACTIVE listings
        criteria = criteria.and(new Criteria("status").is("ACTIVE"));

        // Full-text query across make, model, description
        if (StringUtils.isNotEmpty(req.getQuery())) {
            Criteria textSearch = new Criteria("make").contains(req.getQuery())
                .or(new Criteria("model").contains(req.getQuery()))
                .or(new Criteria("description").contains(req.getQuery()));
            criteria = criteria.and(textSearch);
        }

        if (StringUtils.isNotEmpty(req.getMake())) {
            criteria = criteria.and(new Criteria("make").is(req.getMake()));
        }
        if (StringUtils.isNotEmpty(req.getModel())) {
            criteria = criteria.and(new Criteria("model").contains(req.getModel()));
        }
        if (req.getYearFrom() != null) {
            criteria = criteria.and(new Criteria("year").greaterThanEqual(req.getYearFrom()));
        }
        if (req.getYearTo() != null) {
            criteria = criteria.and(new Criteria("year").lessThanEqual(req.getYearTo()));
        }
        if (req.getPriceFrom() != null) {
            criteria = criteria.and(new Criteria("price").greaterThanEqual(req.getPriceFrom()));
        }
        if (req.getPriceTo() != null) {
            criteria = criteria.and(new Criteria("price").lessThanEqual(req.getPriceTo()));
        }
        if (req.getMileageMax() != null) {
            criteria = criteria.and(new Criteria("mileage").lessThanEqual(req.getMileageMax()));
        }
        if (StringUtils.isNotEmpty(req.getFuelType())) {
            criteria = criteria.and(new Criteria("fuelType").is(req.getFuelType().toUpperCase()));
        }
        if (StringUtils.isNotEmpty(req.getTransmission())) {
            criteria = criteria.and(new Criteria("transmission").is(req.getTransmission().toUpperCase()));
        }
        if (StringUtils.isNotEmpty(req.getCity())) {
            criteria = criteria.and(new Criteria("city").is(req.getCity()));
        }
        if (StringUtils.isNotEmpty(req.getCountry())) {
            criteria = criteria.and(new Criteria("country").is(req.getCountry()));
        }

        Query query = new CriteriaQuery(criteria).setPageable(pageable);
        SearchHits<CarDocument> hits = esOperations.search(query, CarDocument.class);

        return hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .toList();
    }

    public Optional<CarDocument> findById(String id) {
        return searchRepository.findById(id);
    }
}
