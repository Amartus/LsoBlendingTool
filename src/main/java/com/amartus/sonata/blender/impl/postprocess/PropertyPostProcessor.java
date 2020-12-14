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

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract OAS API property postprocessor.
 * It traverse all properties from schemas (including compose schemas).
 * Non-thread safe
 */
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
