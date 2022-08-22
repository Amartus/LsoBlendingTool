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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.*;

import java.util.function.Consumer;

public class SecureEndpointsWithOAuth2 implements Consumer<OpenAPI> {
    @Override
    public void accept(OpenAPI openAPI) {
        var schemeName = "oauth2MEFLSOAPI";

        openAPI.getComponents()
                .addSecuritySchemes(
                        schemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Authorization code flow")
                                .flows(
                                        new OAuthFlows()
                                                .authorizationCode(defaultAuthorizationCode("http://mef.net/example"))
                                )
                );

        openAPI.addSecurityItem(new SecurityRequirement().addList(schemeName, "default"));
    }

    private OAuthFlow defaultAuthorizationCode(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return new OAuthFlow()
                .authorizationUrl(url + "/token")
                .tokenUrl(url + "/refresh")
                .scopes(new Scopes().addString(
                        "default", "default scope "
                ));
    }
}
