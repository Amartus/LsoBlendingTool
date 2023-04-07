package com.amartus.sonata.blender.impl.postprocess;

import com.amartus.sonata.blender.impl.util.OasWrapper;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class RemoveSchemaExtensions implements Consumer<OpenAPI> {

    private final Set<String> extensions;

    public RemoveSchemaExtensions(Set<String> extensions) {
        this.extensions = Objects.requireNonNull(extensions);
    }

    @Override
    public void accept(OpenAPI openAPI) {
        new OasWrapper(openAPI).schemas().values()
                .forEach(s -> Optional.ofNullable(s.getExtensions())
                        .ifPresent(it -> extensions.forEach(it::remove)));
    }
}
