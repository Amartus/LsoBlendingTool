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
import com.amartus.sonata.blender.impl.postprocess.UpdateDiscriminatorMapping;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Once;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.GeneratorNotFoundException;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author bartosz.michalik@amartus.com
 */
@Command(name = "generate", description = "Generate code using configuration.")
public class Generate extends AbstractCmd implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Generate.class);
    @Option(
            name = {"-c", "--config"},
            title = "configuration file",
            description = "Path to configuration file configuration file. It can be json or yaml."
                    + "If file is json, the content should have the format {\"optionKey\":\"optionValue\", \"optionKey1\":\"optionValue1\"...}."
                    + "If file is yaml, the content should have the format optionKey: optionValue"
                    + "Supported options can be different for each language. Run config-help -g {generator name} command for language specific config options.")
    @Once
    private String configFile;


    @Override
    public void run() {
        CodegenConfigurator configurator = CodegenConfigurator.fromFile(configFile);
        if (configurator == null) {
            throw new IllegalStateException("Cannot use generator configuration from: " + configFile);
        }
        log.debug("Will generate artifacts using '{}' configuration", configFile);


        validateProductSpecs(productSpecifications);

        Map<String, Schema> productSchemas = toProductSpecifications();

        if (StringUtils.isNotBlank(spec)) {
            configurator.setInputSpec(spec);
        }

        try {
            final ClientOptInput clientOptInput = configurator.toClientOptInput();
            new AmartusGenerator(productSchemas).opts(clientOptInput).generate();
        } catch (GeneratorNotFoundException e) {
            log.error("Error in wrapper configuration", e);
            System.exit(1);
        } catch (IllegalStateException e) {
            log.error("Error in wrapper configuration", e);
            System.exit(2);
        }
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

            new MergeSchemasAction(modelToAugment, strict)
                    .schemasToInject(schemasToInject)
                    .target(this.openAPI)
                    .execute();

            new RemoveSuperflousTypeDeclarations().accept(openAPI);
            new PropertyEnumExternalize().accept(openAPI);
            new ComposedPropertyToType().accept(openAPI);
            new SingleEnumToDiscriminatorValue().accept(openAPI);
            new ConvertOneOfToAllOffInheritance().accept(openAPI);
            new UpdateDiscriminatorMapping().accept(openAPI);

//            new AlignTypeCompositionWithOasTools().accept(openAPI);

            return super.generate();
        }


    }
}
