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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ConstrainDiscriminatorValueWithEnum extends AbstractPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ConstrainDiscriminatorValueWithEnum.class);

    @Override
    protected void process(String key, Schema schema) {
        if (schema.getDiscriminator() != null) {
            var mapping = schema.getDiscriminator().getMapping();
            if (mapping != null) {
                var property = schema.getDiscriminator().getPropertyName();
                log.debug("Adding enum constraint for {} in {}", property, key);
                findProperty(property, schema)
                        .ifPresentOrElse(p -> {
                            //noinspection unchecked
                            p.setEnum(new ArrayList<>(mapping.keySet()));
                            log.debug("Enum values {}", mapping.keySet());
                        }, () -> log.warn("Property {} does not exists in {}", property, key));
            } else {
                log.warn("Mapping is not defined for {}. Cannot add enum", key);
            }
        }
    }

    private Optional<Schema> findProperty(String name, Schema schema) {
        if (schema.get$ref() != null) {
            return api.schema(schema.get$ref())
                    .flatMap(s -> findProperty(name, s));
        }
        if (schema instanceof ComposedSchema) {
            var cs = (ComposedSchema) schema;
            var allSchemas = Stream.concat(schemas(cs.getAllOf()),
                    Stream.concat(
                            schemas(cs.getAnyOf()),
                            schemas(cs.getOneOf())
                    ));
            return allSchemas.flatMap(s -> findProperty(name, s).stream()).findFirst();
        } else {
            return Optional.ofNullable(schema.getProperties())
                    .flatMap(m -> Optional.ofNullable((Schema) m.get(name)));
        }
    }

    private Stream<Schema> schemas(List<Schema> schemas) {
        if (schemas == null) return Stream.empty();
        return schemas.stream();
    }
}
