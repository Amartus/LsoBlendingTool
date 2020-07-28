/* * Copyright 2020 Amartus
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
 */
package com.amartus.sonata.blender.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIResolver;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ProductSpecReader {
    private final String name;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String KEY = "___to_remove";
    private final String modelToAugment;

    public ProductSpecReader(String modelToAugment, String name) {
        this.name = name;
        this.modelToAugment = modelToAugment;
    }

    public Map<String, Schema> readSchemas() {
        OpenAPI api = new OpenAPI();

        ComposedSchema schema = new ComposedSchema()
                .addAllOfItem(new Schema().$ref("#/components/schemas/" + modelToAugment))
                .addAllOfItem(new Schema().$ref(name));
        api.schema(KEY, schema);

        String productName = getId(name).orElse(toProductName(name));
        Map<String, Schema> resolved = resolve(schema);
        ComposedSchema wrapper = (ComposedSchema) resolved.remove(KEY);
        Schema productCharacteristicsRef = wrapper.getAllOf().get(0);
        Schema specification = resolved.remove(toRefName(wrapper.getAllOf().get(1)));

        resolved.put(productName, new ComposedSchema()
                .addAllOfItem(productCharacteristicsRef)
                .addAllOfItem(specification)
        );

        return resolved;
    }

    private Optional<String> getId(String name) {
        try {
            JsonNode tree = mapper.readTree(Paths.get(name).toFile());
            return Optional.ofNullable(tree.get("$id").textValue());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String toRefName(Schema schema) {
        return schema.get$ref().replace("#/components/schemas/", "");
    }

    private String toProductName(String name) {
        int sepIdx = name.lastIndexOf("/");
        if (sepIdx < 0) {
            sepIdx = name.lastIndexOf("\\");
        }

        return name.substring(sepIdx + 1);
    }

    private Map<String, Schema> resolve(ComposedSchema schema) {
        OpenAPIResolver r = new OpenAPIResolver(new OpenAPI().schema(KEY, schema), Collections.emptyList());
        return r.resolve()
                .getComponents().getSchemas();
    }
}
