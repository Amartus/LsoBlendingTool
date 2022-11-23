package com.amartus.sonata.blender.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DeserializerProvider {
    private static final Logger log = LoggerFactory.getLogger(DeserializerProvider.class);
    static class AmartusDeserializer extends OpenAPIDeserializer {

        @Override
        public Schema getSchema(JsonNode node, String location, ParseResult result) {
            var schema = super.getSchema(node, location, result);
            if (schema.get$ref() != null) {
                var description = Optional.ofNullable(node.get("description"))
                        .map(JsonNode::textValue);

                description.ifPresent(d -> {
                    log.debug("Adding description of a $ref property {}", schema.get$ref());
                    schema.setDescription(d);
                });
            }
            return schema;
        }
    }
    OpenAPIDeserializer deserializer() {
        return new AmartusDeserializer();
    }
}
