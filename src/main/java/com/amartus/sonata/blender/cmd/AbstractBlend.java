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

import com.amartus.sonata.blender.impl.ProductSpecReader;
import com.amartus.sonata.blender.impl.util.PathResolver;
import com.amartus.sonata.blender.parser.DeserializerProvider;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.MutuallyExclusiveWith;
import com.github.rvesse.airline.annotations.restrictions.Once;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractBlend {
    private static final Logger log = LoggerFactory.getLogger(AbstractBlend.class);

    @Option(
            name = {"-b", "--blending-schema"},
            title = "specifications to be blend (integrate) in",
            description = "sets of specifications (e.g. specific product or service definitions) you would like to integrate")
    @MutuallyExclusiveWith(tag = "allOrSelective")
    protected List<String> blendedSchema = new ArrayList<>();

    @Option(
            name = {"-d", "--spec-root-dir"},
            title = "root directory for specifications to be blended",
            description = "root directory for specifications.")
    @Once
    protected String specificationsRootDir = ".";

    @Option(name = {"-i", "--input-spec"}, title = "spec file",
            description = "location of the OpenAPI spec, as URL or file (required)")
    @Once
    protected String spec;

    @Option(name = {"-m", "--model-name"},
            title = "model to be augmented",
            description = "Model which will be hosting product specific extensions (e.g. MEFProductConfiguration)"
    )
    protected String modelToAugment = "MEFProductConfiguration";

    @Option(name = {"-e", "-encoding"},
            title = "files encoding",
            description = "encoding used to read API and product definitions. By default system encoding is used"
    )
    protected String encoding = null;
    @Option(name = {"--strict-mode"},
            title = "strict mode flag",
            description = "Verify that model to be augmented allows for extension (contains discriminator definition)." +
                    "\n If strict-mode is `false` tool will add a discriminator on the fly if possible."
    )
    protected boolean strict = false;

    @Option(name = {"-all", "--all-schemas"},
            title = "take all schemas for given function",
            hidden = true,
            description = "Take all schemas from specification root directory for a given function. By convention use only URN with that function or 'all'"
    )
    @MutuallyExclusiveWith(tag = "allOrSelective")
    protected String allSchemas = null;

    @Option(name = {"-discover", "--auto-discover"},
            title = "auto-discover type target name from schema extension",
            description = "Try to use a parent type name from x-mef-target extension in schema. If not defined the fallback is to take 'model-name'"
    )
    protected boolean autodiscover = false;

    @SuppressWarnings("rawtypes")
    protected Map<String, Schema> toProductSpecifications() {
        var config = new ProductSpecReader.Options(modelToAugment, autodiscover);

        var foundSchemas = toSchemaPaths(blendingSchemas())
                .map(schema -> {
                    var resolved = new ProductSpecReader(config,
                            schema.getLeft(), schema.getRight(),
                            new DeserializerProvider(), ProductSpecReader.defaultOptions())
                            .readSchemas();
                    return Pair.of(schema, resolved);
                })
                .collect(Collectors.toList());

        validateResolvedSchemas(foundSchemas);

        var allSchemas = foundSchemas.stream().flatMap(it -> it.getRight().entrySet().stream());

        return allSchemas
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    if (a.equals(b)) return a;
                    throw new IllegalArgumentException(String.format("Object for the same key does not match %s %s", a, b));
                }));
    }

    Function<Pair<Path, String>, String> toPointer = it -> {
        var path = it.getLeft().toAbsolutePath().toString();
        var schema = it.getRight();
        return StringUtils.isBlank(schema) ? path :  path + "#" + schema;
    };

    private void validateResolvedSchemas(List<Pair<Pair<Path, String>, Map<String, Schema<?>>>> foundSchemas) {
        var byKeys = foundSchemas.stream()
                .flatMap(it -> {
                    var key = it.getLeft();
                    return it.getRight().entrySet().stream()
                            .map(e -> Triple.of(e.getKey(), e.getValue(), key));
                }).collect(Collectors.groupingBy(Triple::getLeft));

        List<String> messages = byKeys.entrySet().stream()
                .filter(it -> it.getValue().size() > 1)
                .map(it -> {
                    var groups = it.getValue().stream().collect(Collectors.groupingBy(
                            Triple::getMiddle,
                            Collectors.mapping(t -> toPointer.apply((Pair<Path, String>) t.getRight()), Collectors.toList())
                    ));

                    var strings = groups.values().stream().map(list -> String.join("\n", list)).collect(Collectors.toList());
                    return Pair.of(it.getKey(), strings);
                })
                .peek(this::logReferencingFiles)
                .filter(it -> ((List<String>)it.getValue()).size() > 1)
                .map(it -> {
                    var groupsCount =  it.getValue().size();
                    StringBuilder sb = new StringBuilder();
                    var idx = 1;

                    for(var group : it.getValue()) {
                        sb.append(String.format("Group %d:\n%s\n", idx++, group));
                    }

                    return String.format("Schema %s defined  %d times and referenced from files groups:\n%s",
                            it.getKey(), groupsCount,
                            sb);
                })
                .collect(Collectors.toList());
        if(messages.isEmpty()) return;

        throw new IllegalArgumentException(String.format("Found conflicting schema definitions\n %s", String.join("\n", messages)));
    }

    private void logReferencingFiles(Pair<String, List<String>> it) {
        var files = String.join("\n", ((List<String>) it.getValue()));
        log.debug("Schema {} found in files:\n{}", it.getKey(), files);
    }

    protected Stream<Pair<Path, String>> toSchemaPaths(Stream<String> path) {
        return new PathResolver(specificationsRootDir).toSchemaPaths(path);
    }
    protected Stream<String> blendingSchemas() {
        return blendedSchema.stream();
    }

    protected void validateProductSpecs() {
        var incorrectFilesExists = toSchemaPaths(blendingSchemas())
                .map(Pair::getLeft)
                .filter(p -> !Files.isRegularFile(p))
                .peek(p -> log.warn("{} is not a file", p))
                .count() > 0;
        if (incorrectFilesExists) {
            throw new IllegalArgumentException("All product specifications has to exist");
        }
    }
}
