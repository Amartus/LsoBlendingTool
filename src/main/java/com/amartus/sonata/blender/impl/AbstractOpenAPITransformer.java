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

package com.amartus.sonata.blender.impl;

import com.google.common.collect.Streams;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractOpenAPITransformer implements Consumer<OpenAPI> {

    Predicate<Schema> refProperty = s -> s.get$ref() != null;
    Function<Schema, Stream<Map.Entry<String, Schema<?>>>> properties = schema -> schema == null ? Stream.empty()
            : Optional.ofNullable(schema.getProperties()).map(m -> m.entrySet().stream()).orElse(Stream.empty());
    Function<OpenAPI, Stream<Map.Entry<String, Schema>>> schemas = openAPI -> openAPI == null ? Stream.empty()
            : openAPI.getComponents().getSchemas().entrySet().stream();

    protected Stream<Schema> allProperties(Schema src) {
        Stream<Schema> result = properties.apply(src).map(Map.Entry::getValue);
        if (src instanceof ComposedSchema) {
            return Streams.concat(result, fromComposed((ComposedSchema) src));
        }
        return result;
    }

    private Stream<Schema> fromComposed(ComposedSchema src) {

        Function<List<Schema>, Stream<Schema>> map = list -> Optional.ofNullable(list)
                .map(s -> s.stream().flatMap(properties).map(x -> (Schema) x.getValue()))
                .orElse(Stream.empty());

        return Stream.of(
                map.apply(src.getAllOf()),
                map.apply(src.getAnyOf()),
                map.apply(src.getOneOf())
        ).flatMap(x -> x);

    }
}
