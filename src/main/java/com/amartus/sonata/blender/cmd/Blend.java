package com.amartus.sonata.blender.cmd;

import com.amartus.sonata.blender.impl.MergeSchemasAction;
import com.amartus.sonata.blender.impl.postprocess.AlignTypeCompositionWithOasTools;
import com.amartus.sonata.blender.impl.postprocess.PropertyCompositionToType;
import com.amartus.sonata.blender.impl.postprocess.PropertyEnumExternalize;
import com.amartus.sonata.blender.impl.postprocess.RemoveSuperflousTypeDeclarations;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.airlift.airline.Command;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Command(name = "blend", description = "Blend Product Specifications into OpenAPI.")
public class Blend extends AbstractCmd implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Blend.class);

    @Override
    public void run() {
        SwaggerParseResult result = new OpenAPIParser().readLocation(this.spec, List.of(), new ParseOptions());
        OpenAPI openAPI = result.getOpenAPI();
        Map<String, Schema> schemasToInject = this.toProductSpecifications();

        log.debug("Injecting {} schemas from {} product spec descriptions",
                schemasToInject.size(), productSpecifications.size());

        new MergeSchemasAction(modelToAugment, strict)
                .schemasToInject(schemasToInject)
                .target(openAPI)
                .execute();

        new RemoveSuperflousTypeDeclarations().accept(openAPI);
        new PropertyCompositionToType().accept(openAPI);
        new PropertyEnumExternalize().accept(openAPI);

        new AlignTypeCompositionWithOasTools().accept(openAPI);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.SPLIT_LINES)
                .enable(YAMLGenerator.Feature.SPLIT_LINES)

        )
                .addMixIn(Object.class, IgnorePropertyMixin.class)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            File output = new File(this.spec + ".modified");
            log.info("Writing to {}", output);

            mapper.writeValue(new FileWriter(output), openAPI);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing file", e);
        }
    }
}

abstract class IgnorePropertyMixin {
    @JsonIgnore
    public abstract Map<String, Object> getExtensions();
}
