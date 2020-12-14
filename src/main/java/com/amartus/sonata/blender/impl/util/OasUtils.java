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

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;

public interface OasUtils {

    static String toSchemaName(String ref) {
        var frag = URI.create(ref).getFragment();
        if (frag == null) return null;

        var segments = frag.split("/");
        return segments[segments.length - 1];
    }

    static String toSchemRef(String name) {
        return "#/components/schemas/" + name;
    }

    static boolean isReferencingSchema(Schema schema) {
        Predicate<List<Schema>> onlyReferences = x -> x != null
                && x.size() > 0
                && x.stream().allMatch(s -> s.get$ref() != null);
        if (schema instanceof ComposedSchema) {
            return
                    onlyReferences.test(((ComposedSchema) schema).getAllOf())
                            || onlyReferences.test(((ComposedSchema) schema).getOneOf());
        }
        return false;
    }


}
