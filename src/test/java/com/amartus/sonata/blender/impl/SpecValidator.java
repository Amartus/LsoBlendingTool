package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

public class SpecValidator {

    private final Supplier<JsonSchema> factory;

    public SpecValidator(JsonNode specification) {
        var jsf = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
         factory = () -> jsf.getSchema(specification);
    }
    public Collection<ValidationMessage> validate(JsonNode toValidate) {
        Set<ValidationMessage> validate = factory.get().validate(toValidate);
        return validate;
    }

    public static SpecValidator fromClasspath() throws IOException {
        var spec = SerializationUtils.jsonMapper()
                .readTree(SpecValidator.class.getResourceAsStream("/schemas/3.0/schema.json"));
        return new SpecValidator(spec);
    }
}
