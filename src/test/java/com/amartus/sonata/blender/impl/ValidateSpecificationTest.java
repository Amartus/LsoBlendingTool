package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.cmd.Blend;
import com.amartus.sonata.blender.impl.postprocess.SecureEndpointsWithOAuth2;
import com.amartus.sonata.blender.impl.util.SerializationUtils;
import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.builder.CliBuilder;
import com.github.rvesse.airline.help.Help;
import com.google.common.collect.Streams;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidateSpecificationTest {
    private static final Logger log = LoggerFactory.getLogger(ValidateSpecificationTest.class);
    private static final Path source;

    static {
        try {
            //noinspection DataFlowIssue
            source = Paths.get(ValidateSpecificationTest.class.getResource("/oas/test-spec.yaml").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private Path target;

    private static Stream<String> args(Path target) {
        var api = source.toAbsolutePath();
        var search = api.getParent();
        return Stream.of(
                "blend",
                "-i",
                api.toString(),
                "-d",
                search.toString(),
                "--model-name",
                "Placeholder",
                "--all-schemas",
                target.getParent().toAbsolutePath().toString(),
                "-o",
                target.toAbsolutePath().toString()
        );
    }

    @BeforeEach
    public void setup() throws IOException {
        var tempDir = Files.createTempDirectory("oas");
        target = Files.createTempDirectory("oas").resolve("output.yaml");
        tempDir.toFile().deleteOnExit();
    }

    @AfterEach
    public void cleanup() throws IOException {
        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());
        var messages = SpecValidator.fromClasspath().validate(generated);

        //noinspection ResultOfMethodCallIgnored
        target.toFile().delete();

        log.info("Generated OAS\n\n {} \n\n",
                SerializationUtils.yamlMapper().writeValueAsString(generated));

        log(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void smokeTest() throws Exception {
        Stream<String> args = args(target);
        var builder = builder();

        builder.build().parse(args.toArray(String[]::new)).run();

        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());
        assertThatJson(generated)
                .inPath("$.components.schemas.ToInject")
                .isObject()
                .satisfies(s ->
                        assertThatJson(s).node("allOf[1].properties.b.description").isPresent()
                )
                .satisfies(s ->
                        assertThatJson(s).node("allOf[0].$ref").isEqualTo("#/components/schemas/Placeholder")
                );
    }

    @Test
    public void respectTarget() throws Exception {
        Stream<String> args = Streams.concat(args(target), Stream.of("--auto-discover"));
        var builder = builder();

        builder.build().parse(args.toArray(String[]::new)).run();

        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());
        assertThatJson(generated)
                .inPath("$.components.schemas.ToInject")
                .isObject()
                .satisfies(s ->
                        assertThatJson(s).node("allOf[0].$ref").isEqualTo("#/components/schemas/TargetParentName")
                );
    }

    @Test
    public void oauthTestPerPath() throws IOException {
        Stream<String> args = Stream.concat(
                args(target),
                Stream.of("--path-security", Blend.PathSecurity.oauth2.toString()));
        var builder = builder();

        builder.build().parse(args.toArray(String[]::new)).run();

        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());
        assertThatJson(generated)
                .inPath("$.security").isArray().hasSize(1);

        assertSecurityScheme(generated);

        assertThatJson(generated)
                .inPath("$.paths.*.*")
                .isArray()
                .allSatisfy(e ->
                        assertThatJson(e).inPath("security[0]")
                                .isObject()
                                .containsOnlyKeys(SecureEndpointsWithOAuth2.DEFAULT_SCHEME_NAME)
                );
    }

    @Test
    public void oauthTestSimple() throws IOException {
        Stream<String> args = Stream.concat(
                args(target),
                Stream.of("--path-security", Blend.PathSecurity.oauth2_simple.toString()));
        var builder = builder();

        builder.build().parse(args.toArray(String[]::new)).run();

        var generated = SerializationUtils.yamlMapper().readTree(target.toFile());
        assertThatJson(generated)
                .inPath("$.security").isArray().hasSize(1);

        assertSecurityScheme(generated);

        assertThatJson(generated)
                .inPath("$.paths.*.*")
                .isArray()
                .noneSatisfy(e ->
                        assertThatJson(e)
                                .isObject()
                                .containsKey("security")
                );
    }

    private void assertSecurityScheme(Object node) {
        var scheme = assertThatJson(node)
                .inPath("$.components.securitySchemes")
                .node(SecureEndpointsWithOAuth2.DEFAULT_SCHEME_NAME);
        scheme
                .node("flows")
                .isObject()
                .containsOnlyKeys("clientCredentials");
        scheme
                .isObject()
                .contains(entry("type", "oauth2"));
    }

    private void log(Collection<ValidationMessage> msgs) {
        var msg = msgs.stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.joining("\n"));
        if (!msgs.isEmpty()) {
            log.warn("Validaiton issues:\n{}\n", msg);
        }
    }

    @SuppressWarnings("unchecked")
    private CliBuilder<Runnable> builder() {
        return Cli.<Runnable>builder("openapi-generator-wrapper-cli")
                .withDescription(
                        String.format(
                                Locale.ROOT,
                                "OpenAPI generator wrapper CLI (version %s).",
                                "1.0"))
                .withDefaultCommand(Help.class)
                .withCommands(
                        Blend.class,
                        Help.class
                );
    }
}
