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
import com.amartus.sonata.blender.impl.util.Pair;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Once;
import com.github.rvesse.airline.annotations.restrictions.RequireOnlyOne;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractCmd {
    private static final Logger log = LoggerFactory.getLogger(AbstractCmd.class);
    @Option(
            name = {"-p", "--product-spec"},
            title = "product specifications",
            description = "sets of product specification you would like to integrate")
    @RequireOnlyOne(tag = "allOrSelective")
    @Deprecated
    protected List<String> productSpecifications = new ArrayList<>();

    @Option(
            name = {"-b", "--blending-schema"},
            title = "specifications to be blend (integrate) in",
            description = "sets of specifications (e.g. specific product or service definitions) you would like to integrate")
    @RequireOnlyOne(tag = "allOrSelective")
    protected List<String> blendedSchema = new ArrayList<>();

    @Option(
            name = {"-d", "--spec-root-dir"},
            title = "root directory for specificatins to be blended",
            description = "sets of product specification root directory for specifications you would like to integrate")
    protected String productsRootDir = null;

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
            title = "take all schemas from repository for blending",
            hidden = true,
            description = "Take all schemas from specification root directory"
    )
    @RequireOnlyOne(tag = "allOrSelective")
    protected boolean allSchemas = false;

    protected Map<String, Schema> toProductSpecifications() {
        return toSchemaPaths(blendingSchemas())
                .flatMap(schema -> new ProductSpecReader(modelToAugment, schema.first(), schema.second()).readSchemas().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    if (a.equals(b)) return a;
                    throw new IllegalArgumentException(String.format("Object for the same key does not match %s %s", a, b));
                }));
    }

    protected Stream<Pair<Path, String>> toSchemaPaths(Stream<String> path) {
        if (productsRootDir == null) {
            return path
                    .map(this::pathWithFragment)
                    .map(p -> Pair.of(Path.of(p.first()), p.second()));
        }

        final var rootPath = Path.of(productsRootDir);
        return path
                .map(this::pathWithFragment)
                .map(p -> Pair.of(rootPath.resolve(p.first()), p.second()));
    }

    protected Pair<String, String> pathWithFragment(String path) {
        var result = path.split("#");
        if (result.length > 2) {
            throw new IllegalArgumentException(path + " is not valid");
        }
        if (result.length == 2) {
            return Pair.of(result[0], "#" + result[1]);
        }
        return Pair.of(result[0], "");
    }

    protected Stream<String> blendingSchemas() {
        return Stream.concat(productSpecifications.stream(), blendedSchema.stream());
    }

    protected void validateProductSpecs(List<String> blendingSchemas) {
        var incorrectFilesExists = toSchemaPaths(blendingSchemas())
                .map(Pair::first)
                .filter(p -> !Files.isRegularFile(p))
                .peek(p -> log.warn("{} is not a file", p))
                .count() > 0;
        if (incorrectFilesExists) {
            throw new IllegalArgumentException("All product specifications has to exist");
        }
    }
}
