package com.amartus.sonata.blender.cmd;

import com.amartus.sonata.blender.impl.ProductSpecReader;
import io.airlift.airline.Option;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractCmd {
    private static final Logger log = LoggerFactory.getLogger(AbstractCmd.class);
    @Option(
            name = {"-p", "--product-spec"},
            title = "product specifications",
            description = "sets of product specification you would like to integrate")
    protected List<String> productSpecifications = new ArrayList<>();

    @Option(name = {"-i", "--input-spec"}, title = "spec file", required = true,
            description = "location of the OpenAPI spec, as URL or file (required)")
    protected String spec;

    @Option(name = {"-m", "--model-name"},
            title = "model to be augmented",
            description = "Model which will be hosting product specific extensions (e.g. Product)"
    )
    protected String modelToAugment = "Product";

    @Option(name = {"-e", "-encoding"},
            title = "files encoding",
            description = "encoding used to read API and product definitions. By default system encoding is used"
    )
    protected String encoding = null;
    @Option(name = {"--strict-mode"},
            title = "strict mode flag",
            description = "Verify that model to be augmented allows for extension (contains discriminator definition)." +
                    "\n If strict-mode is `false` tool will add a discriminator on the fly if possible."
    )
    protected boolean strict = false;

    protected Map<String, Schema> toProductSpecifications() {

        ParseOptions opt = new ParseOptions();
        opt.setResolve(true);
        return productSpecifications.stream()
                .flatMap(file -> new ProductSpecReader(modelToAugment, file).readSchemas().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    if (a.equals(b)) return a;
                    throw new IllegalArgumentException(String.format("Object for the same key does not match %s %s", a, b));
                }));

    }

    protected void validateProductSpecs(List<String> productSpecifications) {
        Optional<String> absolute = productSpecifications.stream()
                .filter(this::isAbsolute)
                .findFirst();
        absolute.ifPresent(p -> {
            String current = Paths.get("").toAbsolutePath().toString();

            log.warn("{} is an absolute current. it should be relative wrt. {}", p, current);
            throw new IllegalArgumentException("All product specifications has to be expressed as relative paths.");
        });

        boolean incorrectFilesExists = productSpecifications.stream()
                .filter(this::notAfile)
                .peek(p -> log.warn("{} is not a file", p))
                .count() > 0;
        if (incorrectFilesExists) {
            throw new IllegalArgumentException("All product specifications has to exist");
        }
    }

    private boolean notAfile(String p) {
        return !Files.isRegularFile(Paths.get(p));
    }

    private boolean isAbsolute(String p) {
        return Paths.get(p).isAbsolute();
    }
}
