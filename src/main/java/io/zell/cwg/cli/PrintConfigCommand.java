package io.zell.cwg.cli;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.ConfigLoader;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "print-config",
    mixinStandardHelpOptions = true,
    description = "Print the effective workload configuration after defaults, config, and CLI overrides.")
public final class PrintConfigCommand implements Callable<Integer> {

  @Mixin private ConfigCommandMixin config;
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    try {
      final var effective = ConfigLoader.load(config.config(), config.overrides());
      final var out = spec.commandLine().getOut();
      out.print(ConfigLoader.toYaml(effective));
      out.flush();
      return CommandLine.ExitCode.OK;
    } catch (final ConfigException e) {
      spec.commandLine().getErr().println(e.getMessage());
      return CommandLine.ExitCode.USAGE;
    } catch (final IOException e) {
      spec.commandLine().getErr().printf("Failed to read config: %s%n", e.getMessage());
      return CommandLine.ExitCode.SOFTWARE;
    }
  }
}
