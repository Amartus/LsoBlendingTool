package com.amartus.sonata.blender.impl;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlendingService {
    private static final Logger log = LoggerFactory.getLogger(BlendingService.class);
    private final OpenAPI oas;
    private final Map<String, Schema> schemasToInject;
    private final List<Consumer<OpenAPI>> postprocessors = new ArrayList<>();
    private boolean strict;
    private String modelToAugment;
    private MergeSchemasAction.Mode mode;

    /**
     * Input OAS spec that is to be augmented
     *
     * @param oas             -
     * @param schemasToInject
     */
    public BlendingService(OpenAPI oas, Map<String, Schema> schemasToInject) {
        this.oas = oas;
        this.schemasToInject = schemasToInject;
    }

    public BlendingService postprocessor(@Nonnull Consumer<OpenAPI> postprocessor) {
        this.postprocessors.add(postprocessor);
        return this;
    }

    public BlendingService mode(MergeSchemasAction.Mode mode) {
        this.mode = mode;
        return this;
    }

    public BlendingService modelToAugment(String name) {
        this.modelToAugment = name;
        return this;
    }

    public OpenAPI blend() {
        if(modelToAugment == null) {
            throw new IllegalStateException("Name of the model to augment is not set");
        }

        new MergeSchemasAction(modelToAugment, mode)
                .schemasToInject(schemasToInject)
                .target(oas)
                .execute();
        log.debug("Running postprocessors");
        for(var p : postprocessors) {
            p.accept(oas);
        }
        return oas;
    }
}
