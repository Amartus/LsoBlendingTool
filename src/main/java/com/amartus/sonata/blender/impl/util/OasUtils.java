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

package com.amartus.sonata.blender.impl.util;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface OasUtils {
    Logger log = LoggerFactory.getLogger(OasUtils.class);

    static String toSchemaName(String ref) {
        var frag = URI.create(ref).getFragment();
        if (frag == null) return null;

        var segments = frag.split("/");
        return segments[segments.length - 1];
    }

    static String toSchemRef(String name) {
        return "#/components/schemas/" + name;
    }

    static OpenAPI readOas(String path) {
        log.debug("Reading {}", path);
        var options = new ParseOptions();
//        options.setResolveFully(true);
        options.setResolve(true);
        try {
            SwaggerParseResult result = new OpenAPIParser().readLocation(path, List.of(), options);
            var api = result.getOpenAPI();
            if (api == null) {
                log.warn("Location {} does not contain a valid schema", path);
                throw new RuntimeException(String.format("%s is not a valid schema", path));
            }
            return api;
        } catch (RuntimeException e) {
            log.warn("Cannot read schema from {}", path);
            throw e;
        }
    }

    static boolean isReferencingSchema(Schema<?> schema) {
        Predicate<List<Schema>> onlyReferences = x -> {
            var refs = Helpers.safeConvert.andThen(Helpers.references)
                    .apply(x);
            return refs > 0 && refs == x.size();
        };
        if (schema instanceof ComposedSchema) {
            return
                    onlyReferences.test(schema.getAllOf())
                            || onlyReferences.test(schema.getOneOf())
                            || onlyReferences.test(schema.getAnyOf());

        }
        return false;
    }

    static long countReferences(Schema schema) {
        final var counter = Helpers.safeConvert.andThen(Helpers.references);
        if (schema instanceof ObjectSchema) {
            return Helpers.references.apply(Stream.of(schema));
        }
        if (schema instanceof ComposedSchema) {
            var cs = (ComposedSchema) schema;
            return Stream.of(
                    counter.apply(cs.getAllOf()),
                    counter.apply(cs.getAnyOf()),
                    counter.apply(cs.getOneOf())
            ).reduce(0L, Long::sum);
        }
        return 0;
    }

    static Stream<Schema> allSchemas(ComposedSchema cs) {
        return Stream.of(
                cs.getAllOf(),
                cs.getAnyOf(),
                cs.getOneOf()
        ).flatMap(Helpers.safeConvert);
    }

    List<String> discriminatorCandidates = List.of(
            "@type",
            "mapType"
    );

    static Optional<String> findDiscriminator(Collection<Schema> schemas) {
        return discriminatorCandidates.stream()
                .filter(k -> {
                    var hasProperty = toPredicate(k);
                    return schemas.stream().allMatch(hasProperty::test);
                }).findFirst();
    }

    static private Predicate<Schema> toPredicate(String keyword) {
        return s -> s.getProperties() != null && s.getProperties().containsKey(keyword);
    }


}

class Helpers {
    static final Function<Stream<Schema>, Long> references = s -> s
            .filter(
                    x -> x.get$ref() != null
                            && Optional.ofNullable(x.getProperties()).map(Map::isEmpty).orElse(true)
            ).count();

    static final Function<List<Schema>, Stream<Schema>> safeConvert = x -> Optional.ofNullable(x)
            .stream()
            .flatMap(Collection::stream);
}
