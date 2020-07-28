package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PropertyPostProcessor extends AbstractPostProcessor {

    protected String currentType;

    @Override
    protected void process(String type, Schema schema) {
        this.currentType = type;

        Map<String, Schema> properties = toProperties(schema)
                .entrySet().stream()
                .map(e -> processProperty(e.getKey(), e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        schema.setProperties(properties);
        if (schema instanceof ComposedSchema) {
            schemas(((ComposedSchema) schema).getAllOf())
                    .forEach(s -> process(type, s));
            schemas(((ComposedSchema) schema).getOneOf())
                    .forEach(s -> process(type, s));
            schemas(((ComposedSchema) schema).getAnyOf())
                    .forEach(s -> process(type, s));
        }
    }

    private Stream<Schema> schemas(List<Schema> from) {
        return Optional.ofNullable(from).map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    private Map<String, Schema> toProperties(Schema schema) {
        return Optional.ofNullable(schema.getProperties())
                .orElse(Collections.emptyMap());
    }

    abstract protected Map.Entry<String, Schema> processProperty(String name, Schema property);
}
