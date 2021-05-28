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

package com.amartus.sonata.blender.impl;

import com.github.jknack.handlebars.internal.RefParam;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

public class CustomizedSwaggerConverter extends SwaggerConverterClone {

    @Override
    public Parameter convert(io.swagger.models.parameters.Parameter v2Parameter) {
        var param = super.convert(v2Parameter);

        if (v2Parameter instanceof RefParam) {
            var desc = v2Parameter.getDescription();
            param.setDescription(desc);
        }

        return param;
    }

    @Override
    protected Schema convert(Property schema) {
        var property = super.convert(schema);
        if (schema instanceof RefProperty) {
            property.setDescription(schema.getDescription());
        }
        return property;
    }
}
