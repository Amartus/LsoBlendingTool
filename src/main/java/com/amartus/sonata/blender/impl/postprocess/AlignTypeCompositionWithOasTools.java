package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * There is a bug in OpenAPI generator which prevents from composing types using allOf
 * org.openapitools.codegen.DefaultCodegen#getOneOfAnyOfDescendants(java.lang.String, java.lang.String, io.swagger.v3.oas.models.media.ComposedSchema, io.swagger.v3.oas.models.OpenAPI)
 * This class is a workaround
 */
public class AlignTypeCompositionWithOasTools extends AbstractPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(AlignTypeCompositionWithOasTools.class);

    @Override
    protected void process(String name, Schema schema) {
        if (schema instanceof ComposedSchema && ((ComposedSchema) schema).getAllOf() != null) {

            boolean requiresFixing =
                    ((ComposedSchema) schema).getAllOf().stream()
                            .anyMatch(s -> s.get$ref() == null);
            if (requiresFixing) {
                log.info("Fixing {}", name);
                fixSchema(name + "_allOf", (ComposedSchema) schema);
            }

        }
    }

    private void fixSchema(String name, ComposedSchema schema) {

        List<Pair<Schema, Pair<String, Schema>>> processed = schema.getAllOf().stream()
                .map(s -> {
                    if (s instanceof ObjectSchema) {
                        Schema<Object> allOf = new Schema<>();
                        allOf.set$ref("#/components/schemas/" + name);
                        return rep(name, s, allOf);
                    }
                    return rep(s);
                }).collect(Collectors.toList());

        processed.stream()
                .map(p -> p.second)
                .filter(Objects::nonNull)
                .forEach(p -> {
                    log.info("Adding {}", p.first);
                    api.getComponents().getSchemas().put(p.first, p.second);
                });
        schema.setAllOf(processed.stream().map(p -> p.first).collect(Collectors.toList()));
    }

    private Pair<Schema, Pair<String, Schema>> rep(String name, Schema org, Schema allOf) {
        return new Pair<>(allOf, new Pair<>(name, org));
    }

    private Pair<Schema, Pair<String, Schema>> rep(Schema org) {
        return new Pair<>(org, null);
    }

    static class Pair<F, S> {
        public final F first;
        public final S second;

        Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}
