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

import com.amartus.sonata.blender.impl.util.OasUtils;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.text.WordUtils;

import java.util.*;
import java.util.stream.Stream;

import static com.amartus.sonata.blender.impl.util.Collections.mapCollector;

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
                .collect(mapCollector());
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

    protected Schema referencing(Schema prop, String name) {
        return new Schema<>()
                .$ref(OasUtils.toSchemRef(name))
                .description(prop.getDescription());
    }

    abstract protected Map.Entry<String, Schema> processProperty(String name, Schema property);

    protected void registerNewSchema(String name, Schema<?> schema) {
        api.schemas().put(name, schema);
    }

    protected String proposeName(String name) {
        name = escape(name);

        Map<String, Schema> types = api.schemas();
        String candidate = WordUtils.capitalize(name);
        if (types.containsKey(candidate)) {
            candidate = currentType + candidate;
        }
        return candidate;
    }

    protected String escape(String name) {
        final var mapping = Map.of(
                "@", "_at",
                "!", "_ex"
        );

        for (var e : mapping.entrySet()) {
            name = name.replaceAll(e.getKey(), e.getValue());
        }

        return name;
    }
}
