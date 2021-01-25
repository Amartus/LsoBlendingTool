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
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Convert top level one of schemas to all of polymorphic pattern
 *
 * @author bartosz.michalik@amartus.com
 */
public class ConvertOneOfToAllOffInheritance implements Consumer<OpenAPI> {

    private static final Logger log = LoggerFactory.getLogger(ConvertOneOfToAllOffInheritance.class);
    private OasWrapper openAPI;
    private Predicate<Schema> isReference = s -> s.get$ref() != null;
    private Predicate<Schema> isOneOf = s -> {
        if (s instanceof ComposedSchema) {
            ComposedSchema c = (ComposedSchema) s;
            return c.getOneOf() != null && !c.getOneOf().isEmpty();
        }
        return false;
    };

    private static <T, U> Map.Entry<String, U> convertValue(Map.Entry<String, T> entry, Function<T, U> mapper) {
        var s = mapper.apply(entry.getValue());
        return Map.entry(entry.getKey(), s);
    }

    private static <T, U> Map.Entry<String, Set<U>> convertValues(Map.Entry<String, Set<T>> entry, Function<T, U> mapper) {
        var s = entry.getValue().stream()
                .map(mapper::apply)
                .collect(Collectors.toSet());
        return Map.entry(entry.getKey(), s);
    }

    @Override
    public void accept(OpenAPI openAPI) {
        this.openAPI = new OasWrapper(openAPI);
        var toProcess = prepare();

        toProcess.entrySet().forEach(this::process);

        log.info("Processing {}", toProcess);
    }

    private void process(Map.Entry<String, Set<String>> toResolve) {
        var schemasToResolve = toResolve.getValue().stream()
                .flatMap(s -> openAPI.schema(s).stream().map(sc -> Map.entry(s, sc)))
                .collect(Collectors.toSet());

        //TODO check all schemas used only in oneOf

        var disc = OasUtils.findDiscriminator(schemasToResolve.stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList()));
        if (disc.isEmpty()) {
            log.warn("Cannot find shared discriminator for schemas {}", toResolve.getValue());
            return;
        }

        Map<String, Schema> props = schemasToResolve.iterator().next().getValue().getProperties();

        var parent = new ObjectSchema()
                .discriminator(new Discriminator().propertyName(disc.get()))
                .required(List.of(disc.get()))
                .addProperties(disc.get(), props.get(disc.get()));

        var converted = schemasToResolve.stream()
                .map(e -> convertValue(e, x -> convertToAllOf(toResolve.getKey(), disc.get(), x)))
                .collect(Collectors.toSet());

        var schemas = openAPI.schemas();
        schemas.put(toResolve.getKey(), parent);

        converted.forEach(e -> schemas.put(e.getKey(), e.getValue()));
    }

    private Schema convertToAllOf(String parent, String discriminatorName, Schema s) {
        var result = new ComposedSchema();
        result.addAllOfItem(
                new Schema<>().$ref("#/components/schemas/" + parent));
        result.setTitle(s.getTitle());
        result.setExtensions(s.getExtensions());
        s.setTitle(null);
        s.setExtensions(null);
        s.getProperties().remove(discriminatorName);

        result.addAllOfItem(s);


        return result;
    }

    private Map<String, Set<String>> prepare() {
        var allSchemas = openAPI.schemas();
        return allSchemas.entrySet().stream()
                .filter(e -> isOneOf.test(e.getValue()))
                .map(this::convertToOneOf)
                .filter(e -> e.getValue().stream().allMatch(isReference))
                .map(e -> convertValues(e, v -> OasUtils.toSchemaName(v.get$ref())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, Set<Schema>> convertToOneOf(Map.Entry<String, Schema> e) {
        var hs = new HashSet(((ComposedSchema) e.getValue()).getOneOf());
        return Map.entry(e.getKey(), hs);
    }

}
