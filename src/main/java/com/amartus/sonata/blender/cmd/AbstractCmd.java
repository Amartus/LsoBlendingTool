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
import com.amartus.sonata.blender.impl.util.PathUtils;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
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
            name = {"-d", "--product-spec-root-dir"},
            title = "product specifications root directory",
            description = "sets of product specification root directory for specifications you would like to integrate")
    protected String productsRootDir = ".";

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

    @Option(name = {"-all", "--all-product-schemas"},
            title = "take all product schemas",
            hidden = true,
            description = "Take all schemas from product specification root directory"
    )
    @RequireOnlyOne(tag = "allOrSelective")
    protected boolean allSchemas = false;

    private Function<String, Path> toPath = p -> Path.of(productsRootDir, PathUtils.toFileName(p));
    private Predicate<String> exists = (String p) -> {
        var path = toPath.apply(p);
        log.debug("{} -> {}", p, path);
        return Files.exists(path);
    };

    protected Map<String, Schema> toProductSpecifications() {
        var root = Path.of(productsRootDir);

        return blendingSchemas()
                .flatMap(schema -> new ProductSpecReader(modelToAugment, root, schema).readSchemas().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    if (a.equals(b)) return a;
                    throw new IllegalArgumentException(String.format("Object for the same key does not match %s %s", a, b));
                }));

    }

    protected Stream<String> blendingSchemas() {
        return Stream.concat(productSpecifications.stream(), blendedSchema.stream());
    }

    protected void validateProductSpecs(List<String> productSpecifications) {
        Optional<String> incorrect = productSpecifications.stream()
                .filter(ps -> exists.negate().test(ps))
                .findFirst();
        incorrect.ifPresent(p -> {
            log.warn("{} cannot be found at {}", p, toPath.apply(p));
            throw new IllegalArgumentException("Resource for " + p + " does not exists");
        });

        boolean incorrectFilesExists = productSpecifications.stream()
                .map(toPath)
                .filter(p -> !Files.isRegularFile(p))
                .peek(p -> log.warn("{} is not a file", p))
                .count() > 0;
        if (incorrectFilesExists) {
            throw new IllegalArgumentException("All product specifications has to exist");
        }
    }
}
