package io.zell.cwg.cli;

import io.zell.cwg.config.ConfigOverrides;
import java.nio.file.Path;
import picocli.CommandLine.Option;

public final class ConfigCommandMixin {

  @Option(
      names = "--config",
      paramLabel = "FILE",
      description = "Path to a workload YAML config file.")
  Path config;

  @Option(names = "--image", paramLabel = "IMAGE", description = "Camunda runtime image.")
  String image;

  @Option(
      names = "--resources",
      paramLabel = "DIR",
      description = "Directory containing BPMN, DMN, form, and payload resources.")
  Path resourcesDirectory;

  @Option(
      names = "--root-process",
      paramLabel = "ID",
      description = "Root process ID used to start workload instances.")
  String rootProcessId;

  @Option(
      names = "--start-instances",
      paramLabel = "COUNT",
      description = "Number of process instances to start.")
  Integer startInstances;

  @Option(
      names = "--complete-instances",
      paramLabel = "COUNT",
      description = "Number of process instances to drive toward completion.")
  Integer completeInstances;

  @Option(
      names = {"--output", "--output-path"},
      paramLabel = "PATH",
      description = "Directory or zip path for generated artifacts.")
  Path outputPath;

  public Path config() {
    return config;
  }
  public ConfigOverrides overrides() {
    return new ConfigOverrides(
        image,
        resourcesDirectory == null ? null : resourcesDirectory.toString(),
        rootProcessId,
        startInstances,
        completeInstances,
        outputPath == null ? null : outputPath.toString());
  }
}
