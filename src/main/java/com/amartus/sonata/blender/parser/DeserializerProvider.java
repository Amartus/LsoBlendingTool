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
        private Optional<String> getByName(JsonNode node, String name) {
            return Optional.ofNullable(node.get(name))
                    .map(JsonNode::textValue);
        }

        @Override
        public Schema getSchema(JsonNode node, String location, ParseResult result) {
            var schema = super.getSchema(node, location, result);
            if (schema.get$ref() != null) {

                getByName(node, "description").ifPresent(d -> {
                    log.debug("Adding description of a $ref property {}", schema.get$ref());
                    schema.setDescription(d);
                });
            }
            getByName(node, "$id").ifPresent(id -> schema.addExtension("x-try-renaming-on", id));
            return schema;
        }
    }
    public OpenAPIDeserializer deserializer() {
        return new AmartusDeserializer();
    }
}
