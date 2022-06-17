/*
 *
 * Copyright 2022 Amartus
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
import com.amartus.sonata.blender.impl.ProductSpecReader;
import com.amartus.sonata.blender.impl.postprocess.ComposedPostprocessor;
import com.amartus.sonata.blender.impl.postprocess.SortTypesByName;
import com.amartus.sonata.blender.impl.util.PathResolver;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Once;
import com.github.rvesse.airline.annotations.restrictions.RequireOnlyOne;
import com.github.rvesse.airline.annotations.restrictions.Required;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "merge", description = "Merge Product / Service Specifications into simple OAS that contains only schemas section.")
public class Merge implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Merge.class);
    @Option(
            name = {"-b", "--blending-schema"},
            title = "specifications to be blend (integrate) in",
            description = "sets of specifications (e.g. specific product or service definitions) you would like to integrate")
    @RequireOnlyOne(tag = "allOrSelective")
    protected List<String> blendedSchema = new ArrayList<>();

    @Option(
            name = {"-d", "--spec-root-dir"},
            title = "root directory for specifications")
    @Once
    protected String schemasRoot = ".";

    @Option(name = {"-m", "--model-name"},
            title = "model to be augmented",
            description = "Model which will be hosting product specific extensions (e.g. MEFProductConfiguration)"
    )
    protected String modelToAugment = "MEFProductConfiguration";

    @Option(name = {"-all", "--all-schemas"},
            title = "take all schemas for given function",
            hidden = true,
            description = "Take all schemas from specification root directory for a given function. By convention use only URN with that function or 'all'"
    )
    @RequireOnlyOne(tag = "allOrSelective")
    protected String allSchemas = null;

    @Option(
            name = {"-o", "--output"},
            title = "Output file name",

            description = "Output file name. Throws exception if file exists."
    )
    @Required
    @Once
    private String outputFile;

    @Option(
            name = {"-f", "--force-override"},
            title = "Override output if exist"
    )
    @Once
    private boolean forceWrite = false;

    @Option(
            name = {"--sorted"},
            title = "sort data types",
            description = "sort data types in a lexical order")
    @Once
    private boolean sorted = false;

    @Override
    public void run() {
        OpenAPI openAPI = prepareOas();

        new MergeSchemasAction(modelToAugment, false)
                .schemasToInject(toProductSpecifications())
                .target(openAPI)
                .execute();

        new ComposedPostprocessor().accept(openAPI);
        if (sorted) {
            new SortTypesByName().accept(openAPI);
        }

        var mapper = SerializationUtils.yamlMapper();

        try {
            File output = output();
            log.info("Writing to {}", output.toPath().toAbsolutePath());
            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }

    private OpenAPI prepareOas() {
        var openAPI = new OpenAPI().components(
                new Components().schemas(new LinkedHashMap<>())
        );

        openAPI.getComponents()
                .addSchemas(modelToAugment, new Schema());
        return openAPI;
    }

    private File output() {
        var fileName = Path.of(outputFile);
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

    protected Map<String, Schema> toProductSpecifications() {
        var resolver = new PathResolver(schemasRoot);
        if (allSchemas != null) {
            blendedSchema = resolver.findAllProductSpecifications(allSchemas);
        }

        var paths = resolver.toSchemaPaths(blendedSchema.stream());

        return paths
                .flatMap(schema -> new ProductSpecReader(modelToAugment, schema.first(), schema.second()).readSchemas().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    if (a.equals(b)) return a;
                    throw new IllegalArgumentException(String.format("Object for the same key does not match %s %s", a, b));
                }));
    }
}
