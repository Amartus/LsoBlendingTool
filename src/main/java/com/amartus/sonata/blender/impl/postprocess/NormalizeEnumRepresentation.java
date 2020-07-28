package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.media.Schema;

public class NormalizeEnumRepresentation extends AbstractPostProcessor {
    @Override
    protected void process(String key, Schema schema) {
        if (schema.getEnum() != null) {
            schema.type("string");
        }
    }
}
