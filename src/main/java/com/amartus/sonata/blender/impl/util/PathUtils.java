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

package com.amartus.sonata.blender.impl.util;

import com.google.common.net.UrlEscapers;

import java.net.URI;
import java.nio.file.Path;

public class PathUtils {
    public static String toFileName(String schema) {
        return toUri(schema).getPath();
    }

    public static URI toUri(String schema) {
        try {
            return URI.create(schema);
        } catch (Exception e) {
            var url = UrlEscapers.urlPathSegmentEscaper().escape(schema);
            return URI.create(url);
        }
    }

    public static Path toRelative(Path path) {
        if (path.isAbsolute()) {
            return Path.of(".").toAbsolutePath().relativize(path).normalize();
        }
        return path;
    }
}
