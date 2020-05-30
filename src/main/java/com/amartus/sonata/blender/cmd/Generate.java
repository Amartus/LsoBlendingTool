/*
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
 */
package com.amartus.sonata.blender.cmd;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.GeneratorNotFoundException;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
@Command(name = "generate", description = "Generate code using configuration.")
public class Generate implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Generate.class);
    @Option(
            name = {"-c", "--config"},
            required = true,
            title = "configuration file",
            description = "Path to configuration file configuration file. It can be json or yaml."
                    + "If file is json, the content should have the format {\"optionKey\":\"optionValue\", \"optionKey1\":\"optionValue1\"...}."
                    + "If file is yaml, the content should have the format optionKey: optionValue"
                    + "Supported options can be different for each language. Run config-help -g {generator name} command for language specific config options.")
    private String configFile;

    @Option(
            name = {"-p", "--product-spec"},
            title = "product specifications",
            description = "sets of product specification you would like to integrate")
    private List<String> productSpecifications = new ArrayList<>();

    @Option(name = {"-i", "--input-spec"}, title = "spec file", required = true,
            description = "location of the OpenAPI spec, as URL or file (required)")
    private String spec;

    @Option(name = {"-m", "--model-name"},
            title = "model to be augmented",
            description = "Model which will be hosting product specific extensions (E.g. ProductCharacteristics)"
    )
    private String modelToAugment = "ProductCharacteristics";

    @Option(name = {"-e", "-encoding"},
            title = "files encoding",
            description = "encoding used to read API and product definitions. By default system encoding is used"
    )
    private String encoding = null;
    @Option(name= {"--strict-mode"},
            title = "Verify that model to be augmented allows for extension"
    )
    private boolean strict = false;

    private static final String DISCRIMINATOR_NAME = "@type";

    @Override
    public void run() {
        CodegenConfigurator configurator = CodegenConfigurator.fromFile(configFile);
        if(configurator == null) {
            throw new IllegalStateException("Cannot use generator configuration from: " + configFile);
        }
        log.debug("Will generate artifacts using '{}' configuration", configFile);


        validateProductSpecs(productSpecifications);

        Map<String, Schema> productSchemas = toProductSpecifications();

        if(StringUtils.isNotBlank(spec)) {
            configurator.setInputSpec(spec);
        }

        try {
            final ClientOptInput clientOptInput = configurator.toClientOptInput();
            new AmartusGenerator(productSchemas).opts(clientOptInput).generate();
        } catch (GeneratorNotFoundException e) {
            log.error("Error in wrapper configuration", e);
            System.exit(1);
        } catch(IllegalStateException e) {
            log.error("Error in wrapper configuration", e);
            System.exit(2);
        }
    }

    private void validateProductSpecs(List<String> productSpecifications) {
        Optional<String> absolute = productSpecifications.stream()
                .filter(this::isAbsolute)
                .findFirst();
        absolute.ifPresent(p -> {
            String current = Paths.get("").toAbsolutePath().toString();

            log.warn("{} is an absolute current. it should be relative wrt. {}", p, current);
            throw new IllegalArgumentException("All product specifications has to be expressed as relative paths.");
        });

        boolean incorrectFilesExists = productSpecifications.stream()
                .filter(this::notAfile)
                .peek(p -> log.warn("{} is not a file", p))
                .count() > 0;
        if(incorrectFilesExists) {
            throw new IllegalArgumentException("All product specifications has to exist");
        }
    }

    private boolean notAfile(String p) {
        return ! Files.isRegularFile(Paths.get(p));
    }

    private boolean isAbsolute(String p) {
        return Paths.get(p).isAbsolute();
    }

    private Map<String, Schema> toProductSpecifications() {

        ParseOptions opt = new ParseOptions();
        opt.setResolve(true);
        return productSpecifications.stream()
                .flatMap(file -> new ProductSpecReader(modelToAugment, file).readSchemas().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    private class AmartusGenerator extends DefaultGenerator {
        private final Map<String, Schema> schemasToInject;

        public AmartusGenerator(Map<String, Schema> schemasToInject) {
            this.schemasToInject = schemasToInject;
        }

        @Override
        public List<File> generate() {
            log.debug("Injecting {} schemas from {} product spec descriptions",
                    schemasToInject.size(), productSpecifications.size());

            if(! schemasToInject.isEmpty()) {
                validateTargetExists();
                if(! isTargetReadyForExtension()) {
                    if(strict) {
                        log.error("No discriminator defined for {} ", modelToAugment);
                        throw new IllegalStateException("Discriminator not found");
                    } else {
                        prepareTargetForExtension();
                    }
                }
            }
            this.openAPI.getComponents().getSchemas().putAll(schemasToInject);
            return super.generate();
        }

        private void validateTargetExists() {
            Schema<?> schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
            if(schema == null) {
                log.error("Schema with name '{}' is not present in the API spec {}", modelToAugment, spec);
                throw new IllegalStateException(String.format("Schema '%s' not found in the specification", modelToAugment));
            }
        }

        private void prepareTargetForExtension() {
            Schema<?> schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);

            boolean hasTypeDefined = schema.getProperties().containsKey(DISCRIMINATOR_NAME);
            if(!hasTypeDefined) {
                log.info("Adding field {} to the {}", DISCRIMINATOR_NAME, modelToAugment);
                schema.addProperties(DISCRIMINATOR_NAME,
                        new StringSchema().description("Used as a discriminator to support polymorphic definitions"));
            }
            log.info("Adding discriminator to the {}", modelToAugment);
            schema.setDiscriminator(new Discriminator().propertyName(DISCRIMINATOR_NAME));

        }

        private boolean isTargetReadyForExtension() {
            Schema schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
            Discriminator discriminator = schema.getDiscriminator();
            return discriminator != null;
        }
    }
}
