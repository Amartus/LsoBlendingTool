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

package com.amartus;

import com.amartus.sonata.blender.cmd.Blend;
import com.amartus.sonata.blender.cmd.Generate;
import io.airlift.airline.Cli;
import io.airlift.airline.Help;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlendAllR5 {
    final static Predicate<String> isYaml = f -> f.toLowerCase().endsWith(".yaml");
    final static Predicate<String> isManagement = f -> f.toLowerCase().contains("management");
    final static Function<Path, String> toName = f -> f.getFileName().toString();
    private static final Logger log = LoggerFactory.getLogger(BlendAllR5.class);

    public static void main(String[] args) throws IOException {
        final var mapping = Map.of(
                "productInventoryManagement", "inventory",
                "productOrderManagement", "order",
                "quoteManagement", "quote",
                "productOfferingQualificationManagement", "poq"
        );

        final var groups = mapping.values().stream()
                .map(s -> Map.entry(s, new LinkedList<Path>()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final Function<Path, Optional<String>> fromGroup = p -> {
            var name = toName.apply(p).toLowerCase();
            return groups.keySet().stream()
                    .filter(name::contains)
                    .findFirst();
        };

        final Function<Path, Optional<String>> fromMapping = p -> {
            var name = toName.apply(p);
            return mapping.keySet().stream()
                    .filter(name::contains)
                    .map(mapping::get)
                    .findFirst();
        };

        //has to be relative
        Path sdkRoot = Paths.get("/tmp/MEF-LSO-Sonata-SDK");
        Path api = sdkRoot.resolve("api");
        Path spec = sdkRoot.resolve("spec");
        Files.walk(spec)
                .filter(f -> !Files.isDirectory(f))
                .forEach(f -> {
                    fromGroup.apply(f).ifPresent(group -> groups.get(group).add(f));
                });

        Files.walk(api)
                .filter(f -> !Files.isDirectory(f))
                .filter(f -> toName.andThen(name -> isYaml.and(isManagement).test(name)).apply(f))
                .forEach(f -> {
                    fromMapping.apply(f)
                            .map(groups::get)
                            .ifPresent(specs -> {
                                blend(f, specs);
                            });

                });


    }

    private static void blend(Path mefSpec, LinkedList<Path> specifications) {
        log.info("Blending {}", mefSpec);


        Stream<String> specs = specifications.stream()
                .flatMap(p -> Stream.of("-p", p.toString()));

        Stream<String> productAgnostic = Stream.of(
                "blend",
                "-i",
                mefSpec.toString(),
                "--model-name",
                "MEFProductConfiguration"
        );

        var args = Stream.concat(productAgnostic, specs)
                .toArray(String[]::new);


        String version = "1.0";
        Cli.CliBuilder<Runnable> builder =
                Cli.<Runnable>builder("openapi-generator-wrapper-cli")
                        .withDescription(
                                String.format(
                                        Locale.ROOT,
                                        "OpenAPI generator wrapper CLI (version %s).",
                                        version))
                        .withDefaultCommand(Help.class)
                        .withCommands(
                                Generate.class,
                                Blend.class,
                                Help.class
                        );

        try {
            builder.build().parse(args).run();
        } catch (Exception e) {
            log.error("Error:", e);
            System.out.println(e.getMessage());
        }
    }
}
