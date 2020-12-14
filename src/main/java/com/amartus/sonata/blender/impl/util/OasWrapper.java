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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OasWrapper {
    private final OpenAPI oas;

    public OasWrapper(OpenAPI oas) {
        this.oas = Objects.requireNonNull(oas);
    }


    public Optional<Schema> schema(String name) {
        return Optional.ofNullable(schemas().get(name));

    }

    public Map<String, Schema> schemas() {
        return components()
                .flatMap(c -> Optional.ofNullable(c.getSchemas()))
                .orElse(Map.of());
    }

    public OpenAPI oas() {
        return oas;
    }

    protected Optional<Components> components() {
        return Optional.ofNullable(oas.getComponents());
    }
}
