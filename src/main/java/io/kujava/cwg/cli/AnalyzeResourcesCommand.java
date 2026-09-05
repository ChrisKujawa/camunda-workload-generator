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

import io.kujava.cwg.config.ConfigException;
import io.kujava.cwg.config.ConfigLoader;
import io.kujava.cwg.resources.WorkloadResourceAnalyzer;
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
      analysis
          .scan()
          .payloadOrConfigResources()
          .forEach(resource -> out.printf("- %s%n", resource));
      out.println("BPMN process IDs:");
      analysis.processIds().forEach(processId -> out.printf("- %s%n", processId));
      out.println("Static BPMN job types:");
      analysis
          .staticJobTypes()
          .forEach(jobType -> out.printf("- %s (%s)%n", jobType.type(), jobType.elementId()));
      out.println("User tasks:");
      analysis
          .userTasks()
          .forEach(
              userTask ->
                  out.printf("- %s%s%n", userTask.elementId(), named(userTask.elementName())));
      out.println("Call activities:");
      analysis
          .callActivities()
          .forEach(
              callActivity ->
                  out.printf(
                      "- %s -> %s%n", callActivity.elementId(), callActivity.calledProcessId()));
      out.println("Message references:");
      analysis
          .messageReferences()
          .forEach(
              messageReference ->
                  out.printf(
                      "- %s -> %s%n", messageReference.elementId(), messageReference.messageRef()));
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

  private static String named(final String name) {
    return name == null || name.isBlank() ? "" : " \"" + name + "\"";
  }
}
