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
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


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
        void handle(Discriminator disc, Schema prop, Schema subject);
    }

    public MergeSchemasAction(String modelToAugment, Mode mode) {
        this.modelToAugment = modelToAugment;
        mode = Optional.ofNullable(mode).orElse(Mode.FIX);
        handler = handler(mode, modelToAugment);
    }

    private Handler handler(Mode mode, String model) {
        switch (mode) {
            case FIX: return this::fixTargetSchema;
            case RELAXED: return  (d, p, t) -> {
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

                fixTargetSchema(d, p, t);

            };
            default: return (s, p, _n) -> {
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
        if (schemasToInject.isEmpty()) return;

        log.debug("Injecting {} schemas",
                schemasToInject.size());
        Set<String> targets = findTargets();

        targets.forEach(targetName -> {
            validateTargetExists(targetName);
            Schema schema = this.openAPI.getComponents().getSchemas().get(targetName);
            log.info("Evaluating target {}", targetName);
            handler.handle(discriminator(schema), prop(schema), schema);
        });

        this.openAPI.getComponents().getSchemas().putAll(schemasToInject);
    }

    private Set<String> findTargets() {
        Set<String> targets = schemasToInject.values().stream()
                .flatMap(s -> OasUtils.extensionByName(ProductSpecReader.TARGET_NAME, s).stream())
                .collect(Collectors.toSet());
        var noRootSchemaWithDefinedTarget = schemasToInject.values().stream()
                .filter(s -> OasUtils.extensionByName(ProductSpecReader.DISCRIMINATOR_VALUE, s).isPresent())
                .anyMatch(s -> OasUtils.extensionByName(ProductSpecReader.TARGET_NAME, s).isEmpty());

        if(noRootSchemaWithDefinedTarget) {
            targets = new HashSet<>(targets);
            targets.add(modelToAugment);
        }

        if(targets.isEmpty() && ! schemasToInject.isEmpty()) {
            throw new IllegalStateException("No targets identified for schemas to inject");
        }

        return targets;
    }

    private void validateTargetExists(String targetName) {
        if (getTargetSchema(targetName).isEmpty()) {
            log.error("Schema with name '{}' is not present in the API", modelToAugment);
            throw new IllegalStateException(String.format("Schema '%s' not found in the specification", modelToAugment));
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void fixTargetSchema(Discriminator discriminator, Schema property, Schema targetSchema) {

        if(property == null) {
            log.info("Adding field {} to the {}", DISCRIMINATOR_NAME, modelToAugment);
            targetSchema.addProperty(DISCRIMINATOR_NAME,
                    new StringSchema().description("Used as a discriminator to support polymorphic definitions"));
        }
        if(discriminator == null) {
            log.info("Adding discriminator to the {}", modelToAugment);
            targetSchema.setDiscriminator(new Discriminator().propertyName(DISCRIMINATOR_NAME));
        }
    }

    @SuppressWarnings("rawtypes")
    private Optional<Schema> getTargetSchema(String modelName) {
        var allSchemas = Optional.ofNullable(this.openAPI.getComponents())
                .flatMap(c -> Optional.ofNullable(c.getSchemas()));

        return allSchemas
                .map(all -> Optional.ofNullable(all.get(modelName)))
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
        return schema.getDiscriminator();
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
