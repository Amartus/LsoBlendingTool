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

package com.amartus.sonata.blender.impl.util;

import com.amartus.sonata.blender.impl.specifications.UrnBasedNamingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdSchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(IdSchemaResolver.class);
    private final UrnPredicate urnPredicate;

    public IdSchemaResolver(String functionName) {
        this.urnPredicate = new UrnPredicate(disc -> disc.endsWith("all") || disc.endsWith(functionName));
    }

    public IdSchemaResolver(Predicate<String> discrMatcher) {
        this.urnPredicate = new UrnPredicate(Objects.requireNonNull(discrMatcher));
    }

    public List<Path> findProductSpecifications(Path rootPath) {
        var walker = new FileWalker<>(Files::isRegularFile, urnPredicate::test);

        try (Stream<Pair<Path, Boolean>> allFiles = walker.walk(rootPath)) {
            return allFiles
                    .filter(Pair::getRight)
                    .map(Pair::getLeft)
                    .map(Path::toAbsolutePath)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read %s", rootPath), e);
        }
    }


    static class UrnPredicate implements Predicate<Path> {
        private final ObjectMapper json;
        private final ObjectMapper yaml;
        private final UrnBasedNamingStrategy namingStrategy;

        private final Predicate<String> discriminatorMatcher;

        private UrnPredicate(Predicate<String> discriminatorMatcher) {
            this.json = SerializationUtils.jsonMapper();
            this.yaml = SerializationUtils.yamlMapper();
            this.namingStrategy = new UrnBasedNamingStrategy();
            this.discriminatorMatcher = discriminatorMatcher;
        }


        @Override
        public boolean test(Path path) {
            var content = read(path);
            if (content == null) {
                log.debug("Not in json or yaml format: {}", path.toAbsolutePath().normalize());
                return false;
            }
            var toInclude = namingStrategy.provideNameAndDiscriminator(null, content)
                    .map(n -> {
                        String disc = n.getDiscriminatorValue();
                        return discriminatorMatcher.test(disc);
                    })
                    .orElse(false);
            if (!toInclude) {
                log.info("{} is has no recognized ID. skipping", path);
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
