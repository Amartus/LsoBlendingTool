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

import com.amartus.sonata.blender.impl.BlendingService;
import com.amartus.sonata.blender.impl.MergeSchemasAction;
import com.amartus.sonata.blender.impl.SpecValidator;
import com.amartus.sonata.blender.impl.postprocess.ComposedPostprocessor;
import com.amartus.sonata.blender.impl.postprocess.SecureEndpointsWithOAuth2;
import com.amartus.sonata.blender.impl.postprocess.SortTypesByName;
import com.amartus.sonata.blender.impl.util.IdSchemaResolver;
import com.amartus.sonata.blender.impl.util.OasUtils;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.AllowedEnumValues;
import com.github.rvesse.airline.annotations.restrictions.Once;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
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
            name = {"--validate"},
            title = "validate OAS with 3.0.x schema",
            description = "Validate consistency of OAS definition with its 3.0.x json schema definition")
    @Once
    private boolean validateOutput = false;

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

        validateProductSpecs();
        OpenAPI openAPI;
        try {
            openAPI = OasUtils.readOas(this.spec);
        } catch (Exception e) {
            return;
        }

        Map<String, Schema> schemasToInject = this.toProductSpecifications();

        log.debug("Injecting {} schemas from {} payload spec descriptions",
                schemasToInject.size(), blendedSchema.size());

        var blending = new BlendingService(openAPI, schemasToInject)
                .modelToAugment(modelToAugment)
                .mode(strict ? MergeSchemasAction.Mode.STRICT : MergeSchemasAction.Mode.FIX)
                .postprocessor(new ComposedPostprocessor());

        configureSecurityDefinitions(blending);

        if (sorted) {
            blending.postprocessor(new SortTypesByName());
        }


        var mapper = SerializationUtils.yamlMapper();

        openAPI = blending.blend();

        if(validateOutput) {
            try {
                var issues = SpecValidator.fromClasspath().validate(openAPI);
                if(issues.isEmpty()) {
                    log.info("Output is compliant with the schema");
                } else {
                    log.warn("There are issues found in OAS schema");
                    issues.forEach(i -> log.warn("Issue: {}", i.getMessage()));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            File output = output();
            log.info("Writing to {}", output);
            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }

    private void configureSecurityDefinitions(BlendingService blending) {
        switch (pathSecurity) {
            case oauth2:
                blending.postprocessor(new SecureEndpointsWithOAuth2(SecureEndpointsWithOAuth2.DEFAULT_SCHEME_NAME,
                        SecureEndpointsWithOAuth2.Mode.PER_OPERATION_SCOPE));

            case oauth2_simple:
                blending.postprocessor(new SecureEndpointsWithOAuth2(SecureEndpointsWithOAuth2.DEFAULT_SCHEME_NAME,
                        SecureEndpointsWithOAuth2.Mode.SINGLE_SCOPE));

        }
    }

    private List<String> findAllProductSpecifications(String allSchemas) {
        return new IdSchemaResolver(allSchemas)
                .findProductSpecifications(Path.of(specificationsRootDir)).stream()
                .map(Path::toString)
                .collect(Collectors.toList());
    }

    public enum PathSecurity {
        oauth2, oauth2_simple, disabled
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




