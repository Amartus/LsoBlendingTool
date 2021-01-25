/*
 *
 * Copyright 2021 Amartus
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class SortTypesByName implements Consumer<OpenAPI> {
    private static final Logger log = LoggerFactory.getLogger(SortTypesByName.class);

    @Override
    public void accept(OpenAPI openAPI) {
        var schemas = new OasWrapper(openAPI).schemas();
        if (schemas.isEmpty()) {
            log.info("No types to be sorted");
        }
        log.debug("Sorting schemas");
        var sorted = schemas.entrySet().stream()
                .sorted(this::compare)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        openAPI.getComponents().setSchemas(sorted);
    }

    private int compare(Map.Entry<String, Schema> a, Map.Entry<String, Schema> b) {
        var aK = a.getKey();
        var bK = b.getKey();
        return aK.compareTo(b.getKey());
    }
}
