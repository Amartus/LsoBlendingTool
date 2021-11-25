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

import com.amartus.sonata.blender.impl.specifications.FragmentBasedNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.PathBaseNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.ProductSpecificationNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.UrnBasedNamingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.parser.util.DeserializationUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unchecked"})
public class ProductSpecReader {
    public static final String DISCRIMINATOR_VALUE = "x-discriminator-value";

    private static final String KEY = "___to_remove";
    private static final Logger log = LoggerFactory.getLogger(ProductSpecReader.class);
    private final Path schemaPath;
    private final String modelToAugment;
    private final List<ProductSpecificationNamingStrategy> namingStrategies;
    private final Charset charset = StandardCharsets.UTF_8;
    private final String fragment;

    public ProductSpecReader(String modelToAugment, Path schemaLocation) {
        this(modelToAugment, schemaLocation, "");
    }

    public ProductSpecReader(String modelToAugment, Path schemaLocation, String fragment) {
        this.schemaPath = Objects.requireNonNull(schemaLocation).toAbsolutePath();
        this.fragment = Objects.requireNonNull(fragment);
        if (!Files.exists(this.schemaPath)) {
            throw new IllegalArgumentException("Path " + this.schemaPath + " does not exists");
        }


        this.modelToAugment = modelToAugment;
        this.namingStrategies = Stream.of(
                new UrnBasedNamingStrategy(),
                new FragmentBasedNamingStrategy(),
                new PathBaseNamingStrategy()
        ).collect(Collectors.toList());
    }

    public Map<String, Schema<?>> readSchemas() {
        log.info("Resolving {}", this.schemaPath);
        OpenAPI api = new OpenAPI();

        ComposedSchema schema = new ComposedSchema()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/" + modelToAugment))
                .addAllOfItem(new Schema<>().$ref(schemaName()));
        api.schema(KEY, schema);

        ProductSpecificationNamingStrategy.NameAndDiscriminator productName;
        try {
            productName = toName();
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read schema for %s", schemaPath), e);
        }

        Map<String, Schema<?>> resolved = resolve(schema);
        ComposedSchema wrapper = (ComposedSchema) resolved.remove(KEY);
        Schema extensionParent = wrapper.getAllOf().get(0);
        Schema specification = resolved.remove(toRefName(wrapper.getAllOf().get(1)));
        Map<String, Object> extensions = specification.getExtensions();
        if (extensions == null) {
            extensions = new HashMap<>();
        }

        if (!extensions.containsKey(DISCRIMINATOR_VALUE)) {
            extensions.put(DISCRIMINATOR_VALUE, productName.getDiscriminatorValue());
        }

        var allOf = Stream.concat(Stream.of(extensionParent), unpack(specification));

        Schema target = new ComposedSchema()
                .allOf(allOf.collect(Collectors.toList()))
                .extensions(extensions);

        target
                .extensions(extensions)
                .title(null);

        specification
                .extensions(null)
                .title(null);

        resolved.put(productName.getName(), target);

        return resolved;
    }

    private String schemaName() {
        return schemaPath.getFileName().toString() + fragment;
    }

    private Stream<Schema> unpack(Schema specification) {
        if (specification instanceof ComposedSchema) {
            var cs = (ComposedSchema) specification;
            var composition = Stream.of(
                    Optional.ofNullable(cs.getAllOf()),
                    Optional.ofNullable(cs.getOneOf()),
                    Optional.ofNullable(cs.getAnyOf())
            ).flatMap(Optional::stream).flatMap(Collection::stream);

            var object = Optional.ofNullable(specification.getProperties())
                    .map(p -> new ObjectSchema()
                            .properties(p).description(specification.getDescription())
                    ).stream();

            return Stream.concat(composition, object);

        }
        return Stream.of(specification);
    }

    private ProductSpecificationNamingStrategy.NameAndDiscriminator toName() throws IOException {
        Path file = schemaPath;

        var content = Files.readString(file, charset);
        JsonNode tree = DeserializationUtils.deserializeIntoTree(content, file.toString());

        var uri = URI.create(schemaName()).resolve(fragment);

        //TODO consider passing only tree fragment referenced by URI fragment
        return namingStrategies.stream()
                .flatMap(str -> str.provideNameAndDiscriminator(uri, tree).stream())
                .findFirst()
                .orElse(new ProductSpecificationNamingStrategy.NameAndDiscriminator(schemaName()));
    }

    private String toRefName(Schema schema) {
        return schema.get$ref().replace("#/components/schemas/", "");
    }

    private Map<String, Schema<?>> resolve(ComposedSchema schema) {
        var parentFile = schemaPath.toString();
        OpenAPIResolver r = new OpenAPIResolver(new OpenAPI().schema(KEY, schema), null, parentFile);
        return r.resolve().getComponents().getSchemas()
                .entrySet().stream()
                .map(e -> Map.entry(e.getKey(), (Schema<?>) e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
