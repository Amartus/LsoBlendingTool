package com.amartus.sonata.blender.impl;

import com.amartus.sonata.blender.impl.util.OasUtils;
import com.amartus.sonata.blender.impl.util.OasWrapper;
import io.swagger.models.properties.StringProperty;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

class MergeSchemasActionTest {

    private OpenAPI oas;


    @BeforeEach
    public void readOas() {
        var uri = MergeSchemasActionTest.class.getResource("/ref-model/root.yaml");
        assert uri != null;
        oas = OasUtils.readOas(uri.toString());
    }


    @Test
    public void fixModeForDefault() {
        shouldWork(oas, null);
        shouldWork(oas, MergeSchemasAction.Mode.FIX);
    }

    @ParameterizedTest
    @EnumSource(value = MergeSchemasAction.Mode.class, names= {"STRICT", "RELAXED"})
    public void failsForDefault(MergeSchemasAction.Mode mode) {
        var underTest = action("Root", oas, mode);
        Assertions.assertThrows(IllegalStateException.class, underTest::execute);
    }

    @ParameterizedTest
    @EnumSource(value = MergeSchemasAction.Mode.class)
    public void allModesSupport(MergeSchemasAction.Mode mode) {

        new OasWrapper(oas).schema("Root").ifPresent(it ->{
            it.addProperty("@type", new StringSchema());
            it.setDiscriminator(new Discriminator().propertyName("@type"));
        });

        shouldWork(oas, mode);
    }
    @Test
    public void relaxedModeFlat() {

        new OasWrapper(oas).schema("Root").ifPresent(it ->{
            it.addProperty("@type", new StringSchema());

        });

        shouldWork(oas, MergeSchemasAction.Mode.RELAXED);
    }

    @Test
    public void relaxedModeFlatInherited() {

        var toAugmentName = "Child";

        var child = new ComposedSchema()
                .allOf(List.of(new Schema().$ref("#/components/schemas/Root")));

        oas.getComponents().getSchemas().put(toAugmentName, child);

        new OasWrapper(oas).schema("Root").ifPresent(it ->{
            it.addProperty("@type", new StringSchema());
        });

        var underTest = action(toAugmentName, oas, MergeSchemasAction.Mode.RELAXED);
        underTest.execute();

        shouldWork(oas, MergeSchemasAction.Mode.RELAXED);
        var extended = new OasWrapper(oas).schema(toAugmentName).get();

        Assertions.assertNotNull(extended.getDiscriminator());
    }


    void shouldWork(OpenAPI oas, MergeSchemasAction.Mode mode) {
        var underTest = action("Root", oas, mode);
        underTest.execute();
        var root = new OasWrapper(oas).schema("Root").get();
        Assertions.assertNotNull(root.getDiscriminator());
        Assertions.assertNotNull(root.getProperties().get("@type"));
    }

    private MergeSchemasAction action(String modelToAugment, OpenAPI oas, MergeSchemasAction.Mode mode) {
        return new MergeSchemasAction(modelToAugment, mode)
                .schemasToInject(schemas())
                .target(oas);
    }

    private Map<String, Schema> schemas() {
        var schema = new ObjectSchema();
        schema.addExtension(ProductSpecReader.DISCRIMINATOR_VALUE, "some-discriminator");
        return Map.of(
                "foo", schema
        );
    }
}