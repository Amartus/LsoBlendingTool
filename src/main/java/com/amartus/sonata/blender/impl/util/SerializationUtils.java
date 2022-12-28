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

import com.amartus.sonata.blender.impl.yaml.YamlMapperFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.serializer.OpenAPISerializer;

public abstract class SerializationUtils {

    public static ObjectMapper yamlMapper() {
        return enhance(new YamlMapperFactory().createYaml());
    }

    public static ObjectMapper jsonMapper() {
        return enhance(Json31.mapper());
    }

    private static ObjectMapper enhance(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule("OpenAPIModule");
        module.addSerializer(OpenAPI.class, new OpenAPISerializer());
        mapper.registerModule(module)
                .addMixIn(Object.class, IgnorePropertyMixin.class)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return mapper;
    }

    private abstract class IgnorePropertyMixin {
        @JsonIgnore
        public abstract boolean getExampleSetFlag();
    }
}
