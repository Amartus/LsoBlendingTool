/*
 *
 * Copyright 2020 Amartus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.amartus.sonata.blender.impl.postprocess;

import com.amartus.sonata.blender.impl.util.Pair;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SecureEndpointsWithOAuth2 implements Consumer<OpenAPI> {

    private static final String DEFAULT_SCHEME_NAME = "oauth2MEFLSOAPI";
    private final String schemeName;
    private final Mode mode;

    public SecureEndpointsWithOAuth2() {
        this(DEFAULT_SCHEME_NAME, Mode.PER_OPERATION_SCOPE);
    }

    public SecureEndpointsWithOAuth2(String schemeName, Mode mode) {
        this.schemeName = schemeName;
        this.mode = mode;
    }

    @Override
    public void accept(OpenAPI openAPI) {
        SecurityModifier modifier;
        if (mode == Mode.SINGLE_SCOPE) {
            modifier = new DefaultSecurityModifier(openAPI);
        } else {
            modifier = new PerOperationSecurityModifier(openAPI);
        }

        modifier.execute();
    }

    private enum Mode {
        PER_OPERATION_SCOPE,
        SINGLE_SCOPE
    }

    private class DefaultSecurityModifier extends SecurityModifier {

        private DefaultSecurityModifier(OpenAPI oas) {
            super(oas);
        }

        @Override
        protected void addRequirements(List<Pair<Operation, String>> operations) {
            oas.addSecurityItem(
                    new SecurityRequirement()
                            .addList(schemeName, "default")
            );
        }

        @Override
        protected Scopes toScopes(List<Pair<Operation, String>> operations) {
            var scopes = new Scopes();
            scopes.addString("default", "default scope");
            return scopes;
        }
    }

    private class PerOperationSecurityModifier extends SecurityModifier {

        private PerOperationSecurityModifier(OpenAPI oas) {
            super(oas);
        }

        @Override
        protected void addRequirements(List<Pair<Operation, String>> operations) {
            operations.forEach(e -> {
                var operation = e.first();
                operation.addSecurityItem(
                        new SecurityRequirement()
                                .addList(schemeName, e.second())
                );
            });
        }

        @Override
        protected Scopes toScopes(List<Pair<Operation, String>> operations) {
            var scopes = new Scopes();

            operations.stream()
                    .map(Pair::second)
                    .forEach(name -> scopes.addString(name, String.format("Scope for operation %s", name)));
            return scopes;
        }
    }

    private abstract class SecurityModifier {
        protected final OpenAPI oas;

        private SecurityModifier(OpenAPI oas) {
            this.oas = oas;
        }

        public void execute() {
            var operations = oas.getPaths().entrySet().stream()
                    .flatMap(e -> e.getValue().readOperations().stream().map(o -> Pair.of(o, operationId(e.getKey(), o))))
                    .collect(Collectors.toList());

            addSecuritySchema(operations);
            addRequirements(operations);

        }

        protected abstract void addRequirements(List<Pair<Operation, String>> operations);

        protected abstract Scopes toScopes(List<Pair<Operation, String>> operations);

        private String operationId(String path, Operation operation) {
            var id = operation.getOperationId();
            if (id != null) {
                return id;
            }
            return Base64.getEncoder()
                    .encodeToString((operation + path).getBytes(StandardCharsets.UTF_8));
        }

        private void addSecuritySchema(List<Pair<Operation, String>> operations) {

            oas.getComponents()
                    .addSecuritySchemes(
                            schemeName, new SecurityScheme()
                                    .type(SecurityScheme.Type.OAUTH2)
                                    .description("Default m2m client code")
                                    .flows(
                                            new OAuthFlows()
                                                    .clientCredentials(flow("http://mef.net/example", toScopes(operations)))
                                    )
                    );

        }

        private OAuthFlow flow(String url, Scopes scopes) {
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return new OAuthFlow()
                    .authorizationUrl(url + "/token")
                    .tokenUrl(url + "/refresh")
                    .scopes(scopes);
        }

    }


}
