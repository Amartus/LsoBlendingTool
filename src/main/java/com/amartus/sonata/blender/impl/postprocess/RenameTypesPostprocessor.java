package com.amartus.sonata.blender.impl.postprocess;

import com.amartus.sonata.blender.impl.util.OasUtils;
import com.amartus.sonata.blender.impl.util.OasWrapper;
import com.amartus.sonata.blender.impl.util.Pair;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RenameTypesPostprocessor implements Consumer<OpenAPI> {
    private static final Logger log = LoggerFactory.getLogger(RenameTypesPostprocessor.class);
    public interface NameConverter {
        String convert(String input);
    }
    private Map<String, String> substitutions;
    private final NameConverter converter;
    public RenameTypesPostprocessor(NameConverter converter) {
        this.converter = converter;
    }

    private static final String extensionName = "x-try-renaming-on";

    @Override
    public void accept(OpenAPI openAPI) {

        var schemas = new OasWrapper(openAPI).schemas();
        this.substitutions = schemas.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), toName(e.getValue()).orElse(e.getKey())))
                .filter(p -> ! p.getKey().equals(p.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if(substitutions.isEmpty()) return;

        if(log.isDebugEnabled()) {
            substitutions.forEach((a,b) -> log.debug("Renaming {} to {}", a, b));
        }

        var newNames = new HashSet<>(substitutions.values());
        if(newNames.size() < substitutions.size()) {
            log.warn("Conflicting substitution names. Skipping.");
            return;
        }
        if (schemas.keySet().stream().anyMatch(newNames::contains)) {
            log.warn("Name substitution already defined in the model. Skipping.");
            return;
        }
        schemas.values().forEach(s -> Optional.ofNullable(s.getExtensions()).ifPresent(e -> e.remove(extensionName)));
        renameSchemas(openAPI);
        renameReferences(openAPI);
        cleanupExtensions(openAPI);
    }

    private void cleanupExtensions(OpenAPI openAPI) {
        var schemas = new OasWrapper(openAPI).schemas().values();
        schemas.forEach(s -> {
            Optional.ofNullable(s.getExtensions()).ifPresent(e -> e.remove(extensionName));
        });
    }

    private void renameSchemas(OpenAPI oas) {
        var schemas = new OasWrapper(oas).schemas().entrySet().stream()
                .map(e -> Pair.of(substitutions.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .collect(Collectors.toMap(Pair::first, Pair::second));
        oas.getComponents().setSchemas(schemas);
    }

    protected Schema tryConverting(Schema schema) {
        if(schema instanceof ArraySchema) {
            var items = tryConverting(schema.getItems());
            schema.setItems(items);
        }
        schema.set$ref(convert(schema.get$ref()));
        return schema;
    }

    protected String convert(String ref) {
        return Optional.ofNullable(ref)
                .flatMap(r -> {
                    var name = OasUtils.toSchemaName(ref);
                    return Optional.ofNullable(substitutions.get(name))
                            .map(OasUtils::toSchemRef);
                }).orElse(ref);
    }

    private void renameReferences(OpenAPI openAPI) {
        new RenameReferences().accept(openAPI);
        new RenamePathReferences().accept(openAPI);
    }

    protected Optional<String> toName(Schema schema) {
        var name = Optional.ofNullable(schema.getExtensions())
                .flatMap(e -> Optional.ofNullable(e.get(extensionName)).map(it -> (String)it));
        return  name.flatMap(n -> Optional.ofNullable(converter.convert(n)));
    }

    private class RenameReferences extends PropertyPostProcessor {
        protected Map.Entry<String, Schema> processProperty(String name, Schema property) {
            return Map.entry(name, tryConverting(property));
        }
    }

    private class RenamePathReferences implements Consumer<OpenAPI> {

        @Override
        public void accept(OpenAPI openAPI) {
            Stream<Schema> schemas = toOperations(openAPI).flatMap(o -> {
                var bs = schemas(o.getRequestBody());
                var rs = schemas(o.getResponses());
                return Stream.concat(bs, rs);
            });

            schemas.forEach(RenameTypesPostprocessor.this::tryConverting);
        }

        private Stream<Operation> toOperations(OpenAPI oas) {
            return Optional.ofNullable(oas.getPaths())
                    .map(p -> p.values().stream().flatMap(pi -> pi.readOperations().stream())
                    ).orElse(Stream.empty());
        }

        private Stream<Schema> schemas(ApiResponses resp) {
            return Optional.ofNullable(resp)
                    .map(LinkedHashMap::values).stream()
                    .flatMap(Collection::stream)
                    .flatMap(this::schemas);
        }
        private Stream<Schema> schemas(ApiResponse r) {
            if(r.get$ref() != null) return Stream.empty();
            return schemas(r.getContent());
        }

        private Stream<Schema> schemas(Content c) {
            if (c == null) return Stream.empty();
            return c.values().stream().map(MediaType::getSchema);
        }

        private Stream<Schema> schemas(RequestBody rb) {
            return Optional.ofNullable(rb).stream().flatMap(r -> {
                if(r.get$ref() != null) return Stream.empty();
                return schemas(r.getContent());
            });
        }
    }
}
