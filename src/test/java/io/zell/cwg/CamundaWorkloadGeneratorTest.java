package io.zell.cwg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class CamundaWorkloadGeneratorTest {

  @TempDir private Path tempDir;

  @Test
  void shouldPrintEffectiveConfigWithCliOverrides() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(
        configFile,
        """
        runtime:
          image: camunda/camunda:8.8.1
        resources:
          directory: src/test/resources/workload
          rootProcessId: invoice
          payload: from-config.json
        workload:
          startInstances: 10
          completeInstances: 4
        output:
          path: build/from-config
        """);
    final var out = new StringWriter();
    final var err = new StringWriter();
    final var command = new CommandLine(new CamundaWorkloadGenerator.RootCommand());
    command.setOut(new PrintWriter(out));
    command.setErr(new PrintWriter(err));

    // when
    final var exitCode =
        command.execute(
            "print-config",
            "--config",
            configFile.toString(),
            "--image",
            "camunda/camunda:8.8.2",
            "--resources",
            "models",
            "--root-process",
            "order",
            "--payload",
            "from-cli.json",
            "--start-instances",
            "12",
            "--complete-instances",
            "5",
            "--output",
            "build/from-cli");

    // then
    assertThat(exitCode).isZero();
    assertThat(err.toString()).isEmpty();
    assertThat(out.toString())
        .contains("image: \"camunda/camunda:8.8.2\"")
        .contains("directory: \"models\"")
        .contains("rootProcessId: \"order\"")
        .contains("payload: \"from-cli.json\"")
        .contains("startInstances: 12")
        .contains("completeInstances: 5")
        .contains("path: \"build/from-cli\"");
  }

  @Test
  void shouldValidateConfigWithoutStartingRuntime() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(configFile, "resources:%n  rootProcessId: invoice%n".formatted());
    final var out = new StringWriter();
    final var err = new StringWriter();
    final var command = new CommandLine(new CamundaWorkloadGenerator.RootCommand());
    command.setOut(new PrintWriter(out));
    command.setErr(new PrintWriter(err));

    // when
    final var exitCode = command.execute("validate", "--config", configFile.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(out.toString()).contains("Configuration is valid.");
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void shouldRejectInvalidCounts() throws Exception {
    // given
    final var configFile = tempDir.resolve("workload.yaml");
    Files.writeString(configFile, "workload:%n  startInstances: -1%n".formatted());
    final var err = new StringWriter();
    final var command = new CommandLine(new CamundaWorkloadGenerator.RootCommand());
    command.setErr(new PrintWriter(err));

    // when
    final var exitCode = command.execute("validate", "--config", configFile.toString());

    // then
    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(err.toString()).contains("workload.startInstances must be greater than or equal to 0");
  }

  @Test
  void shouldAnalyzeResourcesFromCli() throws Exception {
    // given
    Files.writeString(
        tempDir.resolve("invoice.bpmn"),
        """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
          <process id="invoice">
            <serviceTask id="charge_card">
              <extensionElements>
                <zeebe:taskDefinition type="charge-card" />
              </extensionElements>
            </serviceTask>
          </process>
        </definitions>
        """);
    Files.writeString(tempDir.resolve("invoice.dmn"), "test");
    Files.writeString(tempDir.resolve("payload.json"), "{}");
    final var out = new StringWriter();
    final var err = new StringWriter();
    final var command = new CommandLine(new CamundaWorkloadGenerator.RootCommand());
    command.setOut(new PrintWriter(out));
    command.setErr(new PrintWriter(err));

    // when
    final var exitCode = command.execute("analyze-resources", "--resources", tempDir.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(err.toString()).isEmpty();
    assertThat(out.toString())
        .contains("BPMN invoice.bpmn")
        .contains("DMN invoice.dmn")
        .contains("JSON payload.json")
        .contains("invoice")
        .contains("charge-card (charge_card)");
  }
}
