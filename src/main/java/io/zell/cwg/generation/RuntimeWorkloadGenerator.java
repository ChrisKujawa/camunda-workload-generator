package io.zell.cwg.generation;

import io.zell.cwg.artifacts.ManifestWriter;
import io.zell.cwg.artifacts.ReportWriter;
import io.zell.cwg.artifacts.WorkloadManifest;
import io.zell.cwg.artifacts.WorkloadReport;
import io.zell.cwg.artifacts.WorkloadReport.RunSummary;
import io.zell.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.deployment.ResourceDeployment;
import io.zell.cwg.deployment.ZeebeResourceDeployment;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import io.zell.cwg.resources.WorkloadResourceAnalyzer;
import io.zell.cwg.runtime.CamundaRuntimeFactory;
import io.zell.cwg.runtime.ManagedCamundaRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;

public final class RuntimeWorkloadGenerator implements WorkloadGenerator {

  private final WorkloadResourceAnalyzer resourceAnalyzer;
  private final CamundaRuntimeFactory runtimeFactory;
  private final ResourceDeployment resourceDeployment;
  private final ManifestWriter manifestWriter;
  private final ReportWriter reportWriter;
  private final Clock clock;

  public RuntimeWorkloadGenerator() {
    this(
        new WorkloadResourceAnalyzer(),
        ManagedCamundaRuntime::from,
        new ZeebeResourceDeployment(),
        new ManifestWriter(),
        new ReportWriter(),
        Clock.systemUTC());
  }

  RuntimeWorkloadGenerator(
      final WorkloadResourceAnalyzer resourceAnalyzer,
      final CamundaRuntimeFactory runtimeFactory,
      final ResourceDeployment resourceDeployment,
      final ManifestWriter manifestWriter,
      final ReportWriter reportWriter,
      final Clock clock) {
    this.resourceAnalyzer = resourceAnalyzer;
    this.runtimeFactory = runtimeFactory;
    this.resourceDeployment = resourceDeployment;
    this.manifestWriter = manifestWriter;
    this.reportWriter = reportWriter;
    this.clock = clock;
  }

  @Override
  public GenerationResult generate(final WorkloadConfig config) throws IOException {
    final var resourcesDirectory = Path.of(config.getResources().directory());
    final var resourceAnalysis = resourceAnalyzer.analyze(resourcesDirectory);
    if (resourceAnalysis.scan().deployableResources().isEmpty()) {
      throw new ConfigException(
          "No deployable BPMN, DMN, or form resources found in %s".formatted(resourcesDirectory));
    }
    final var outputDirectory = Path.of(config.getOutput().path());

    final int deployedResources;
    try (final var runtime = runtimeFactory.create(config.getRuntime())) {
      runtime.start();
      final var deployment =
          resourceDeployment.deploy(
              runtime.gatewayAddress(), resourceAnalysis.scan().deployableResources());
      deployedResources = deployment.deployedResourceCount();
    }

    final var generatedAt = Instant.now(clock).toString();
    final var manifestPath =
        manifestWriter.write(
            outputDirectory, WorkloadManifest.from(config, resourceAnalysis, generatedAt));
    final var reportPath =
        reportWriter.write(outputDirectory, reportFrom(resourceAnalysis, generatedAt));

    return new GenerationResult(deployedResources, manifestPath, reportPath);
  }

  private static WorkloadReport reportFrom(
      final WorkloadResourceAnalysis resourceAnalysis, final String generatedAt) {
    return new WorkloadReport(
        "1",
        generatedAt,
        new RunSummary(0, 0, 0, 0),
        resourceAnalysis.staticJobTypes().stream().map(jobType -> jobType.type()).distinct().toList(),
        new LinkedHashMap<>(),
        SecondaryStorageReport.skipped());
  }
}
