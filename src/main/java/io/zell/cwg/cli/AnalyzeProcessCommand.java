package io.zell.cwg.cli;

import io.zell.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.zell.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.zell.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.zell.cwg.bpmn.BpmnAnalysis.ProcessPath;
import io.zell.cwg.bpmn.BpmnAnalyzer;
import io.zell.cwg.config.ConfigException;
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
      out.println("Static job types:");
      analysis
          .staticJobTypes()
          .forEach(jobType -> out.printf("- %s (%s)%n", jobType.type(), jobType.elementId()));
      out.println("Workers:");
      analysis.staticJobTypes().stream()
          .map(jobType -> jobType.type())
          .distinct()
          .forEach(worker -> out.printf("- %s%n", worker));
      printCallActivities(analysis.callActivities());
      printMessageReferences(analysis.messageReferences());
      printDmnReferences(analysis.dmnReferences());
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

  private void printCallActivities(final List<CallActivity> callActivities) {
    final var out = spec.commandLine().getOut();
    out.println("Call activities:");
    callActivities.forEach(
        callActivity ->
            out.printf("- %s -> %s%n", callActivity.elementId(), callActivity.calledProcessId()));
  }

  private void printMessageReferences(final List<MessageReference> messageReferences) {
    final var out = spec.commandLine().getOut();
    out.println("Message references:");
    messageReferences.forEach(
        messageReference ->
            out.printf("- %s -> %s%n", messageReference.elementId(), messageReference.messageRef()));
  }

  private void printDmnReferences(final List<DmnReference> dmnReferences) {
    final var out = spec.commandLine().getOut();
    out.println("DMN references:");
    dmnReferences.forEach(
        dmnReference ->
            out.printf("- %s -> %s%n", dmnReference.elementId(), dmnReference.decisionId()));
  }
}
