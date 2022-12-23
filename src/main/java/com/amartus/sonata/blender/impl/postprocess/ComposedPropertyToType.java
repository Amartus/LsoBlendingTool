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
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Uplift composed property declaration to types
 *
 * @author bartosz.michalik@amartus.com
 */
public class ComposedPropertyToType extends PropertyPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ComposedPropertyToType.class);

    private Map<String, Set<String>> hashToName;

    @Override
    public void accept(OpenAPI openAPI) {
        hashToName = new HashMap<>(computeHashes(openAPI));
        super.accept(openAPI);
    }

    private Map<String, Set<String>> computeHashes(OpenAPI openAPI) {
        return new OasWrapper(openAPI).schemas().entrySet().stream()
                .filter(e -> OasUtils.isReferencingSchema(e.getValue()))
                .map(e -> {
                    var hash = toHash((ComposedSchema) e.getValue()).orElseThrow();
                    return Map.entry(hash, e.getKey());
                })
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
    }

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        if (property instanceof ArraySchema) {
            var s = ((ArraySchema) property).getItems();
            var converted = convertProperty(name, s);
            property.setItems(converted.getValue());
            return Map.entry(converted.getKey(), property);
        }

        return convertProperty(name, property);
    }

    private Map.Entry<String, Schema> convertProperty(String name, Schema property) {
        if (OasUtils.countReferences(property) > 1) {

            final ComposedSchema cs = (ComposedSchema) property;

            var existing = findUnique(cs);
            if (existing.isPresent()) {
                log.info("Refactoring property {} to point to already existing schema with name {}", name, existing.get());
                return Map.entry(
                        name,
                        referencing(cs, existing.get()));
            }

            var nameCandidate = toName(cs)
                    .orElse(proposeName(name));

            log.info("Refactoring {}.{} to separate schema: {}", currentType, name, nameCandidate);
            var newSchema = referencing(cs, nameCandidate);
            registerNewSchema(nameCandidate, cs);

            return Map.entry(name, newSchema);
        } else {
            if (property instanceof ComposedSchema) {
                return Map.entry(name, toObjectSchema((ComposedSchema) property));
            }
            return Map.entry(name, property);
        }
    }

    private Schema toObjectSchema(ComposedSchema cs) {
        var ref = OasUtils.allSchemas(cs).findFirst().orElseThrow();
        return new ObjectSchema()
                .$ref(ref.get$ref())
                .description(cs.getDescription())
                .extensions(cs.getExtensions());
    }

    @Override
    protected void registerNewSchema(String name, Schema<?> schema) {
        super.registerNewSchema(name, schema);
        updateHashes(name, (ComposedSchema) schema);
    }

    private void updateHashes(String name, ComposedSchema schema) {
        var hash = toHash(schema);

        hash.ifPresent(h ->
                hashToName.computeIfAbsent(h, x -> new HashSet<>()).add(name)
        );
    }

    private Optional<String> findUnique(ComposedSchema schema) {
        if (OasUtils.isReferencingSchema(schema)) {
            return toHash(schema)
                    .map(this::byHash)
                    .stream()
                    .flatMap(Collection::stream)
                    .findFirst();
        }
        return Optional.empty();
    }

    private Set<String> byHash(String hash) {
        var resp = hashToName.getOrDefault(hash, Set.of());
        if (resp.size() > 1) {
            log.warn("Multiple answers for hash {} -> {}. reduction not possible", hash, resp);
            return Set.of();
        }
        return resp;
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
                schema.getAnyOf(),
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
