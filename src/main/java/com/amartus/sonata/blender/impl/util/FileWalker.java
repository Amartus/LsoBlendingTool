package com.amartus.sonata.blender.impl.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FileWalker<T> {
    private final Predicate<Path> toAnalize;
    private final Function<Path, T> mapper;

    public FileWalker(Predicate<Path> toAnalize, Function<Path, T> mapper) {
        this.toAnalize = toAnalize;
        this.mapper = mapper;
    }

    public Stream<Pair<Path, T>> walk(Path rootPath) throws IOException {
        return Files.walk(rootPath).filter(toAnalize)
                .map(p -> Pair.of(p, mapper.apply(p)));
    }
}
