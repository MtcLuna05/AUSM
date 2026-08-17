package com.l.ausm.impl.client.dynamic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DynamicLightSpatialIndexTest {
    @Test
    void queriesOnlySourcesWhoseRadiusTouchesTheBlockSection() {
        DynamicLightSource nearby = new DynamicLightSource("near", 0.5D, 64.5D, 0.5D, 15);
        DynamicLightSource distant = new DynamicLightSource("far", 1024.5D, 64.5D, 1024.5D, 15);

        Map<Long, List<DynamicLightSource>> index = DynamicLightSpatialIndex.build(List.of(nearby, distant));

        assertEquals(1, DynamicLightSpatialIndex.candidateCount(index, 0, 64, 0));
        assertEquals(1, DynamicLightSpatialIndex.candidateCount(index, 1024, 64, 1024));
        assertEquals(0, DynamicLightSpatialIndex.candidateCount(index, 512, 64, 512));
    }

    @Test
    void preservesLightAcrossSectionBoundaries() {
        DynamicLightSource boundary = new DynamicLightSource("boundary", 15.75D, 64.5D, 0.5D, 15);
        Map<Long, List<DynamicLightSource>> index = DynamicLightSpatialIndex.build(List.of(boundary));

        assertEquals(15, DynamicLightSpatialIndex.lightAt(index, 16, 64, 0));
        assertEquals(1, DynamicLightSpatialIndex.candidateCount(index, 16, 64, 0));
    }

    @Test
    void returnsTheBrightestLocalContribution() {
        DynamicLightSource dim = new DynamicLightSource("dim", 0.5D, 64.5D, 0.5D, 6);
        DynamicLightSource bright = new DynamicLightSource("bright", 0.5D, 64.5D, 0.5D, 12);
        Map<Long, List<DynamicLightSource>> index = DynamicLightSpatialIndex.build(List.of(dim, bright));

        assertEquals(12, DynamicLightSpatialIndex.lightAt(index, 0, 64, 0));
    }
}
