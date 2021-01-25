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

import com.amartus.sonata.blender.cmd.AbstractCmd;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class MergeSchemasAction {
    protected static final String DISCRIMINATOR_NAME = "@type";
    private static final Logger log = LoggerFactory.getLogger(AbstractCmd.class);
    private final String modelToAugment;
    private final boolean strict;
    private Map<String, Schema> schemasToInject = Map.of();
    private OpenAPI openAPI;

    public MergeSchemasAction(String modelToAugment, boolean strict) {
        this.modelToAugment = modelToAugment;
        this.strict = strict;
    }

    public MergeSchemasAction schemasToInject(Map<String, Schema> schemasToInject) {
        this.schemasToInject = Map.copyOf(schemasToInject);
        return this;
    }

    public MergeSchemasAction target(OpenAPI api) {
        this.openAPI = api;
        validateTargetExists();
        return this;

    }

    public void execute() {
        log.debug("Injecting {} schemas",
                schemasToInject.size());

        if (!schemasToInject.isEmpty()) {

            if (!isTargetReadyForExtension()) {
                if (strict) {
                    log.error("No discriminator defined for {} ", modelToAugment);
                    throw new IllegalStateException("Discriminator not found");
                } else {
                    prepareTargetForExtension();
                }
            }
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
    private void prepareTargetForExtension() {
        var schema = getTargetSchema().get();

        boolean hasTypeDefined = Optional.ofNullable(schema.getProperties())
                .map(p -> p.containsKey(DISCRIMINATOR_NAME))
                .orElse(false);

        if (!hasTypeDefined) {
            log.info("Adding field {} to the {}", DISCRIMINATOR_NAME, modelToAugment);
            schema.addProperties(DISCRIMINATOR_NAME,
                    new StringSchema().description("Used as a discriminator to support polymorphic definitions"));
        }
        log.info("Adding discriminator to the {}", modelToAugment);
        schema.setDiscriminator(new Discriminator().propertyName(DISCRIMINATOR_NAME));

    }

    @SuppressWarnings("rawtypes")
    private Optional<Schema> getTargetSchema() {
        var allSchemas = Optional.ofNullable(this.openAPI.getComponents())
                .flatMap(c -> Optional.ofNullable(c.getSchemas()));

        return allSchemas
                .map(all -> Optional.ofNullable(all.get(modelToAugment)))
                .orElseThrow(() -> new IllegalStateException("API schemas are not resolved"));
    }

    private boolean isTargetReadyForExtension() {
        Schema schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
        Discriminator discriminator = schema.getDiscriminator();
        return discriminator != null;
    }
}
