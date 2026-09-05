package io.zell.cwg.workload;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
import io.zell.cwg.workload.UserTaskCompletions.UserTaskCompletion;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class ZeebeWorkloadExecutor implements WorkloadExecutor {

  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration MESSAGE_TIME_TO_LIVE = Duration.ofMinutes(5);
  private static final Duration USER_TASK_POLL_DELAY = Duration.ofSeconds(1);

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
    final var appliedWorkerOutputs = new ConcurrentHashMap<String, LongAdder>();
    final var publishedMessages = new ConcurrentHashMap<String, LongAdder>();
    final var completedUserTasks = new ConcurrentHashMap<String, LongAdder>();
    try (final var client =
        ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build()) {
      final var userTaskCompletions = UserTaskCompletions.from(config, resourceAnalysis);
      completeProcessInstances(
          client,
          resourceAnalysis,
          rootProcessId,
          completeInstances,
          payloadVariables,
          config.getWorkload().workerOutputs(),
          config.getWorkload().messages(),
          userTaskCompletions,
          appliedWorkerOutputs,
          publishedMessages,
          completedUserTasks,
          completedJobs);
      startActiveProcessInstances(
          client, rootProcessId, startInstances - completeInstances, payloadVariables);
    }

    return new WorkloadExecution(
        startInstances,
        completeInstances,
        startInstances - completeInstances,
        0,
        count(completedJobs),
        count(appliedWorkerOutputs),
        count(publishedMessages),
        count(completedUserTasks));
  }

  private static void completeProcessInstances(
      final ZeebeClient client,
      final WorkloadResourceAnalysis resourceAnalysis,
      final String rootProcessId,
      final int completeInstances,
      final Map<String, Object> payloadVariables,
      final Map<String, Map<String, Object>> workerOutputs,
      final List<WorkloadConfig.MessageConfig> messages,
      final List<UserTaskCompletion> userTaskCompletions,
      final ConcurrentHashMap<String, LongAdder> appliedWorkerOutputs,
      final ConcurrentHashMap<String, LongAdder> publishedMessages,
      final ConcurrentHashMap<String, LongAdder> completedUserTasks,
      final ConcurrentHashMap<String, LongAdder> completedJobs) {
    if (completeInstances == 0) {
      return;
    }

    try (final var workers =
        new GenericWorkers(client, resourceAnalysis, workerOutputs, appliedWorkerOutputs, completedJobs)) {
      workers.open();
      for (int i = 0; i < completeInstances; i++) {
        if (payloadVariables.isEmpty()) {
          final var result =
              client
              .newCreateInstanceCommand()
              .bpmnProcessId(rootProcessId)
              .latestVersion()
              .withResult()
              .requestTimeout(COMMAND_TIMEOUT)
              .send();
          publishMessages(client, messages, payloadVariables, publishedMessages);
          completeUserTasksUntilProcessCompletes(
              client, result, userTaskCompletions, completedUserTasks);
        } else {
          final var result =
              client
              .newCreateInstanceCommand()
              .bpmnProcessId(rootProcessId)
              .latestVersion()
              .variables(payloadVariables)
              .withResult()
              .requestTimeout(COMMAND_TIMEOUT)
              .send();
          publishMessages(client, messages, payloadVariables, publishedMessages);
          completeUserTasksUntilProcessCompletes(
              client, result, userTaskCompletions, completedUserTasks);
        }
      }
    }
  }

  private static void completeUserTasksUntilProcessCompletes(
      final ZeebeClient client,
      final ZeebeFuture<ProcessInstanceResult> result,
      final List<UserTaskCompletion> userTaskCompletions,
      final ConcurrentHashMap<String, LongAdder> completedUserTasks) {
    if (userTaskCompletions.isEmpty()) {
      result.join();
      return;
    }

    while (!result.isDone()) {
      final var completed =
          completeAvailableUserTasks(client, userTaskCompletions, completedUserTasks);
      if (completed == 0) {
        waitForUserTasks();
      }
    }
    result.join();
  }

  private static int completeAvailableUserTasks(
      final ZeebeClient client,
      final List<UserTaskCompletion> userTaskCompletions,
      final ConcurrentHashMap<String, LongAdder> completedUserTasks) {
    var completed = 0;
    for (final var userTaskCompletion : userTaskCompletions) {
      final var userTasks =
          client
              .newUserTaskQuery()
              .filter(
                  filter ->
                      filter
                          .state("CREATED")
                          .elementId(userTaskCompletion.elementId()))
              .requestTimeout(COMMAND_TIMEOUT)
              .send()
              .join()
              .items();
      for (final var userTask : userTasks) {
        if (userTaskCompletion.variables().isEmpty()) {
          client
              .newUserTaskCompleteCommand(userTask.getKey())
              .requestTimeout(COMMAND_TIMEOUT)
              .send()
              .join();
        } else {
          client
              .newUserTaskCompleteCommand(userTask.getKey())
              .variables(userTaskCompletion.variables())
              .requestTimeout(COMMAND_TIMEOUT)
              .send()
              .join();
        }
        completedUserTasks
            .computeIfAbsent(userTaskCompletion.elementId(), ignored -> new LongAdder())
            .increment();
        completed++;
      }
    }
    return completed;
  }

  private static void waitForUserTasks() {
    try {
      Thread.sleep(USER_TASK_POLL_DELAY.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConfigException("Interrupted while waiting for user tasks");
    }
  }

  private static void publishMessages(
      final ZeebeClient client,
      final List<WorkloadConfig.MessageConfig> messages,
      final Map<String, Object> payloadVariables,
      final ConcurrentHashMap<String, LongAdder> publishedMessages) {
    for (final var message : messages) {
      final var correlationKey = MessageCorrelationKeyResolver.resolve(message, payloadVariables);
      if (message.variables().isEmpty()) {
        client
            .newPublishMessageCommand()
            .messageName(message.name())
            .correlationKey(correlationKey)
            .timeToLive(MESSAGE_TIME_TO_LIVE)
            .requestTimeout(COMMAND_TIMEOUT)
            .send()
            .join();
      } else {
        client
            .newPublishMessageCommand()
            .messageName(message.name())
            .correlationKey(correlationKey)
            .variables(message.variables())
            .timeToLive(MESSAGE_TIME_TO_LIVE)
            .requestTimeout(COMMAND_TIMEOUT)
            .send()
            .join();
      }
      publishedMessages.computeIfAbsent(message.name(), ignored -> new LongAdder()).increment();
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

  private static Map<String, Long> count(final Map<String, LongAdder> completedJobs) {
    final var counts = new LinkedHashMap<String, Long>();
    completedJobs.keySet().stream()
        .sorted()
        .forEach(jobType -> counts.put(jobType, completedJobs.get(jobType).sum()));
    return counts;
  }

  private static final class GenericWorkers implements AutoCloseable {

    private final ZeebeClient client;
    private final WorkloadResourceAnalysis resourceAnalysis;
    private final Map<String, Map<String, Object>> workerOutputs;
    private final ConcurrentHashMap<String, LongAdder> appliedWorkerOutputs;
    private final ConcurrentHashMap<String, LongAdder> completedJobs;
    private final Map<String, JobWorker> workers = new LinkedHashMap<>();

    private GenericWorkers(
        final ZeebeClient client,
        final WorkloadResourceAnalysis resourceAnalysis,
        final Map<String, Map<String, Object>> workerOutputs,
        final ConcurrentHashMap<String, LongAdder> appliedWorkerOutputs,
        final ConcurrentHashMap<String, LongAdder> completedJobs) {
      this.client = client;
      this.resourceAnalysis = resourceAnalysis;
      this.workerOutputs = workerOutputs;
      this.appliedWorkerOutputs = appliedWorkerOutputs;
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
                                final var outputVariables =
                                    WorkerOutputVariables.forJobType(job.getType(), workerOutputs);
                                if (outputVariables.isEmpty()) {
                                  jobClient
                                      .newCompleteCommand(job.getKey())
                                      .requestTimeout(COMMAND_TIMEOUT)
                                      .send()
                                      .join();
                                } else {
                                  jobClient
                                      .newCompleteCommand(job.getKey())
                                      .variables(outputVariables)
                                      .requestTimeout(COMMAND_TIMEOUT)
                                      .send()
                                      .join();
                                  appliedWorkerOutputs
                                      .computeIfAbsent(job.getType(), ignored -> new LongAdder())
                                      .increment();
                                }
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
