package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PropertyCompositionToType extends PropertyPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(PropertyCompositionToType.class);

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        if (property instanceof ComposedSchema) {
            Map<String, Schema> types = api.getComponents().getSchemas();
            String candidate = name + "_oneOf";
            if (types.containsKey(candidate)) {
                candidate = currentType + candidate;
            }
            log.info("Refactoring {}.{} to separate schema: {}", currentType, name, candidate);
            Schema<Object> newSchema = new Schema<>();
            newSchema.set$ref("#/components/schemas/" + candidate);
            newSchema.setDescription(property.getDescription());
            types.put(candidate, property);
            return Map.entry(name, newSchema);
        } else {
            return Map.entry(name, property);
        }
    }
}
