package io.zell.cwg.workload;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class ZeebeWorkloadExecutor implements WorkloadExecutor {

  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

  @Override
  public WorkloadExecution execute(
      final String gatewayAddress,
      final WorkloadConfig config,
      final WorkloadResourceAnalysis resourceAnalysis,
      final Map<String, Object> payloadVariables) {
    final var startInstances = config.getWorkload().startInstances();
    final var completeInstances = config.getWorkload().completeInstances();
    if (startInstances == 0) {
      return WorkloadExecution.skipped();
    }

    final var rootProcessId = config.getResources().rootProcessId();
    if (rootProcessId == null || rootProcessId.isBlank()) {
      throw new ConfigException(
          "resources.rootProcessId must be set when workload.startInstances is greater than 0");
    }

    final var completedJobs = new ConcurrentHashMap<String, LongAdder>();
    try (final var client =
        ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build()) {
      completeProcessInstances(
          client,
          resourceAnalysis,
          rootProcessId,
          completeInstances,
          payloadVariables,
          completedJobs);
      startActiveProcessInstances(
          client, rootProcessId, startInstances - completeInstances, payloadVariables);
    }

    return new WorkloadExecution(
        startInstances,
        completeInstances,
        startInstances - completeInstances,
        0,
        completedJobs(completedJobs));
  }

  private static void completeProcessInstances(
      final ZeebeClient client,
      final WorkloadResourceAnalysis resourceAnalysis,
      final String rootProcessId,
      final int completeInstances,
      final Map<String, Object> payloadVariables,
      final ConcurrentHashMap<String, LongAdder> completedJobs) {
    if (completeInstances == 0) {
      return;
    }

    try (final var workers = new GenericWorkers(client, resourceAnalysis, completedJobs)) {
      workers.open();
      for (int i = 0; i < completeInstances; i++) {
        if (payloadVariables.isEmpty()) {
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(rootProcessId)
              .latestVersion()
              .withResult()
              .requestTimeout(COMMAND_TIMEOUT)
              .send()
              .join();
        } else {
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(rootProcessId)
              .latestVersion()
              .variables(payloadVariables)
              .withResult()
              .requestTimeout(COMMAND_TIMEOUT)
              .send()
              .join();
        }
      }
    }
  }

  private static void startActiveProcessInstances(
      final ZeebeClient client,
      final String rootProcessId,
      final int activeInstances,
      final Map<String, Object> payloadVariables) {
    for (int i = 0; i < activeInstances; i++) {
      if (payloadVariables.isEmpty()) {
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(rootProcessId)
            .latestVersion()
            .requestTimeout(COMMAND_TIMEOUT)
            .send()
            .join();
      } else {
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(rootProcessId)
            .latestVersion()
            .variables(payloadVariables)
            .requestTimeout(COMMAND_TIMEOUT)
            .send()
            .join();
      }
    }
  }

  private static Map<String, Long> completedJobs(final Map<String, LongAdder> completedJobs) {
    final var counts = new LinkedHashMap<String, Long>();
    completedJobs.keySet().stream()
        .sorted()
        .forEach(jobType -> counts.put(jobType, completedJobs.get(jobType).sum()));
    return counts;
  }

  private static final class GenericWorkers implements AutoCloseable {

    private final ZeebeClient client;
    private final WorkloadResourceAnalysis resourceAnalysis;
    private final ConcurrentHashMap<String, LongAdder> completedJobs;
    private final Map<String, JobWorker> workers = new LinkedHashMap<>();

    private GenericWorkers(
        final ZeebeClient client,
        final WorkloadResourceAnalysis resourceAnalysis,
        final ConcurrentHashMap<String, LongAdder> completedJobs) {
      this.client = client;
      this.resourceAnalysis = resourceAnalysis;
      this.completedJobs = completedJobs;
    }

    private void open() {
      resourceAnalysis.staticJobTypes().stream()
          .map(jobType -> jobType.type())
          .distinct()
          .forEach(
              jobType ->
                  workers.put(
                      jobType,
                      client
                          .newWorker()
                          .jobType(jobType)
                          .handler(
                              (jobClient, job) -> {
                                jobClient
                                    .newCompleteCommand(job.getKey())
                                    .requestTimeout(COMMAND_TIMEOUT)
                                    .send()
                                    .join();
                                completedJobs
                                    .computeIfAbsent(job.getType(), ignored -> new LongAdder())
                                    .increment();
                              })
                          .name("camunda-workload-generator-" + jobType)
                          .open()));
    }

    @Override
    public void close() {
      workers.values().forEach(JobWorker::close);
    }
  }
}
