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
package io.kujava.cwg;

import io.kujava.cwg.cli.AnalyzeProcessCommand;
import io.kujava.cwg.cli.AnalyzeResourcesCommand;
import io.kujava.cwg.cli.GenerateCommand;
import io.kujava.cwg.cli.IngestCommand;
import io.kujava.cwg.cli.PrintConfigCommand;
import io.kujava.cwg.cli.ValidateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

public final class CamundaWorkloadGenerator {

  private CamundaWorkloadGenerator() {}

  public static void main(final String[] args) {
    System.exit(execute(args));
  }

  public static int execute(final String... args) {
    return new CommandLine(new RootCommand()).execute(args);
  }

  @Command(
      name = "camunda-workload-generator",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class,
      description = "Generate reproducible Camunda workload artifacts.",
      subcommands = {
        GenerateCommand.class,
        IngestCommand.class,
        AnalyzeProcessCommand.class,
        AnalyzeResourcesCommand.class,
        ValidateCommand.class,
        PrintConfigCommand.class
      })
  public static final class RootCommand implements Runnable {

    @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
      spec.commandLine().usage(spec.commandLine().getOut());
    }
  }

  public static final class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
      final var implementationVersion =
          CamundaWorkloadGenerator.class.getPackage().getImplementationVersion();
      final var version =
          implementationVersion == null || implementationVersion.isBlank()
              ? "dev"
              : implementationVersion;
      return new String[] {"camunda-workload-generator " + version};
    }
  }
}
