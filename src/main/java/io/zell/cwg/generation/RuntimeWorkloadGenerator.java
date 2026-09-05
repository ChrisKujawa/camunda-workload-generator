package io.zell.cwg.generation;

import io.zell.cwg.artifacts.ManifestWriter;
import io.zell.cwg.artifacts.ReportWriter;
import io.zell.cwg.artifacts.WorkloadManifest.ArtifactPaths;
import io.zell.cwg.artifacts.WorkloadManifest;
import io.zell.cwg.artifacts.WorkloadReport;
import io.zell.cwg.artifacts.WorkloadReport.RunSummary;
import io.zell.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import io.zell.cwg.artifacts.WorkloadReport.ZeebeDataReport;
import io.zell.cwg.artifacts.ZeebeDataArtifacts;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.deployment.ResourceDeployment;
import io.zell.cwg.deployment.ZeebeResourceDeployment;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import io.zell.cwg.resources.WorkloadResourceAnalyzer;
import io.zell.cwg.runtime.CamundaRuntimeFactory;
import io.zell.cwg.runtime.ManagedCamundaRuntime;
import io.zell.cwg.runtime.ZeebeDataArtifactSource;
import io.zell.cwg.workload.PayloadVariablesLoader;
import io.zell.cwg.workload.WorkloadExecution;
import io.zell.cwg.workload.WorkloadExecutor;
import io.zell.cwg.workload.ZeebeWorkloadExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

public final class RuntimeWorkloadGenerator implements WorkloadGenerator {

  private final WorkloadResourceAnalyzer resourceAnalyzer;
  private final CamundaRuntimeFactory runtimeFactory;
  private final ResourceDeployment resourceDeployment;
  private final WorkloadExecutor workloadExecutor;
  private final PayloadVariablesLoader payloadVariablesLoader;
  private final ManifestWriter manifestWriter;
  private final ReportWriter reportWriter;
  private final Clock clock;

  public RuntimeWorkloadGenerator() {
    this(
        new WorkloadResourceAnalyzer(),
        ManagedCamundaRuntime::from,
        new ZeebeResourceDeployment(),
        new ZeebeWorkloadExecutor(),
        new PayloadVariablesLoader(),
        new ManifestWriter(),
        new ReportWriter(),
        Clock.systemUTC());
  }

  RuntimeWorkloadGenerator(
      final WorkloadResourceAnalyzer resourceAnalyzer,
      final CamundaRuntimeFactory runtimeFactory,
      final ResourceDeployment resourceDeployment,
      final WorkloadExecutor workloadExecutor,
      final PayloadVariablesLoader payloadVariablesLoader,
      final ManifestWriter manifestWriter,
      final ReportWriter reportWriter,
      final Clock clock) {
    this.resourceAnalyzer = resourceAnalyzer;
    this.runtimeFactory = runtimeFactory;
    this.resourceDeployment = resourceDeployment;
    this.workloadExecutor = workloadExecutor;
    this.payloadVariablesLoader = payloadVariablesLoader;
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
    final var payloadVariables = payloadVariablesLoader.load(config);
    final var outputDirectory = Path.of(config.getOutput().path());

    final int deployedResources;
    final WorkloadExecution workloadExecution;
    final ZeebeDataArtifacts zeebeDataArtifacts;
    try (final var runtime = runtimeFactory.create(config.getRuntime())) {
      runtime.start();
      final var deployment =
          resourceDeployment.deploy(
              runtime.gatewayAddress(), resourceAnalysis.scan().deployableResources());
      deployedResources = deployment.deployedResourceCount();
      workloadExecution =
          workloadExecutor.execute(
              runtime.gatewayAddress(), config, resourceAnalysis, payloadVariables);
      zeebeDataArtifacts =
          writeZeebeDataArtifacts(runtime, outputDirectory, config.getOutput().zipZeebeData());
    }

    final var generatedAt = Instant.now(clock).toString();
    final var manifestPath =
        manifestWriter.write(
            outputDirectory,
            WorkloadManifest.from(
                config, resourceAnalysis, generatedAt, ArtifactPaths.from(zeebeDataArtifacts)));
    final var reportPath =
        reportWriter.write(
            outputDirectory,
            reportFrom(resourceAnalysis, workloadExecution, zeebeDataArtifacts, generatedAt));

    return new GenerationResult(deployedResources, manifestPath, reportPath);
  }

  private static WorkloadReport reportFrom(
      final WorkloadResourceAnalysis resourceAnalysis,
      final WorkloadExecution workloadExecution,
      final ZeebeDataArtifacts zeebeDataArtifacts,
      final String generatedAt) {
    return new WorkloadReport(
        "1",
        generatedAt,
        new RunSummary(
            workloadExecution.startedInstances(),
            workloadExecution.completedInstances(),
            workloadExecution.activeInstances(),
            workloadExecution.createdIncidents()),
        resourceAnalysis.staticJobTypes().stream()
            .map(jobType -> jobType.type())
            .distinct()
            .toList(),
        workloadExecution.completedJobs(),
        workloadExecution.appliedWorkerOutputs(),
        workloadExecution.publishedMessages(),
        workloadExecution.completedUserTasks(),
        ZeebeDataReport.from(zeebeDataArtifacts),
        SecondaryStorageReport.skipped());
  }

  private static ZeebeDataArtifacts writeZeebeDataArtifacts(
      final AutoCloseable runtime, final Path outputDirectory, final boolean zip)
      throws IOException {
    if (runtime instanceof ZeebeDataArtifactSource zeebeDataArtifactSource) {
      return zeebeDataArtifactSource.writeZeebeData(outputDirectory, zip);
    }
    throw new ConfigException(
        "Configured runtime does not support Zeebe data artifact output: "
            + runtime.getClass().getName());
  }
}
