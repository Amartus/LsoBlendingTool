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
package com.amartus.sonata.blender.impl.specifications;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UrnBasedNamingStrategy implements ProductSpecificationNamingStrategy {
    private static final Logger log = LoggerFactory.getLogger(UrnBasedNamingStrategy.class);

    @Override
    public Optional<NameAndDiscriminator> provideNameAndDiscriminator(URI schemaLocation, JsonNode fileContent) {

        return Optional.ofNullable(fileContent)
                .map(fc -> fc.get("$id"))
                .flatMap(i -> Optional.ofNullable(i.textValue()))
                .flatMap(this::convert);
    }

    private Optional<NameAndDiscriminator> convert(String id) {
        var uri = URI.create(id);

        if ("urn".equals(uri.getScheme())) {
            String[] segments = uri.getRawSchemeSpecificPart().split(":");
            if ("mef".equals(segments[0]) && segments.length == 7) {
                try {
                    var name = toName(segments[4]);
                    return Optional.of(new NameAndDiscriminator(name, id));
                } catch (NullPointerException | IndexOutOfBoundsException e) {
                    log.info("{} is not MEF urn", id);
                }
            }

        }
        return Optional.empty();
    }

    private String toName(String type) {
        return split(type, '-').map(WordUtils::capitalize)
                .collect(Collectors.joining(""));
    }

    private Stream<String> split(String word, char... separators) {
        if (separators.length == 0) return Stream.of(word);
        String regex = "[" + new String(separators) + "]";
        return Arrays.stream(word.split(regex));
    }
}
