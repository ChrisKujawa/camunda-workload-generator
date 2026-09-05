package io.zell.cwg.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "generate",
    mixinStandardHelpOptions = true,
    description = "Validate workload configuration before runtime generation is implemented.")
public final class GenerateCommand implements Callable<Integer> {

  @Mixin private ConfigCommandMixin config;
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    return ConfigCommands.validateFutureCommand(
        config,
        spec.commandLine(),
        "Runtime generation is not implemented in this foundation slice.");
  }
}
