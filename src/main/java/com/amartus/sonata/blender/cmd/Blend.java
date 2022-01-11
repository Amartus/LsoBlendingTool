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

package com.amartus.sonata.blender.cmd;

import com.amartus.sonata.blender.impl.MergeSchemasAction;
import com.amartus.sonata.blender.impl.postprocess.*;
import com.amartus.sonata.blender.impl.specifications.UrnBasedNamingStrategy;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Once;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Command(name = "blend", description = "Blend Product Specifications into OpenAPI.")
public class Blend extends AbstractCmd implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Blend.class);

    @Option(
            name = {"--sorted"},
            title = "sort data types",
            description = "sort data types in a lexical order")
    @Once
    private boolean sorted = false;
    private UrnPredicate urnPredicate;

    @Override
    public void run() {
        if (allSchemas != null) {
            blendedSchema = findAllProductSpecifications(allSchemas);

        } else {
            blendedSchema = blendingSchemas().collect(Collectors.toList());
        }
        productSpecifications = List.of();

        validateProductSpecs(blendedSchema);
        OpenAPI openAPI;
        try {
            openAPI = readApi();
        } catch (Exception e) {
            return;
        }

        Map<String, Schema> schemasToInject = this.toProductSpecifications();

        log.debug("Injecting {} schemas from {} product spec descriptions",
                schemasToInject.size(), blendedSchema.size());

        new MergeSchemasAction(modelToAugment, strict)
                .schemasToInject(schemasToInject)
                .target(openAPI)
                .execute();

        new RemoveSuperflousTypeDeclarations().accept(openAPI);
        new PropertyEnumExternalize().accept(openAPI);
        new ComposedPropertyToType().accept(openAPI);
        new SingleEnumToDiscriminatorValue().accept(openAPI);
        new ConvertOneOfToAllOffInheritance().accept(openAPI);
        new UpdateDiscriminatorMapping().accept(openAPI);
        new ConstrainDiscriminatorValueWithEnum().accept(openAPI);
        if (sorted) {
            new SortTypesByName().accept(openAPI);
        }

        var mapper = SerializationUtils.yamlMapper();

        try {
            File output = new File(this.spec + ".modified");
            log.info("Writing to {}", output);
            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }

    private OpenAPI readApi() {
        var options = new ParseOptions();
//        options.setResolveFully(true);
        options.setResolve(true);
        try {
            SwaggerParseResult result = new OpenAPIParser().readLocation(this.spec, List.of(), options);
            var api = result.getOpenAPI();
            if (api == null) {
                log.warn("Location {} does not contain a valid schema", this.spec);
                throw new RuntimeException(String.format("%s is not a valid schema", this.spec));
            }
            return api;
        } catch (RuntimeException e) {
            log.warn("Cannot read schema from {}", this.spec);
            throw e;
        }
    }

    private List<String> findAllProductSpecifications(String functionName) {
        var root = Path.of(productsRootDir);
        var toInclude = getUrnPredicate(functionName);
        try {

            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(toInclude)
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());

//            return Files.list(root)
//                    .filter(f -> !Files.isDirectory(f))
//                    .filter(toInclude)
//                    .map(p -> root.relativize(p).toString())
//                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read %s", root), e);
        }
    }

    private UrnPredicate getUrnPredicate(String functionName) {
        if (urnPredicate == null) {
            urnPredicate = new UrnPredicate(functionName);
        }
        return urnPredicate;
    }

    private static class UrnPredicate implements Predicate<Path> {
        private final ObjectMapper json;
        private final ObjectMapper yaml;
        private final UrnBasedNamingStrategy namingStrategy;
        private final String functionName;

        private UrnPredicate(String functionName) {
            this.json = SerializationUtils.jsonMapper();
            this.yaml = SerializationUtils.yamlMapper();
            this.namingStrategy = new UrnBasedNamingStrategy();
            this.functionName = functionName;
        }

        @Override
        public boolean test(Path path) {
            var toInclude = namingStrategy.provideNameAndDiscriminator(null, read(path))
                    .map(n -> {
                        String disc = n.getDiscriminatorValue();
                        return (disc.endsWith("all") || disc.endsWith(functionName));
                    })
                    .orElse(false);
            if (!toInclude) {
                log.info("{} is not a valid MEF URN for {} function. skipping", path, functionName);
            }
            return toInclude;
        }

        private JsonNode read(Path path) {
            File file = path.toFile();
            try {
                return json.readTree(file);

            } catch (IOException e) {
                try {
                    return yaml.readTree(file);
                } catch (IOException ioException) {
                    return null;
                }
            }
        }
    }
}




