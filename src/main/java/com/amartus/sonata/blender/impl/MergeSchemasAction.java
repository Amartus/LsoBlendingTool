/*
 *
 * Copyright 2020 Amartus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.cmd.AbstractBlend;
import com.amartus.sonata.blender.impl.util.OasUtils;
import com.amartus.sonata.blender.impl.util.OasWrapper;
import io.swagger.models.properties.Property;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;


public class MergeSchemasAction {
    public enum Mode {
        FIX, RELAXED, STRICT
    }

    protected static final String DISCRIMINATOR_NAME = "@type";
    private static final Logger log = LoggerFactory.getLogger(AbstractBlend.class);
    private final String modelToAugment;
    private final Handler handler;
    private Map<String, Schema> schemasToInject = Map.of();
    private OpenAPI openAPI;
    private OasWrapper wrapper;

    private interface Handler {
        void handle(Discriminator disc, Schema prop);
    }

    public MergeSchemasAction(String modelToAugment, Mode mode) {
        this.modelToAugment = modelToAugment;
        mode = Optional.ofNullable(mode).orElse(Mode.FIX);
        handler = handler(mode, modelToAugment);
    }

    private Handler handler(Mode mode, String model) {
        switch (mode) {
            case FIX: return this::fixTargetSchema;
            case RELAXED: return  (d, p) -> {
                var issues = 0;
                if(d == null) {
                    issues += 1;
                    log.warn("No discriminator property defined for {} ", model);
                }

                if(p == null) {
                    issues += 1;
                    log.warn("No discriminator defined for {} ", model);
                }
                if(issues == 2) {
                    throw new IllegalStateException("Discriminator property not found");
                }

                fixTargetSchema(d, p);

            };
            default: return (s, p) -> {
                if(s == null || p == null) {
                    log.error("No discriminator defined for {} ", modelToAugment);
                    throw new IllegalStateException("Discriminator not found");
                }
            };
        }
    }

    public MergeSchemasAction schemasToInject(Map<String, Schema> schemasToInject) {
        this.schemasToInject = Map.copyOf(schemasToInject);
        return this;
    }

    public MergeSchemasAction target(OpenAPI api) {
        this.openAPI = api;
        this.wrapper = new OasWrapper(api);

        return this;

    }

    public void execute() {
        log.debug("Injecting {} schemas",
                schemasToInject.size());

        if (!schemasToInject.isEmpty()) {
            validateTargetExists();
            Schema schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
            handler.handle(discriminator(schema), prop(schema));
        }
        this.openAPI.getComponents().getSchemas().putAll(schemasToInject);
    }

    private void validateTargetExists() {
        if (getTargetSchema().isEmpty()) {
            log.error("Schema with name '{}' is not present in the API", modelToAugment);
            throw new IllegalStateException(String.format("Schema '%s' not found in the specification", modelToAugment));
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void fixTargetSchema(Discriminator discriminator, Schema property) {
        var schema = getTargetSchema().get();
        if(property == null) {
            log.info("Adding field {} to the {}", DISCRIMINATOR_NAME, modelToAugment);
            schema.addProperty(DISCRIMINATOR_NAME,
                    new StringSchema().description("Used as a discriminator to support polymorphic definitions"));
        }
        if(discriminator == null) {
            log.info("Adding discriminator to the {}", modelToAugment);
            schema.setDiscriminator(new Discriminator().propertyName(DISCRIMINATOR_NAME));
        }
    }

    @SuppressWarnings("rawtypes")
    private Optional<Schema> getTargetSchema() {
        var allSchemas = Optional.ofNullable(this.openAPI.getComponents())
                .flatMap(c -> Optional.ofNullable(c.getSchemas()));

        return allSchemas
                .map(all -> Optional.ofNullable(all.get(modelToAugment)))
                .orElseThrow(() -> new IllegalStateException("API schemas are not resolved"));
    }

    private <T> T recursiveSearch(Schema schema, Function<Schema, T> extractor) {
        Schema toEvaluate = schema;
        if(schema.get$ref() != null) {
            var name =  OasUtils.toSchemaName(schema.get$ref());
            toEvaluate = wrapper.schema(name)
                    .orElseThrow(() -> new IllegalStateException("cannot resolve " + schema.get$ref()));
        }
        var elem = extractor.apply(toEvaluate);
        if(elem != null) {
            return elem;
        }

        return Optional.ofNullable(toEvaluate.getAllOf())
                .stream().flatMap(Collection<Schema>::stream)
                .map(it -> recursiveSearch(it, extractor))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private Discriminator discriminator(Schema schema) {
        return recursiveSearch(schema, Schema::getDiscriminator);
    }

    private Schema prop(Schema schema) {
        return recursiveSearch(schema, s -> {
            if(s.getProperties() != null) {
                return (Schema) s.getProperties().get(DISCRIMINATOR_NAME);
            }
            return null;
        });
    }
}
