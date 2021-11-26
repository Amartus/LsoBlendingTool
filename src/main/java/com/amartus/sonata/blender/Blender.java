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
import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.help.Help;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Blender {
    private static final Logger log = LoggerFactory.getLogger(Blender.class);

    public static void main(String[] args) {
        String version = "1.2";
        var builder =
                Cli.<Runnable>builder("sonata-blending-tool-cli")
                        .withDescription(
                                String.format(
                                        Locale.ROOT,
                                        "Sonata Blending Tool CLI (version %s).",
                                        version))
                        .withDefaultCommand(Help.class)
                        .withCommands(
                                Generate.class,
                                Blend.class,
                                Help.class
                        );

        try {
            if (args.length == 0) {
                builder.build().parse("help").run();
            } else {
                builder.build().parse(args).run();
            }
        } catch (Exception e) {
            log.error("Error:", e);
            System.out.println(e.getMessage());
        }
    }
}
