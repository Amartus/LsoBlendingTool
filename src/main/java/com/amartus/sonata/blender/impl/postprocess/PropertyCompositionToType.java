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
import com.amartus.sonata.blender.impl.util.OasWrapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Uplift composed property declaration to types
 */
public class PropertyCompositionToType extends PropertyPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(PropertyCompositionToType.class);

    private Map<String, String> hashToName;

    @Override
    public void accept(OpenAPI openAPI) {
        hashToName = new HashMap<>(computeHashes(openAPI));
        super.accept(openAPI);
    }

    private Map<String, String> computeHashes(OpenAPI openAPI) {
        return new OasWrapper(openAPI).schemas().entrySet().stream()
                .filter(e -> OasUtils.isReferencingSchema(e.getValue()))
                .map(e -> {
                    var hash = toHash((ComposedSchema) e.getValue()).get();
                    return Map.entry(hash, e.getKey());
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getKey));
    }

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        if (property instanceof ArraySchema) {
            var s = ((ArraySchema) property).getItems();
            var converted = convertProperty(name, s);
            ((ArraySchema) property).setItems(converted.getValue());
            return Map.entry(converted.getKey(), property);
        }

        return convertProperty(name, property);
    }

    private Map.Entry<String, Schema> convertProperty(String name, Schema property) {
        if (property instanceof ComposedSchema) {

            final ComposedSchema cs = (ComposedSchema) property;

            Optional<String> existing = find(cs);
            if (existing.isPresent()) {
                log.info("Refactoring property {} to point to already existing schema with name {}", name, existing.get());
                return Map.entry(
                        name,
                        referencing(cs, existing.get()));
            }

            Map<String, Schema> types = api.schemas();

            var nameCandidate = toName(cs)
                    .orElse(proposeName(name));

            log.info("Refactoring {}.{} to separate schema: {}", currentType, name, nameCandidate);
            var newSchema = referencing(cs, nameCandidate);

            types.put(nameCandidate, cs);
            updateHashes(nameCandidate, cs);

            return Map.entry(name, newSchema);
        } else {
            return Map.entry(name, property);
        }
    }

    private void updateHashes(String name, ComposedSchema schema) {
        var hash = toHash(schema);
        hashToName.put(hash.get(), name);
    }

    private String proposeName(String name) {
        Map<String, Schema> types = api.schemas();
        String candidate = WordUtils.capitalize(name);
        if (types.containsKey(candidate)) {
            candidate = currentType + candidate;
        }
        return candidate;
    }

    private Schema referencing(Schema prop, String name) {
        return new Schema<>()
                .$ref(OasUtils.toSchemRef(name))
                .description(prop.getDescription());
    }

    private Optional<String> find(ComposedSchema schema) {
        if (OasUtils.isReferencingSchema(schema)) {
            return toHash(schema)
                    .flatMap(h -> Optional.ofNullable(hashToName.get(h)));
        }
        return Optional.empty();
    }

    private Optional<String> toName(ComposedSchema schema) {
        return Stream.of(
                schema.getAllOf(),
                schema.getOneOf()
        ).flatMap(l -> commonName(l).stream())
                .findFirst();
    }

    private Optional<String> commonName(List<Schema> refSchemas) {
        if (refSchemas == null) {
            return Optional.empty();
        }
        var prefix = refSchemas.stream()
                .flatMap(r -> Optional.ofNullable(OasUtils.toSchemaName(r.get$ref())).stream())
                .reduce(StringUtils::getCommonPrefix);
        return prefix.filter(StringUtils::isNotBlank);
    }

    private Optional<String> toHash(ComposedSchema schema) {
        return Stream.of(
                schema.getAllOf(),
                schema.getOneOf()
        )
                .flatMap(l -> refToHash(l).stream())
                .findFirst();
    }

    private Optional<String> refToHash(List<Schema> refSchemas) {
        if (refSchemas == null || refSchemas.isEmpty()) {
            return Optional.empty();
        }

        var x = refSchemas.stream()
                .map(Schema::get$ref)
                .sorted()
                .collect(Collectors.joining());

        return Optional.of(DigestUtils.md2Hex(x));
    }
}
