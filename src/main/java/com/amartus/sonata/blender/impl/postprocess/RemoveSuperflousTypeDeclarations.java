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

package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Remove typ setup from properties that are ref properties
 *
 * @author bartosz.michalik@amartus.com
 */
public class RemoveSuperflousTypeDeclarations extends PropertyPostProcessor {
    protected Predicate<Schema> refProperty = s -> s.get$ref() != null;

    @Override
    protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
        if (refProperty.test(property)) {
            property.setType(null);
        }
        return Map.entry(name, property);
    }
}
