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

import com.amartus.sonata.blender.impl.util.OasWrapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Abstract OAS postprocessor
 */
public abstract class AbstractPostProcessor implements Consumer<OpenAPI> {
    protected OasWrapper api;

    static LinkedHashMap<String, Schema> schemas(OpenAPI api) {
        return Optional.ofNullable(api.getComponents().getSchemas())
                .map(LinkedHashMap::new)
                .orElse(new LinkedHashMap<>());
    }

    @Override
    public void accept(OpenAPI openAPI) {
        this.api = new OasWrapper(openAPI);

        processPaths();
        processSchemas();
    }

    protected void processSchemas() {
        schemas(api.oas())
                .forEach(this::process);
    }

    /**
     * Process datatype schema
     *
     * @param key
     * @param schema
     */
    protected abstract void process(String key, Schema schema);

    protected void processPaths() {

    }
}
