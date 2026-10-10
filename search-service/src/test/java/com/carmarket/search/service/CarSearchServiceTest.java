package com.carmarket.search.service;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.dto.SearchRequest;
import com.carmarket.search.repository.CarSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CarSearchService ranking")
class CarSearchServiceTest {

    private static final Sort.Order PROMOTED_DESC = Sort.Order.desc("promoted");

    @Test
    void unsortedSearchRanksPromotedFirstThenRelevance() {
        Pageable ranked = CarSearchService.withPromotedFirst(PageRequest.of(2, 20));

        assertThat(ranked.getSort()).containsExactly(PROMOTED_DESC, Sort.Order.desc("_score"));
        assertThat(ranked.getPageNumber()).isEqualTo(2);
        assertThat(ranked.getPageSize()).isEqualTo(20);
    }

    @Test
    void newestFirstRanksPromotedFirstThenNewest() {
        Pageable ranked = CarSearchService.withPromotedFirst(
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(ranked.getSort()).containsExactly(PROMOTED_DESC, Sort.Order.desc("createdAt"));
    }

    @Test
    void explicitPriceSortStaysPure() {
        Pageable cheapestFirst = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "price"));

        assertThat(CarSearchService.withPromotedFirst(cheapestFirst)).isSameAs(cheapestFirst);
    }

    @Test
    void oldestFirstIsAnExplicitSortToo() {
        Pageable oldestFirst = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"));

        assertThat(CarSearchService.withPromotedFirst(oldestFirst)).isSameAs(oldestFirst);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchSendsRankedSortToElasticsearch() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        SearchHits<CarDocument> hits = mock(SearchHits.class);
        when(operations.search(any(Query.class), eq(CarDocument.class))).thenReturn(hits);
        CarSearchService service = new CarSearchService(mock(CarSearchRepository.class), operations);

        service.search(new SearchRequest(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(operations).search(query.capture(), eq(CarDocument.class));
        assertThat(query.getValue().getSort()).containsExactly(PROMOTED_DESC, Sort.Order.desc("createdAt"));
    }
}
