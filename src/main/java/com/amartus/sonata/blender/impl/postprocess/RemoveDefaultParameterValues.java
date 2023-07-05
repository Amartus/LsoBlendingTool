package com.amartus.sonata.blender.impl.postprocess;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class RemoveDefaultParameterValues implements Consumer<OpenAPI> {


    @Override
    public void accept(OpenAPI api) {
        nullSafe(api.getPaths()).forEach(this::process);
        nullSafe(Optional
                .ofNullable(api.getComponents())
                .map(Components::getPathItems).orElse(null)
        ).forEach(this::process);
        Optional.ofNullable(api.getComponents())
                .stream().flatMap(it -> nullSafe(it.getParameters()).entrySet().stream())
                .forEach(it -> process(it.getValue()));

    }

    private <T,V>  Map<T, V> nullSafe(Map<T, V> v) {
        if(v == null) return Map.of();
        return v;
    }

    private <T> List<T> nullSafe(List<T> v) {
        return Optional.ofNullable(v).orElse(List.of());
    }
    protected void process(String name, PathItem item) {
        nullSafe(item.readOperations())
            .forEach(this::process);
    }

    private void process(Operation operation) {
        nullSafe(operation.getParameters()).forEach(this::process);
        nullSafe(operation.getResponses()).values().stream()
                .flatMap(it -> nullSafe(it.getHeaders()).values().stream())
                .forEach(this::process);
    }

    private final Function<Parameter, Parameter.StyleEnum> defaultFormat = par -> {
        if(par instanceof QueryParameter || par instanceof CookieParameter) {
            return Parameter.StyleEnum.FORM;
        }
        return Parameter.StyleEnum.SIMPLE;
    };

    private void process(Header header) {
        if(header == null) {
            return;
        }
        if(header.getStyle() == Header.StyleEnum.SIMPLE) {
            if(header.getExplode() == Boolean.FALSE) {
                header.setExplode(null);
            }
            header.setStyle(null);
        }
    }

    private void process(Parameter parameter) {
        var defStyle = defaultFormat.apply(parameter);

        if(defaultExplode(defStyle, parameter.getExplode())) {
            parameter.setExplode(null);
        }

        if(parameter.getStyle() == defStyle) {
            parameter.setStyle(null);
        }
     }

    private boolean defaultExplode(Parameter.StyleEnum defStyle, Boolean explode) {
        return Boolean.valueOf(defStyle == Parameter.StyleEnum.FORM) == explode;
    }
}
