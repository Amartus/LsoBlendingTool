package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;
import java.util.function.Predicate;

public class RemoveSuperflousTypeDeclarations extends PropertyPostProcessor {
    protected Predicate<Schema> refProperty = s -> s.get$ref() != null;

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        if (refProperty.test(property)) {
            property.setType(null);
        }
        return Map.entry(name, property);
    }
}
