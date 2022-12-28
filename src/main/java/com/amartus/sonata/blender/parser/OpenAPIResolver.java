package com.amartus.sonata.blender.parser;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.ResolverCache;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.processors.ComponentsProcessor;
import io.swagger.v3.parser.processors.OperationProcessor;
import io.swagger.v3.parser.processors.PathsProcessor;

import java.util.HashSet;
import java.util.Set;

/**
 * Modified version of {@link io.swagger.v3.parser.OpenAPIResolver}
 */
public class OpenAPIResolver {
    private final OpenAPI openApi;
    private final ResolverCache cache;
    private final ComponentsProcessor componentsProcessor;
    private final PathsProcessor pathProcessor;
    private final OperationProcessor operationsProcessor;
    private final io.swagger.v3.parser.OpenAPIResolver.Settings settings;
    private final Set<String> resolveValidationMessages = new HashSet<>();

    public ResolverCache getCache() {
        return cache;
    }

    public OpenAPIResolver(OpenAPI openApi, ResolverCache cache, io.swagger.v3.parser.OpenAPIResolver.Settings settings) {
        this.openApi = openApi;
        this.settings = settings != null ? settings : new io.swagger.v3.parser.OpenAPIResolver.Settings();
        this.cache = cache;
        componentsProcessor = new ComponentsProcessor(openApi, this.cache);
        pathProcessor = new PathsProcessor(this.cache, openApi,this.settings);
        operationsProcessor = new OperationProcessor(cache, openApi);
    }

    public void resolve(SwaggerParseResult result) {

        OpenAPI resolved = resolve();
        if (resolved == null) {
            return;
        }
        result.setOpenAPI(resolved);
        result.getMessages().addAll(resolveValidationMessages);
    }

    public OpenAPI resolve() {
        if (openApi == null) {
            return null;
        }

        pathProcessor.processPaths();
        componentsProcessor.processComponents();

        if(openApi.getPaths() != null) {
            for(String pathname : openApi.getPaths().keySet()) {
                PathItem pathItem = openApi.getPaths().get(pathname);
                if(pathItem.readOperations() != null) {
                    for(Operation operation : pathItem.readOperations()) {
                        operationsProcessor.processOperation(operation);
                    }
                }
            }
        }
        return openApi;
    }
}
