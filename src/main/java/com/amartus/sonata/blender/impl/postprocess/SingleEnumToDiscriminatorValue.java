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
import com.google.common.collect.Streams;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Iterates over top level schemas. For each oneOf schema
 * it tries to use enum value as discriminator if:
 * <ul>
 *     <ol>singleton enum is defined on discriminator field</ol>
 *     <ol>singleton enum is defined as a inline enum with one value obviously :D</ol>
 * </ul>
 * Has to be ran before {@link UpdateDiscriminatorMapping}
 *
 * @author bartosz.michalik@amartus.com
 */
public class SingleEnumToDiscriminatorValue extends AbstractPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(SingleEnumToDiscriminatorValue.class);

    @Override
    protected void process(String key, Schema schema) {
        if (OasUtils.isReferencingSchema(schema)) {
            ComposedSchema cs = (ComposedSchema) schema;
            if (cs.getOneOf() == null || cs.getOneOf().isEmpty()) {
                return;
            }
            //TODO inject values

            var referencedSchemas = cs.getOneOf().stream()
                    .map(s -> OasUtils.toSchemaName(s.get$ref()))
                    .flatMap(s -> api.schema(s).stream())
                    .collect(Collectors.toList());

            var disc = OasUtils.findDiscriminator(referencedSchemas);

            disc.ifPresent(discName -> {
                var dF = getSingletonEnumDiscriminator(discName);
                var discValues = referencedSchemas.stream().flatMap(s -> dF.apply(s).stream())
                        .collect(Collectors.toList());
                if (discValues.size() == referencedSchemas.size()) {
                    Streams.zip(
                            referencedSchemas.stream(),
                            discValues.stream(),
                            Map::entry)
                            .forEach(e -> {
                                log.debug("Assigning 'x-discriminator-value'='{}' to one of the variants of {}", e.getValue(), key);
                                e.getKey().addExtension("x-discriminator-value", e.getValue());
                                Schema<?> ps = (Schema<?>) e.getKey().getProperties().get(discName);
                                ps.setEnum(null);
                            });
                } else {
                    log.warn("Not all variants of {} are using same representation of discriminator {}", key, discName);
                }
            });

        }
    }

    private Function<Schema<?>, Optional<String>> getSingletonEnumDiscriminator(String propertyName) {
        return sc -> Optional.ofNullable(sc.getProperties())
                .flatMap(p -> Optional.ofNullable((Schema<?>) p.get(propertyName)))
                .flatMap(x -> Optional.of(x.getEnum()))
                .flatMap(lE -> {
                    if (lE.size() == 1) {
                        return Optional.of((String) lE.get(0));
                    }
                    return Optional.empty();
                });
    }
}
