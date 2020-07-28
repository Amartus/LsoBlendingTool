package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class PropertyEnumExternalize extends PropertyPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(PropertyEnumExternalize.class);

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        return extractEnumSchema(name, property)
                .orElse(Map.entry(name, property));
    }

    Optional<Map.Entry<String, Schema>> extractEnumSchema(String name, Schema property) {
        if (property.getEnum() != null) {
            return extractEnum(name, property);
        } else if (property instanceof ArraySchema) {
            Schema<?> items = ((ArraySchema) property).getItems();
            return extractEnumSchema(name, items)
                    .map(e -> Map.entry(
                            e.getKey(),
                            new ArraySchema().items(e.getValue())
                    ));
        }

        return Optional.empty();
    }

    private Optional<Map.Entry<String, Schema>> extractEnum(String name, Schema property) {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        String candidate = StringUtils.capitalize(name);
        if (schemas.containsKey(candidate)) {
            candidate = currentType + candidate;
        }

        log.info("Refactoring enum {}.{} to separate schema: {}", currentType, name, candidate);

        Schema<Object> newType = new Schema<>();
        newType.set$ref("#/components/schemas/" + candidate);
        property.setType("string");
        schemas.put(candidate, property);
        return Optional.of(Map.entry(name, newType));
    }
}
