package io.zell.cwg.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "validate",
    mixinStandardHelpOptions = true,
    description = "Validate the workload configuration without starting a Camunda runtime.")
public final class ValidateCommand implements Callable<Integer> {

  @Mixin private ConfigCommandMixin config;
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    return ConfigCommands.validate(config, spec.commandLine());
  }
}
