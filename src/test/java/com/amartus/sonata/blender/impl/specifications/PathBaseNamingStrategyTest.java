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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;


class PathBaseNamingStrategyTest {
    ProductSpecificationNamingStrategy strategy;

    private static Stream<Arguments> toCheck() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("xxx", true),
                Arguments.of("/aaa/bbb/xxx", true),
                Arguments.of("aaa/bbb/xxx", true),
                Arguments.of("xxx.json#/abc/def", true),
                Arguments.of("xxx.json#/abc/def/", true),
                Arguments.of("aaa/bbb/xxx.json#/abc/def/", true),
                Arguments.of("file://a/b/c", true),
                Arguments.of("file://a/b/c#xxx", true),
                Arguments.of("http://amartus.com/xxx", true),
                Arguments.of("http://amartus.com#/xxx", true)
        );
    }

    @BeforeEach
    public void setup() {
        strategy = new PathBaseNamingStrategy();
    }

    @ParameterizedTest
    @MethodSource("toCheck")
    void testStrategy(String schemaLocation, boolean shouldBePresent) {
        Optional<ProductSpecificationNamingStrategy.NameAndDiscriminator> response = strategy.provideNameAndDiscriminator(schemaLocation, null);
        Assertions.assertEquals(shouldBePresent, response.isPresent());
    }

}