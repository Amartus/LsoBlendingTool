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

import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;
import java.util.function.Consumer;

public class ComposedPostprocessor implements Consumer<OpenAPI> {

    private List<Consumer<OpenAPI>> postprocessors = List.of(
            new RemoveSuperflousTypeDeclarations(),
            new PropertyEnumExternalize(),
            new ComposedPropertyToType(),
            new SingleEnumToDiscriminatorValue(),
            new ConvertOneOfToAllOffInheritance(),
            new UpdateDiscriminatorMapping(),
            new ConstrainDiscriminatorValueWithEnum()
    );


    @Override
    public void accept(OpenAPI openAPI) {
        for (var p : postprocessors) {
            p.accept(openAPI);
        }
    }
}
