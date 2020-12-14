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

import com.amartus.sonata.blender.impl.util.OasUtils;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class UpdateDiscriminatorMapping extends AbstractPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(UpdateDiscriminatorMapping.class);

    @Override
    protected void process(String name, Schema schema) {
        if (schema instanceof ComposedSchema) {
            var cs = (ComposedSchema) schema;
            if (cs.getAllOf() != null && cs.getAllOf().size() == 2) {
                var parent = cs.getAllOf().get(0);
                if (parent.get$ref() != null) {
                    var discValue = discrininatorValue(schema).orElse(name);
                    log.info("Updating {} discriminator mapping for {} with value '{}'", parent.get$ref(), name, discValue);
                    updateMapping(name, discValue, toSchema(parent.get$ref()));
                }
            }
        }
    }

    private Schema toSchema(String ref) {
        var name = OasUtils.toSchemaName(ref);
        return api.schema(name).get();
    }

    private void updateMapping(String schemaName, String discriminator, Schema parent) {
        var disc = parent.getDiscriminator();
        if (disc != null && disc.getPropertyName() != null) {
            disc.mapping(discriminator, OasUtils.toSchemRef(schemaName));
        }
    }

    private Optional<String> discrininatorValue(Schema schema) {
        return Optional.ofNullable(schema.getExtensions())
                .flatMap(e -> Optional.ofNullable((String) e.get("x-discriminator-value")));
    }
}
