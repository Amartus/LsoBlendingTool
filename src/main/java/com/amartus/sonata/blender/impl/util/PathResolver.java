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

package com.amartus.sonata.blender.impl.util;

import com.amartus.sonata.blender.impl.specifications.UrnBasedNamingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PathResolver {
    private static final Logger log = LoggerFactory.getLogger(PathResolver.class);
    private final String root;
    private UrnPredicate urnPredicate;

    public PathResolver(String root) {
        this.root = root;
    }


    public Stream<Pair<Path, String>> toSchemaPaths(Collection<String> paths) {
        return toSchemaPaths(paths.stream());
    }

    public Stream<Pair<Path, String>> toSchemaPaths(Stream<String> paths) {

        final var rootPath = Path.of(root).toAbsolutePath();
        return paths
                .map(this::pathWithFragment)
                .map(p -> Pair.of(toPath(p.first(), rootPath), p.second()));
    }

    public List<String> findAllProductSpecifications(String functionName) {
        var rootPath = Path.of(root);
        var toInclude = getUrnPredicate(functionName);
        try {

            return Files.walk(rootPath)
                    .filter(Files::isRegularFile)
                    .filter(toInclude)
                    .map(p -> rootPath.relativize(p).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read %s", rootPath), e);
        }
    }

    private UrnPredicate getUrnPredicate(String functionName) {
        if (urnPredicate == null) {
            urnPredicate = new UrnPredicate(functionName);
        }
        return urnPredicate;
    }

    private Path toPath(String path, Path root) {
        var p = Path.of(path);
        if (p.isAbsolute()) {
            return p;
        }
        return root.resolve(p);
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
