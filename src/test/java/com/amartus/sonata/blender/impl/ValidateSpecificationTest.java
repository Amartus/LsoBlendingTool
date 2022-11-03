package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.cmd.Blend;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.help.Help;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidateSpecificationTest {
    @Test
    public void smokeTest() throws Exception {
        var source = Paths.get(ValidateSpecificationTest.class.getResource("/oas/test-spec.yaml").toURI());
        var target = Files.createTempDirectory("oas").resolve("output.yaml");

        Stream<String> args = Stream.of(
                "blend",
                "-i",
                source.toAbsolutePath().toString(),
                "--model-name",
                "Placeholder",
                "--all-schemas",
                target.getParent().toAbsolutePath().toString(),
                "-o",
                target.toAbsolutePath().toString()
        );

        String version = "1.0";
        var builder =
                Cli.<Runnable>builder("openapi-generator-wrapper-cli")
                        .withDescription(
                                String.format(
                                        Locale.ROOT,
                                        "OpenAPI generator wrapper CLI (version %s).",
                                        version))
                        .withDefaultCommand(Help.class)
                        .withCommands(
                                Blend.class,
                                Help.class
                        );

        builder.build().parse(args.toArray(String[]::new)).run();

        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());

        var messages = SpecValidator.fromClasspath().validate(generated);
        assertTrue(messages.isEmpty());
    }
}
