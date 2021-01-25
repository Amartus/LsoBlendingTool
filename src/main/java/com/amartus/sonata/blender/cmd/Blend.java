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
import com.amartus.sonata.blender.impl.postprocess.ComposedPropertyToType;
import com.amartus.sonata.blender.impl.postprocess.ConvertOneOfToAllOffInheritance;
import com.amartus.sonata.blender.impl.postprocess.PropertyEnumExternalize;
import com.amartus.sonata.blender.impl.postprocess.RemoveSuperflousTypeDeclarations;
import com.amartus.sonata.blender.impl.postprocess.SingleEnumToDiscriminatorValue;
import com.amartus.sonata.blender.impl.postprocess.SortTypesByName;
import com.amartus.sonata.blender.impl.postprocess.UpdateDiscriminatorMapping;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
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
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    @Override
    public void run() {
        if (allSchemas) {
            productSpecifications = findAllProductSpecifications();
        }

        validateProductSpecs(productSpecifications);


        var options = new ParseOptions();
//        options.setResolveFully(true);
        options.setResolve(true);

        SwaggerParseResult result = new OpenAPIParser().readLocation(this.spec, List.of(), options);
        OpenAPI openAPI = result.getOpenAPI();
        Map<String, Schema> schemasToInject = this.toProductSpecifications();

        log.debug("Injecting {} schemas from {} product spec descriptions",
                schemasToInject.size(), productSpecifications.size());

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
        if (sorted) {
            new SortTypesByName().accept(openAPI);
        }

        var mapper = SerializationUtils.yamlMapper();

        try {
            File output = new File(this.spec + ".modified");
            File snake = new File(this.spec + ".modified.snake");
            log.info("Writing to {}", output);

            var yaml = new Yaml();
            yaml.dump(openAPI, new FileWriter(snake));

            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }

    private List<String> findAllProductSpecifications() {
        var root = Path.of(productsRootDir);
        try {
            return Files.list(root)
                    .filter(f -> !Files.isDirectory(f))
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read %s", root), e);
        }
    }
}


