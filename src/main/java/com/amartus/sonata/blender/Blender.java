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
package com.amartus.sonata.blender;

import com.amartus.sonata.blender.cmd.Blend;
import com.amartus.sonata.blender.cmd.Generate;
import com.amartus.sonata.blender.cmd.Merge;
import com.amartus.sonata.blender.impl.util.TextUtils;
import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.builder.CliBuilder;
import com.github.rvesse.airline.help.Help;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Blender {
    private static final Logger log = LoggerFactory.getLogger(Blender.class);

    public static CliBuilder<Runnable> builder() {
        String version = "1.8";
        return Cli.<Runnable>builder("sonata-blending-tool-cli")
                .withDescription(
                        String.format(
                                Locale.ROOT,
                                "Sonata Blending Tool CLI (version %s).",
                                version))
                .withDefaultCommand(Help.class)
                .withCommands(
                        Generate.class,
                        Blend.class,
                        Merge.class,
                        Help.class
                );
    }

    private static List<String> fromFile(String fileName) throws IOException {
        return TextUtils.splitPhrases(Files.readString(Paths.get(fileName)));
    }

    private static String[] resolveArgs(String... args) {
        return Stream.of(args).flatMap(arg -> {
            if (arg.startsWith("@")) {
                var filename = arg.substring(1);
                try {
                    return fromFile(filename).stream();
                } catch (IOException e) {
                    throw new RuntimeException(String.format(" %s does not exist or cannot be read", Paths.get(filename).toAbsolutePath()), e);
                }
            } else {
                return Stream.of(arg);
            }
        }).toArray(String[]::new);
    }

    public static void main(String... args) {
        var builder = builder();

        try {
            if (args.length == 0) {
                builder.build().parse("help").run();
            } else {
                builder.build().parse(resolveArgs(args)).run();
            }
        } catch (Exception e) {
            log.error("Error:", e);
            System.out.println(e.getMessage());
        }
    }
}
