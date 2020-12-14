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

package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Externalize inline enum to separate schema.
 * This does not work for singleton enums.
 *
 * @author bartosz.michalik@amartus.com
 */
public class PropertyEnumExternalize extends PropertyPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(PropertyEnumExternalize.class);

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        return extractEnumSchema(name, property)
                .orElse(Map.entry(name, property));
    }

    Optional<Map.Entry<String, Schema>> extractEnumSchema(String name, Schema property) {
        if (property.getEnum() != null) {

            if (property.getEnum().size() > 1) {
                return extractEnum(name, property);
            }
            log.info("Enum {}.{} is constant. Skipping, refactoring", currentType, name);


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
        String enumName = proposeName(name);

        log.info("Refactoring enum {}.{} to separate schema: {}", currentType, name, enumName);
        var newSchema = referencing(property, enumName);
        registerNewSchema(enumName, property);

        return Optional.of(Map.entry(name, newSchema));
    }
}
