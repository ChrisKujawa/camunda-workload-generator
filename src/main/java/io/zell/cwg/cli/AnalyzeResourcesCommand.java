package io.zell.cwg.cli;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.ConfigLoader;
import io.zell.cwg.resources.WorkloadResourceAnalyzer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "analyze-resources",
    mixinStandardHelpOptions = true,
    description = "Scan resources and report deployable files plus static BPMN metadata.")
public final class AnalyzeResourcesCommand implements Callable<Integer> {

  @Mixin private ConfigCommandMixin config;
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    try {
      final var workloadConfig = ConfigLoader.load(config.config(), config.overrides());
      final var resourcesDirectory = Path.of(workloadConfig.getResources().directory());
      final var analysis = new WorkloadResourceAnalyzer().analyze(resourcesDirectory);
      final var out = spec.commandLine().getOut();

      out.println("Deployable resources:");
      analysis.scan().deployableResources().forEach(resource -> out.printf("- %s%n", resource));
      out.println("Payload/config inputs:");
      analysis.scan().payloadOrConfigResources().forEach(resource -> out.printf("- %s%n", resource));
      out.println("BPMN process IDs:");
      analysis.processIds().forEach(processId -> out.printf("- %s%n", processId));
      out.println("Static BPMN job types:");
      analysis
          .staticJobTypes()
          .forEach(jobType -> out.printf("- %s (%s)%n", jobType.type(), jobType.elementId()));
      out.println("Call activities:");
      analysis
          .callActivities()
          .forEach(
              callActivity ->
                  out.printf(
                      "- %s -> %s%n",
                      callActivity.elementId(), callActivity.calledProcessId()));
      out.println("Message references:");
      analysis
          .messageReferences()
          .forEach(
              messageReference ->
                  out.printf(
                      "- %s -> %s%n",
                      messageReference.elementId(), messageReference.messageRef()));
      out.println("DMN references:");
      analysis
          .dmnReferences()
          .forEach(
              dmnReference ->
                  out.printf("- %s -> %s%n", dmnReference.elementId(), dmnReference.decisionId()));
      out.flush();
      return CommandLine.ExitCode.OK;
    } catch (final ConfigException e) {
      final var err = spec.commandLine().getErr();
      err.println(e.getMessage());
      err.flush();
      return CommandLine.ExitCode.USAGE;
    } catch (final IOException e) {
      final var err = spec.commandLine().getErr();
      err.printf("Failed to analyze resources: %s%n", e.getMessage());
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    }
  }
}
