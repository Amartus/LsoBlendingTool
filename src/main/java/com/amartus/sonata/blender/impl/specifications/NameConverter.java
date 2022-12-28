package com.amartus.sonata.blender.impl.specifications;

import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.amartus.sonata.blender.impl.util.TextUtils.split;

public interface NameConverter extends Function<String, ProductSpecificationNamingStrategy.NameAndDiscriminator> {

    Logger log = LoggerFactory.getLogger(UrnBasedNamingStrategy.class);

    private static String toName(String type) {
        return split(type, '-').map(WordUtils::capitalize)
                .collect(Collectors.joining(""));
    }

    NameConverter urn = id -> {
        var uri = URI.create(id);

        if ("urn".equals(uri.getScheme())) {
            String[] segments = uri.getRawSchemeSpecificPart().split(":");
            if ("mef".equals(segments[0]) && segments.length == 7) {
                try {
                    var name = toName(segments[4]);
                    return new ProductSpecificationNamingStrategy.NameAndDiscriminator(name, id);
                } catch (NullPointerException | IndexOutOfBoundsException e) {
                    log.info("{} is not MEF urn", id);
                }
            }
        }
        return null;
    };

    private static ProductSpecificationNamingStrategy.NameAndDiscriminator lastSegment(String s) {
        return Optional.ofNullable(s)
                .flatMap(p -> split(p, '/').reduce((acc,b) -> b))
                .map(ProductSpecificationNamingStrategy.NameAndDiscriminator::new)
                .orElse(null);
    }

    NameConverter lastSegment = NameConverter::lastSegment;
}
