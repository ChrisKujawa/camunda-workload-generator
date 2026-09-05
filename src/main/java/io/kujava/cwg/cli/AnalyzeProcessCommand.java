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

import io.kujava.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.kujava.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.kujava.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.kujava.cwg.bpmn.BpmnAnalysis.ProcessPath;
import io.kujava.cwg.bpmn.BpmnAnalysis.StaticJobType;
import io.kujava.cwg.bpmn.BpmnAnalysis.UserTask;
import io.kujava.cwg.bpmn.BpmnAnalyzer;
import io.kujava.cwg.config.ConfigException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "analyze-process",
    mixinStandardHelpOptions = true,
    description = "Analyze one BPMN process model and print static happy-path metadata.")
public final class AnalyzeProcessCommand implements Callable<Integer> {

  @Parameters(index = "0", paramLabel = "BPMN", description = "Path to the BPMN file to analyze.")
  private Path bpmnFile;

  @Option(
      names = "--process",
      paramLabel = "ID",
      description = "Process ID to analyze when the BPMN contains multiple processes.")
  private String processId;

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    try {
      final var analysis = new BpmnAnalyzer().analyze(bpmnFile);
      final var processPath = selectProcess(analysis.processPaths());
      final var out = spec.commandLine().getOut();

      out.printf("Process model: %s%n", analysis.source());
      out.println("Processes:");
      analysis.processIds().forEach(id -> out.printf("- %s%n", id));
      out.printf("Selected process: %s%n", processPath.processId());
      out.printf("Happy-path flow node instances: %d%n", processPath.flowNodeInstances());
      out.printf("Happy path complete: %s%n", processPath.completePath());
      out.println("Happy path:");
      processPath
          .happyPath()
          .forEach(
              node ->
                  out.printf(
                      "- %s %s%s%s%n",
                      node.elementType(),
                      node.elementId(),
                      node.elementName().isBlank() ? "" : " \"" + node.elementName() + "\"",
                      node.jobType() == null ? "" : " worker=" + node.jobType()));
      final var staticJobTypes = staticJobTypes(analysis.staticJobTypes(), processPath);
      out.println("Static job types:");
      staticJobTypes.forEach(
          jobType -> out.printf("- %s (%s)%n", jobType.type(), jobType.elementId()));
      out.println("Workers:");
      staticJobTypes.stream()
          .map(jobType -> jobType.type())
          .distinct()
          .forEach(worker -> out.printf("- %s%n", worker));
      printUserTasks(userTasks(analysis.userTasks(), processPath));
      printCallActivities(callActivities(analysis.callActivities(), processPath));
      printMessageReferences(messageReferences(analysis.messageReferences(), processPath));
      printDmnReferences(dmnReferences(analysis.dmnReferences(), processPath));
      out.flush();
      return CommandLine.ExitCode.OK;
    } catch (final ConfigException e) {
      final var err = spec.commandLine().getErr();
      err.println(e.getMessage());
      err.flush();
      return CommandLine.ExitCode.USAGE;
    } catch (final IOException e) {
      final var err = spec.commandLine().getErr();
      err.printf("Failed to analyze process model: %s%n", e.getMessage());
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  private ProcessPath selectProcess(final List<ProcessPath> processPaths) {
    if (processPaths.isEmpty()) {
      throw new ConfigException("BPMN file does not contain a process: %s".formatted(bpmnFile));
    }
    if (processId == null || processId.isBlank()) {
      return processPaths.get(0);
    }
    return processPaths.stream()
        .filter(path -> processId.equals(path.processId()))
        .findFirst()
        .orElseThrow(
            () ->
                new ConfigException(
                    "Process '%s' does not exist in %s".formatted(processId, bpmnFile)));
  }

  private static List<StaticJobType> staticJobTypes(
      final List<StaticJobType> staticJobTypes, final ProcessPath processPath) {
    return staticJobTypes.stream()
        .filter(jobType -> processPath.processId().equals(jobType.processId()))
        .toList();
  }

  private static List<CallActivity> callActivities(
      final List<CallActivity> callActivities, final ProcessPath processPath) {
    return callActivities.stream()
        .filter(callActivity -> processPath.processId().equals(callActivity.processId()))
        .toList();
  }

  private static List<UserTask> userTasks(
      final List<UserTask> userTasks, final ProcessPath processPath) {
    return userTasks.stream()
        .filter(userTask -> processPath.processId().equals(userTask.processId()))
        .toList();
  }

  private static List<MessageReference> messageReferences(
      final List<MessageReference> messageReferences, final ProcessPath processPath) {
    return messageReferences.stream()
        .filter(messageReference -> processPath.processId().equals(messageReference.processId()))
        .toList();
  }

  private static List<DmnReference> dmnReferences(
      final List<DmnReference> dmnReferences, final ProcessPath processPath) {
    return dmnReferences.stream()
        .filter(dmnReference -> processPath.processId().equals(dmnReference.processId()))
        .toList();
  }

  private void printCallActivities(final List<CallActivity> callActivities) {
    final var out = spec.commandLine().getOut();
    out.println("Call activities:");
    callActivities.forEach(
        callActivity ->
            out.printf("- %s -> %s%n", callActivity.elementId(), callActivity.calledProcessId()));
  }

  private void printUserTasks(final List<UserTask> userTasks) {
    final var out = spec.commandLine().getOut();
    out.println("User tasks:");
    userTasks.forEach(
        userTask ->
            out.printf(
                "- %s%s%n",
                userTask.elementId(),
                userTask.elementName().isBlank() ? "" : " \"" + userTask.elementName() + "\""));
  }

  private void printMessageReferences(final List<MessageReference> messageReferences) {
    final var out = spec.commandLine().getOut();
    out.println("Message references:");
    messageReferences.forEach(
        messageReference ->
            out.printf(
                "- %s -> %s%n", messageReference.elementId(), messageReference.messageRef()));
  }

  private void printDmnReferences(final List<DmnReference> dmnReferences) {
    final var out = spec.commandLine().getOut();
    out.println("DMN references:");
    dmnReferences.forEach(
        dmnReference ->
            out.printf("- %s -> %s%n", dmnReference.elementId(), dmnReference.decisionId()));
  }
}
