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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

class UrnBasedNamingStrategyTest {

    private ObjectMapper mapper = new ObjectMapper();

    private ProductSpecificationNamingStrategy strategy;

    private static Stream<Arguments> toCheck() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("urn", false),
                Arguments.of("urn:mef:oas:schemas:sonata:AEL", false),
                Arguments.of("urn:mef:oas:schemas:sonata:AEL:poq", false),
                Arguments.of("urn:mef:oas:schemas:sonata:AEL:1.0.0:poq", true)
        );
    }

    @BeforeEach
    public void setup() {
        strategy = new UrnBasedNamingStrategy();
    }

    @ParameterizedTest
    @MethodSource("toCheck")
    void testStrategy(String name, boolean shouldBePresent) {
        var input = toIdNode(name);
        Optional<ProductSpecificationNamingStrategy.NameAndDiscriminator> response = strategy.provideNameAndDiscriminator(null, input);
        Assertions.assertEquals(shouldBePresent, response.isPresent());
    }

    private JsonNode toIdNode(String idValue) {
        if (idValue == null) {
            return null;
        }
        var node = mapper.createObjectNode();

        if (StringUtils.isBlank(idValue)) {
            return node;
        }
        return node.put("$id", idValue);
    }

}