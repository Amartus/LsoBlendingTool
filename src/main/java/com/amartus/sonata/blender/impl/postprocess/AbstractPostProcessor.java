package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractPostProcessor implements Consumer<OpenAPI> {
    protected OpenAPI api;

    static LinkedHashMap<String, Schema> schemas(OpenAPI api) {
        return Optional.ofNullable(api.getComponents().getSchemas())
                .map(LinkedHashMap::new)
                .orElse(new LinkedHashMap<>());
    }

    @Override
    public void accept(OpenAPI openAPI) {
        this.api = Objects.requireNonNull(openAPI);

        processPaths();
        processSchemas();
    }

    protected void processSchemas() {
        schemas(api)
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
