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
import java.util.Optional;

public class FragmentBasedNamingStrategy implements ProductSpecificationNamingStrategy {
    @Override
    public Optional<NameAndDiscriminator> provideNameAndDiscriminator(URI schemaLocation, JsonNode fileContent) {
        if (schemaLocation == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(schemaLocation.getFragment())
                .map(f -> {
                    var idx = f.lastIndexOf("/");
                    if (idx < 0 || idx == f.length() - 1) {
                        //TODO rethink
                        return f;
                    }
                    return f.substring(idx + 1);
                })
                .map(n -> new NameAndDiscriminator(n, null));
    }
}
