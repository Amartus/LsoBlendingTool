/*
 *
 * Copyright 2022 Amartus
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

package com.amartus.sonata.blender.impl.util;

import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

public class PathResolver {
    private final String root;

    public PathResolver(String root) {
        this.root = root;
    }

    public Stream<Pair<Path, String>> toSchemaPaths(Collection<String> paths) {
        return toSchemaPaths(paths.stream());
    }

    public Stream<Pair<Path, String>> toSchemaPaths(Stream<String> paths) {
        final var rootPath = Path.of(root).toAbsolutePath();
        return paths
                .map(this::pathWithFragment)
                .map(p -> Pair.of(toPath(p.getLeft(), rootPath), p.getRight()));
    }

    private Path toPath(String path, Path root) {
        var p = Path.of(path);
        if (p.isAbsolute()) {
            return p;
        }
        return root.resolve(p);
    }

    protected Pair<String, String> pathWithFragment(String path) {
        var result = path.split("#");
        if (result.length > 2) {
            throw new IllegalArgumentException(path + " is not valid");
        }
        if (result.length == 2) {
            return Pair.of(result[0], "#" + result[1]);
        }
        return Pair.of(result[0], "");
    }
}
