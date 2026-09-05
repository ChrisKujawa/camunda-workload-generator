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
import io.kujava.cwg.generation.RuntimeWorkloadGenerator;
import io.kujava.cwg.generation.WorkloadGenerator;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "generate",
    mixinStandardHelpOptions = true,
    description = "Start a managed runtime, deploy resources, and write metadata artifacts.")
public final class GenerateCommand implements Callable<Integer> {

  private final WorkloadGenerator generator;

  @Mixin private ConfigCommandMixin config;
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  public GenerateCommand() {
    this(new RuntimeWorkloadGenerator());
  }

  GenerateCommand(final WorkloadGenerator generator) {
    this.generator = generator;
  }

  @Override
  public Integer call() {
    try {
      final var workloadConfig = ConfigLoader.load(config.config(), config.overrides());
      final var result = generator.generate(workloadConfig);
      final var out = spec.commandLine().getOut();
      out.printf("Deployed %d resource(s).%n", result.deployedResources());
      out.printf("Manifest: %s%n", result.manifestPath());
      out.printf("Report: %s%n", result.reportPath());
      out.flush();
      return CommandLine.ExitCode.OK;
    } catch (final ConfigException e) {
      final var err = spec.commandLine().getErr();
      err.println(e.getMessage());
      err.flush();
      return CommandLine.ExitCode.USAGE;
    } catch (final IOException e) {
      final var err = spec.commandLine().getErr();
      err.printf("Failed to generate workload artifacts: %s%n", e.getMessage());
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    } catch (final RuntimeException e) {
      final var err = spec.commandLine().getErr();
      err.printf("Failed to run managed runtime: %s%n", e.getMessage());
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    }
  }
}
