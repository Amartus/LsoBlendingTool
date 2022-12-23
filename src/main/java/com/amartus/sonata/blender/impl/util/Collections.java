package com.amartus.sonata.blender.impl.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface Collections {
    static <K,V> Collector<Map.Entry<K,V>, ?, LinkedHashMap<K,V>> mapCollector() {
        return mapCollector((a,b) -> a);
    }

    static <K,V> Collector<Map.Entry<K,V>, ?, LinkedHashMap<K,V>> mapCollector(BinaryOperator<V> merger) {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, merger, LinkedHashMap::new);
    }
}
