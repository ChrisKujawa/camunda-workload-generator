package io.zell.cwg.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.zell.cwg.generation.GenerationResult;
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
