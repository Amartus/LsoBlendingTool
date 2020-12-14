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
        Map<String, Schema> schemas = api.schemas();
        String enumName = StringUtils.capitalize(name);
        if (schemas.containsKey(enumName)) {
            enumName = currentType + enumName;
        }

        log.info("Refactoring enum {}.{} to separate schema: {}", currentType, name, enumName);

        Schema<Object> newType = new Schema<>();
        newType.set$ref("#/components/schemas/" + enumName);
        property.setType("string");
        schemas.put(enumName, property);
        return Optional.of(Map.entry(name, newType));
    }
}
