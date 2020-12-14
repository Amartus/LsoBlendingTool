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
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amartus.sonata.blender.impl.util.PathUtils.toFileName;
import static com.amartus.sonata.blender.impl.util.PathUtils.toRelative;

public class ProductSpecReader {
    public static final String DISCRIMINATOR_VALUE = "x-discriminator-value";

    private static final String KEY = "___to_remove";
    private static final Logger log = LoggerFactory.getLogger(ProductSpecReader.class);
    private final String schemaPath;
    private final String modelToAugment;
    private final Path parentLocation;
    private final List<ProductSpecificationNamingStrategy> namingStrategies;
    private Charset charset = Charset.forName("utf8");

    public ProductSpecReader(String modelToAugment, Path parentLocation, String schemaPath) {

        this.schemaPath = schemaPath;
        this.parentLocation = parentLocation == null ? Path.of(".") : toRelative(parentLocation);
        if (!Files.isDirectory(this.parentLocation)) {
            throw new IllegalArgumentException("Path " + this.parentLocation + " is not a directory");
        }

        log.debug("Root for schemas {}", this.parentLocation);
        log.debug("Relative schema path {}", schemaPath);
        this.modelToAugment = modelToAugment;
        this.namingStrategies = Stream.of(
                new UrnBasedNamingStrategy(),
                new FragmentBasedNamingStrategy(),
                new PathBaseNamingStrategy()
        ).collect(Collectors.toList());
    }

    public Map<String, Schema> readSchemas() {
        OpenAPI api = new OpenAPI();

        ComposedSchema schema = new ComposedSchema()
                .addAllOfItem(new Schema().$ref("#/components/schemas/" + modelToAugment))
                .addAllOfItem(new Schema().$ref(schemaPath));
        api.schema(KEY, schema);

        ProductSpecificationNamingStrategy.NameAndDiscriminator productName = null;
        try {
            productName = toName(schemaPath);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read schema for %s", schema()), e);
        }

        Map<String, Schema> resolved = resolve(schema);
        ComposedSchema wrapper = (ComposedSchema) resolved.remove(KEY);
        Schema productCharacteristicsRef = wrapper.getAllOf().get(0);
        Schema specification = resolved.remove(toRefName(wrapper.getAllOf().get(1)));
        Map<String, Object> extensions = specification.getExtensions();
        if (extensions == null) {
            extensions = new HashMap<>();
        }

        if (!extensions.containsKey(DISCRIMINATOR_VALUE)) {
            extensions.put(DISCRIMINATOR_VALUE, productName.getDiscriminatorValue());
        }

        Schema target = new ComposedSchema()
                .addAllOfItem(productCharacteristicsRef)
                .addAllOfItem(specification)
                .extensions(extensions);

        target
                .extensions(extensions)
                .title(specification.getTitle());

        specification
                .extensions(null)
                .title(null);

        resolved.put(productName.getName(), target);

        return resolved;
    }

    private ProductSpecificationNamingStrategy.NameAndDiscriminator toName(String schemaPath) throws IOException {
        Path file = schema();

        var content = Files.readString(file, charset);
        JsonNode tree = DeserializationUtils.deserializeIntoTree(content, file.toString());

        return namingStrategies.stream()
                .flatMap(str -> str.provideNameAndDiscriminator(schemaPath, tree).stream())
                .findFirst()
                .orElse(new ProductSpecificationNamingStrategy.NameAndDiscriminator(schemaPath));
    }

    private String toRefName(Schema schema) {
        return schema.get$ref().replace("#/components/schemas/", "");
    }

    private Map<String, Schema> resolve(ComposedSchema schema) {
        OpenAPIResolver r = new OpenAPIResolver(new OpenAPI().schema(KEY, schema), null, schema().toString());
        return r.resolve()
                .getComponents().getSchemas();
    }

    private Path schema() {
        return parentLocation.resolve(toFileName(schemaPath));
    }

}
