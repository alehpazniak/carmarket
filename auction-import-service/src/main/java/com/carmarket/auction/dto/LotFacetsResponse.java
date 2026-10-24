package com.carmarket.auction.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * GET /api/auctions/lots/facets: the makes and models that currently have listed lots, for the /aukcje dropdowns.
 * Values are UPPER(TRIM(...)) of the stored make/model, the same form GET /lots matches exactly.
 */
public record LotFacetsResponse(List<MakeFacet> makes) {

    public record MakeFacet(String make, long count, List<ModelFacet> models) {
    }

    public record ModelFacet(String model, long count) {
    }

    /** One row of the grouped query: a normalized make/model pair and its number of listed lots. */
    public record Row(String make, String model, Long count) {
    }

    /**
     * Makes and models sorted alphabetically. A blank value can't be picked in the dropdown (GET /lots treats a
     * blank parameter as "any"), so it gets no facet, but its lots still count towards their make.
     */
    public static LotFacetsResponse from(List<Row> rows) {
        Map<String, Long> makeCounts = new TreeMap<>();
        Map<String, Map<String, Long>> modelCounts = new TreeMap<>();
        for (Row row : rows) {
            if (row.make() == null || row.make().isEmpty()) continue;
            makeCounts.merge(row.make(), row.count(), Long::sum);
            Map<String, Long> models = modelCounts.computeIfAbsent(row.make(), make -> new TreeMap<>());
            if (row.model() != null && !row.model().isEmpty()) models.merge(row.model(), row.count(), Long::sum);
        }
        List<MakeFacet> makes = new ArrayList<>();
        makeCounts.forEach((make, count) -> makes.add(new MakeFacet(make, count,
            modelCounts.get(make).entrySet().stream().map(e -> new ModelFacet(e.getKey(), e.getValue())).toList())));
        return new LotFacetsResponse(makes);
    }
}
