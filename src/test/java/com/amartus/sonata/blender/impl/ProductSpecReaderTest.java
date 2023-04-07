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

import com.amartus.Utils;
import com.amartus.sonata.blender.parser.DeserializerProvider;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amartus.sonata.blender.impl.ProductSpecReader.DISCRIMINATOR_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductSpecReaderTest {
    private final DeserializerProvider deserializerProvider = new DeserializerProvider();
    @Test
    public void testReaderForSchema() {
        var dirPath = Utils.toPath("mini-model").toAbsolutePath();
        var schemas = new ProductSpecReader("testToAugment", dirPath.resolve("model-js.json"))
                .readSchemas();
        singleRootSchema(schemas);
        assertEquals(7, schemas.size());
    }

    @Test
    public void testReaderForSchemaUsingRelativePaths() {
        var dirPath = Utils.toPath("mini-model").toAbsolutePath();
        dirPath = Paths.get(".").toAbsolutePath().relativize(dirPath);
        var schemas = new ProductSpecReader("testToAugment", dirPath.resolve("model-js.json"))
                .readSchemas();
        singleRootSchema(schemas);
        assertEquals(7, schemas.size());
    }

    @Test
    public void testReaderForSchemaOas() {
        var dirPath = Utils.toPath("mini-model");
        var schemas = new ProductSpecReader(ProductSpecReader.Options.forName("testToAugment"), dirPath.resolve("model-oas.yaml"), "#/components/schemas/ModelOAS", deserializerProvider, ProductSpecReader.defaultOptions())
                .readSchemas();
        singleRootSchema(schemas);
        assertEquals(7, schemas.size());
    }

    @Test
    public void testReadComposedModel() {
        var dirPath = Utils.toPath("ref-model");
        var schemas = new ProductSpecReader(ProductSpecReader.Options.forName("testToAugment"), dirPath.resolve("root.yaml"), "#/components/schemas/Root", deserializerProvider, ProductSpecReader.defaultOptions())
                .readSchemas();
        singleRootSchema(schemas);
        assertEquals(6, schemas.size());
    }

    @Test
    public void testProtectDescriptions() {
        final var name = "typeRoot.yaml";
        var dirPath = Utils.toPath("protect-descriptions");
        var schemas = new ProductSpecReader("testToAugment", dirPath.resolve(name))
                .readSchemas();
        singleRootSchema(schemas);
        var root = (ObjectSchema) schemas.get(name).getAllOf().get(1);
        root.getProperties().forEach((k,v) -> assertNotNull(v.getDescription()));
    }

    private void singleRootSchema(Map<String, Schema<?>> allSchemas) {
        assertEquals(1, markedSchemas(allSchemas)
                .collect(Collectors.toSet()).size());
    }

    private Stream<String> markedSchemas(Map<String, Schema<?>> allSchemas) {
        Predicate<Schema<?>> isMarked = s -> Optional.ofNullable(s.getExtensions())
                .map(e -> e.containsKey(DISCRIMINATOR_VALUE))
                .orElse(false);

        return allSchemas.entrySet().stream()
                .filter(e -> isMarked.test(e.getValue()))
                .map(Map.Entry::getKey);
    }

}