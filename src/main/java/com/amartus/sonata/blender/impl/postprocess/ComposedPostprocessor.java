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

import com.amartus.sonata.blender.impl.specifications.FragmentBasedNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.PathBaseNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.ProductSpecificationNamingStrategy;
import com.amartus.sonata.blender.impl.specifications.UrnBasedNamingStrategy;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amartus.sonata.blender.impl.postprocess.RenameTypesPostprocessor.NameConverter;

public class ComposedPostprocessor implements Consumer<OpenAPI> {
    private static final Logger log = LoggerFactory.getLogger(ComposedPostprocessor.class);

    private final List<Consumer<OpenAPI>> postprocessors = List.of(
            new RemoveSuperflousTypeDeclarations(),
            new RenameTypesPostprocessor(converter()),
            new PropertyEnumExternalize(),
            new ComposedPropertyToType(),
            new SingleEnumToDiscriminatorValue(),
            new ConvertOneOfToAllOffInheritance(),
            new UpdateDiscriminatorMapping(),
            new ConstrainDiscriminatorValueWithEnum(),
            new RemoveDefaultParameterValues(),
            new RemoveSchemaExtensions(Set.of(
                    "x-try-renaming-on"
            ))
    );
    @Override
    public void accept(OpenAPI openAPI) {
        log.info("Running {} OAS post-processors", postprocessors.size());
        for (var p : postprocessors) {
            log.debug("Running {}", p.getClass().getSimpleName());
            p.accept(openAPI);
        }
    }
    private NameConverter converter() {
        var converters=  Stream.of(
            new UrnBasedNamingStrategy(),
            new FragmentBasedNamingStrategy(),
            new PathBaseNamingStrategy()
        ).collect(Collectors.toList());

        return input -> converters.stream().flatMap(it -> it.fromText(input).stream()
            .map(ProductSpecificationNamingStrategy.NameAndDiscriminator::getName))
            .findFirst().orElse(null);
    }
}
