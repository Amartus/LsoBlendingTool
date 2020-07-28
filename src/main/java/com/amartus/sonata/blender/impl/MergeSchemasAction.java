package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.cmd.AbstractCmd;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class MergeSchemasAction {
    protected static final String DISCRIMINATOR_NAME = "@type";
    private static final Logger log = LoggerFactory.getLogger(AbstractCmd.class);
    private final String modelToAugment;
    private final boolean strict;
    private Map<String, Schema> schemasToInject = Map.of();
    private OpenAPI openAPI;

    public MergeSchemasAction(String modelToAugment, boolean strict) {
        this.modelToAugment = modelToAugment;
        this.strict = strict;
    }

    public MergeSchemasAction schemasToInject(Map<String, Schema> schemasToInject) {
        this.schemasToInject = Map.copyOf(schemasToInject);
        return this;
    }

    public MergeSchemasAction target(OpenAPI api) {
        this.openAPI = api;
        return this;

    }

    public void execute() {
        log.debug("Injecting {} schemas",
                schemasToInject.size());

        if (!schemasToInject.isEmpty()) {
            validateTargetExists();
            if (!isTargetReadyForExtension()) {
                if (strict) {
                    log.error("No discriminator defined for {} ", modelToAugment);
                    throw new IllegalStateException("Discriminator not found");
                } else {
                    prepareTargetForExtension();
                }
            }
        }
        this.openAPI.getComponents().getSchemas().putAll(schemasToInject);
    }

    private void validateTargetExists() {
        Schema<?> schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
        if (schema == null) {
            log.error("Schema with name '{}' is not present in the API", modelToAugment);
            throw new IllegalStateException(String.format("Schema '%s' not found in the specification", modelToAugment));
        }
    }

    private void prepareTargetForExtension() {
        Schema<?> schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);

        boolean hasTypeDefined = schema.getProperties().containsKey(DISCRIMINATOR_NAME);
        if (!hasTypeDefined) {
            log.info("Adding field {} to the {}", DISCRIMINATOR_NAME, modelToAugment);
            schema.addProperties(DISCRIMINATOR_NAME,
                    new StringSchema().description("Used as a discriminator to support polymorphic definitions"));
        }
        log.info("Adding discriminator to the {}", modelToAugment);
        schema.setDiscriminator(new Discriminator().propertyName(DISCRIMINATOR_NAME));

    }

    private boolean isTargetReadyForExtension() {
        Schema schema = this.openAPI.getComponents().getSchemas().get(modelToAugment);
        Discriminator discriminator = schema.getDiscriminator();
        return discriminator != null;
    }
}
