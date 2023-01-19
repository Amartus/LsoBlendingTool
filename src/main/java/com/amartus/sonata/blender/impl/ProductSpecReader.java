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
import com.amartus.sonata.blender.parser.DeserializerProvider;
import com.amartus.sonata.blender.parser.OpenAPIResolver;
import com.amartus.sonata.blender.parser.ResolverCache;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.parser.util.DeserializationUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
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
        return readSchemas(SpecVersion.V30);
    }

    public Map<String, Schema<?>> readSchemas(SpecVersion version) {
        log.info("Resolving {}", this.schemaPath);
        OpenAPI api = new OpenAPI();

        var schema = new ComposedSchema()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/" + modelToAugment))
                .addAllOfItem(new Schema<>().$ref(schemaName()));
        api.schema(KEY, schema);

        ProductSpecificationNamingStrategy.NameAndDiscriminator productName;
        try {
            productName = toName();
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read schema for %s", schemaPath), e);
        }

        Map<String, Schema<?>> resolved = resolve(schema, version);
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

    private String  schemaName() {
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
        final Path file = schemaPath;
        final JsonNode tree = DeserializationUtils.deserializeIntoTree(Files.readString(file, charset), file.toString());

        final var uri =
                Optional.ofNullable(fragment)
                    .flatMap(f -> f.isBlank() ? Optional.empty() : Optional.of(f))
                    .map(f -> URI.create(schemaName()).resolve(f))
                    .orElse(URI.create(schemaName()));

        //TODO consider passing only tree fragment referenced by URI fragment
        return namingStrategies.stream()
                .flatMap(str -> str.provideNameAndDiscriminator(uri, tree).stream())
                .findFirst()
                .orElse(new ProductSpecificationNamingStrategy.NameAndDiscriminator(schemaName()));
    }

    private String toRefName(Schema schema) {
        return schema.get$ref().replace("#/components/schemas/", "");
    }

    protected Map<String, Schema<?>> resolve(Schema schema, SpecVersion version) {
        var parentFile = schemaPath.toString();
        var options = new ParseOptions();
        options.setResolve(true);
        options.setValidateExternalRefs(true);


        var oas = new OpenAPI(version)
                .openapi(version == SpecVersion.V31 ? "3.1" : "3.0")
                .schema(KEY, schema);

        var cache = new ResolverCache(oas, parentFile, options, new DeserializerProvider());

        OpenAPIResolver r = new OpenAPIResolver(oas, cache, null);
        var res = new SwaggerParseResult().messages(new ArrayList<>());
        if(version == SpecVersion.V31) {
            res.setOpenapi31(true);
        }
        r.resolve(res);

        if (!res.getMessages().isEmpty()) {
            log.warn("Potential issues found while resolving definitions from file {}:\n\t{}",
                    parentFile, String.join("\n\t", res.getMessages()));
        }

        return res.getOpenAPI()
                .getComponents().getSchemas()
                .entrySet().stream()
                .map(e -> Map.entry(e.getKey(), (Schema<?>) e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
