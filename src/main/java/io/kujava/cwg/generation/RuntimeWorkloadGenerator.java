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
package io.kujava.cwg.generation;

import io.kujava.cwg.artifacts.ManifestWriter;
import io.kujava.cwg.artifacts.ReportWriter;
import io.kujava.cwg.artifacts.WorkloadManifest;
import io.kujava.cwg.artifacts.WorkloadManifest.ArtifactPaths;
import io.kujava.cwg.artifacts.WorkloadReport;
import io.kujava.cwg.artifacts.WorkloadReport.RunSummary;
import io.kujava.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import io.kujava.cwg.artifacts.WorkloadReport.ZeebeDataReport;
import io.kujava.cwg.artifacts.ZeebeDataArtifacts;
import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.WorkloadConfig;
import io.kujava.cwg.deployment.ResourceDeployment;
import io.kujava.cwg.deployment.ZeebeResourceDeployment;
import io.kujava.cwg.resources.WorkloadResourceAnalysis;
import io.kujava.cwg.resources.WorkloadResourceAnalyzer;
import io.kujava.cwg.runtime.CamundaRuntimeFactory;
import io.kujava.cwg.runtime.ManagedCamundaRuntime;
import io.kujava.cwg.runtime.SecondaryStorageEndpoint;
import io.kujava.cwg.runtime.SecondaryStorageRuntime;
import io.kujava.cwg.runtime.ZeebeDataArtifactSource;
import io.kujava.cwg.secondary.SecondaryStorageReporter;
import io.kujava.cwg.workload.PayloadVariablesLoader;
import io.kujava.cwg.workload.WorkloadExecution;
import io.kujava.cwg.workload.WorkloadExecutor;
import io.kujava.cwg.workload.ZeebeWorkloadExecutor;
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
  private final SecondaryStorageReporter secondaryStorageReporter;
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
        new SecondaryStorageReporter(),
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
      final SecondaryStorageReporter secondaryStorageReporter,
      final ManifestWriter manifestWriter,
      final ReportWriter reportWriter,
      final Clock clock) {
    this.resourceAnalyzer = resourceAnalyzer;
    this.runtimeFactory = runtimeFactory;
    this.resourceDeployment = resourceDeployment;
    this.workloadExecutor = workloadExecutor;
    this.payloadVariablesLoader = payloadVariablesLoader;
    this.secondaryStorageReporter = secondaryStorageReporter;
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
    final SecondaryStorageReport secondaryStorageReport;
    try (final var runtime = runtimeFactory.create(config)) {
      final var zeebeDataArtifactSource = zeebeDataArtifactSource(runtime);
      validateSecondaryStorageRuntime(config, runtime);
      runtime.start();
      final var deployment =
          resourceDeployment.deploy(
              runtime.gatewayAddress(),
              runtime.restAddress(),
              resourceAnalysis.scan().deployableResources());
      deployedResources = deployment.deployedResourceCount();
      workloadExecution =
          workloadExecutor.execute(
              runtime.gatewayAddress(),
              runtime.restAddress(),
              config,
              resourceAnalysis,
              payloadVariables);
      secondaryStorageReport =
          secondaryStorageReporter.report(
              config.getSecondaryStorage(), secondaryStorageEndpoint(config, runtime));
      zeebeDataArtifacts =
          zeebeDataArtifactSource.writeZeebeData(
              outputDirectory, config.getOutput().zipZeebeData());
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
            reportFrom(
                resourceAnalysis,
                workloadExecution,
                zeebeDataArtifacts,
                secondaryStorageReport,
                generatedAt));

    return new GenerationResult(deployedResources, manifestPath, reportPath);
  }

  private static WorkloadReport reportFrom(
      final WorkloadResourceAnalysis resourceAnalysis,
      final WorkloadExecution workloadExecution,
      final ZeebeDataArtifacts zeebeDataArtifacts,
      final SecondaryStorageReport secondaryStorageReport,
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
        secondaryStorageReport);
  }

  private static ZeebeDataArtifactSource zeebeDataArtifactSource(final AutoCloseable runtime) {
    if (runtime instanceof ZeebeDataArtifactSource zeebeDataArtifactSource) {
      return zeebeDataArtifactSource;
    }
    throw new ConfigException(
        "Configured runtime does not support Zeebe data artifact output: "
            + runtime.getClass().getName());
  }

  private static void validateSecondaryStorageRuntime(
      final WorkloadConfig config, final AutoCloseable runtime) {
    if (!WorkloadConfig.SecondaryStorageConfig.MODE_DISABLED.equals(
            config.getSecondaryStorage().mode())
        && !(runtime instanceof SecondaryStorageRuntime)) {
      throw new ConfigException(
          "Configured runtime does not support secondary-storage output: "
              + runtime.getClass().getName());
    }
  }

  private static java.util.Optional<SecondaryStorageEndpoint> secondaryStorageEndpoint(
      final WorkloadConfig config, final AutoCloseable runtime) {
    if (WorkloadConfig.SecondaryStorageConfig.MODE_DISABLED.equals(
        config.getSecondaryStorage().mode())) {
      return java.util.Optional.empty();
    }
    return ((SecondaryStorageRuntime) runtime).secondaryStorageEndpoint();
  }
}
