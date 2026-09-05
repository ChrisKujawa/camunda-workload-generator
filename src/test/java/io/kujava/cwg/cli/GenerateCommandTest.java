/*
 * Copyright 2026 camunda-workload-generator contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kujava.cwg.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.kujava.cwg.generation.GenerationResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class GenerateCommandTest {

  @TempDir private Path tempDir;

  @Test
  void shouldRunGeneratorWithValidatedConfig() throws Exception {
    // given
    final var resources = tempDir.resolve("resources");
    Files.createDirectories(resources);
    Files.writeString(resources.resolve("payload.json"), "{}");
    final var output = tempDir.resolve("output");
    final var out = new StringWriter();
    final var err = new StringWriter();
    final var command =
        new CommandLine(
                new GenerateCommand(
                    config -> {
                      assertThat(config.getResources().payload()).isEqualTo("payload.json");
                      return new GenerationResult(
                          1, output.resolve("manifest.json"), output.resolve("report.json"));
                    }))
            .setOut(new PrintWriter(out))
            .setErr(new PrintWriter(err));

    // when
    final var exitCode =
        command.execute(
            "--resources",
            resources.toString(),
            "--payload",
            "payload.json",
            "--output",
            output.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(err.toString()).isEmpty();
    assertThat(out.toString())
        .contains("Deployed 1 resource(s).")
        .contains("Manifest: " + output.resolve("manifest.json"))
        .contains("Report: " + output.resolve("report.json"));
  }
}
