package io.zell.cwg.cli;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.ConfigLoader;
import io.zell.cwg.generation.RuntimeWorkloadGenerator;
import io.zell.cwg.generation.WorkloadGenerator;
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
