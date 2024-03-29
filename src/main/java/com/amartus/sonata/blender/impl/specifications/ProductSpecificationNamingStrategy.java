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

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Discriminator type resolution strategy for product specifications
 */
public interface ProductSpecificationNamingStrategy {
    Optional<NameAndDiscriminator> provideNameAndDiscriminator(URI schemaLocation, JsonNode fileContent);
    Optional<NameAndDiscriminator> fromText(String id);

    class NameAndDiscriminator {
        private final String name;
        private final String discriminatorValue;

        public NameAndDiscriminator(String name) {
            this(name, null);
        }

        public NameAndDiscriminator(String name, String discriminatorValue) {
            this.name = Objects.requireNonNull(name);
            this.discriminatorValue = discriminatorValue;
        }

        public String getDiscriminatorValue() {
            if (discriminatorValue == null) {
                return name;
            }
            return discriminatorValue;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "NameAndDiscriminator{" +
                    "name='" + name + '\'' +
                    ", discriminatorValue='" + discriminatorValue + '\'' +
                    '}';
        }
    }
}
