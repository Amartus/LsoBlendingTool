package com.amartus.sonata.blender.impl.postprocess;

import com.amartus.sonata.blender.impl.util.OasUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.IterableUtils.isEmpty;


class SortTypesByNameTest {
    private OpenAPI oas;

    @BeforeEach
    public void readOas() {
        var uri = SortTypesByNameTest.class.getResource("/ref-model/unsorted.yaml");
        assert uri != null;
        oas = OasUtils.readOas(uri.toString());
    }
    @Test
    public void typesAreSorted() {
        Assertions.assertThat(schemaNames(oas)).is(new Condition<>(this::notSorted, "not sorted"));
        new SortTypesByName().accept(oas);
        Assertions.assertThat(schemaNames(oas)).isSorted();
    }

    @Test
    public void propertiesAreNotSorted() {
        var schema = oas.getComponents().getSchemas().get("B");
        List<String> keysBefore = schemaKeys(schema.getProperties());
        new SortTypesByName().accept(oas);
        schema = oas.getComponents().getSchemas().get("B");
        List<String> keysAfter = schemaKeys(schema.getProperties());
        Assertions.assertThat(keysAfter)
                .is(new Condition<>(this::notSorted, "not sorted"))
                .isEqualTo(keysBefore);
    }

    private <T extends Comparable<T>> boolean notSorted(List<? extends T> list) {
        if (isEmpty(list) || list.size() == 1) {
            return false;
        }

        var iter = list.iterator();
        T current, previous = iter.next();
        while (iter.hasNext()) {
            current = iter.next();
            if (previous.compareTo(current) > 0) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    private static List<String> schemaNames(OpenAPI oas) {
        return schemaKeys(oas.getComponents().getSchemas());
    }
    private static List<String> schemaKeys(Map<String, Schema> schema) {
        return new ArrayList<>(schema.keySet());
    }
}