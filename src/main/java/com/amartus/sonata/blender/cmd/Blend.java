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
import com.amartus.sonata.blender.impl.postprocess.ComposedPostprocessor;
import com.amartus.sonata.blender.impl.postprocess.SecureEndpointsWithOAuth2;
import com.amartus.sonata.blender.impl.postprocess.SortTypesByName;
import com.amartus.sonata.blender.impl.util.IdSchemaResolver;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.AllowedEnumValues;
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
import java.util.stream.Collectors;

@Command(name = "blend", description = "Blend Product / Service Specifications into OpenAPI.")
public class Blend extends AbstractBlend implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Blend.class);

    @Option(
            name = {"-f", "--force-override"},
            description = "Override output if exist"
    )
    @Once
    private boolean forceWrite = false;

    @Option(
            name = {"--sorted"},
            title = "sort data types",
            description = "sort data types in a lexical order")
    @Once
    private boolean sorted = false;

    @Option(
            name = {"-o", "--output"},
            title = "Output file name",
            description = "Output file name. Throws exception if file exists. If it is not provided output file is 'output-spec'.modified"
    )
    @Once
    private String outputFile;
    @Option(
            name = {"--path-security"},
            description = "mechanism to use to secure API paths. default disabled"

    )
    @AllowedEnumValues(PathSecurity.class)
    private PathSecurity pathSecurity = PathSecurity.disabled;

    @Override
    public void run() {
        if (allSchemas != null) {
            blendedSchema = findAllProductSpecifications(allSchemas);

        } else {
            blendedSchema = blendingSchemas().collect(Collectors.toList());
        }
        productSpecifications = List.of();

        validateProductSpecs();
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

        new ComposedPostprocessor().accept(openAPI);
        if (sorted) {
            new SortTypesByName().accept(openAPI);
        }

        if (pathSecurity == PathSecurity.oauth2) {
            new SecureEndpointsWithOAuth2().accept(openAPI);
        }

        var mapper = SerializationUtils.yamlMapper();

        try {
            File output = output();
            log.info("Writing to {}", output);
            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }


    private enum PathSecurity {
        oauth2, disabled
    }

    private List<String> findAllProductSpecifications(String allSchemas) {
        return new IdSchemaResolver(allSchemas)
                .findProductSpecifications(Path.of(productsRootDir)).stream()
                .map(Path::toString)
                .collect(Collectors.toList());
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

    private File output() {
        var fileName = Path.of(outputFile != null ? outputFile : this.spec + ".modified");
        if (Files.exists(fileName) && !forceWrite) {
            log.warn("Output: {} exists. please add force flag if you want to override it.", fileName.toAbsolutePath());
            throw new IllegalArgumentException("Cannot override " + fileName);
        }
        if (Files.isDirectory(fileName)) {
            log.warn("Output {} is a directory", fileName.toAbsolutePath());
            throw new IllegalArgumentException(fileName + " is a directory");
        }
        return fileName.toFile();
    }
}




