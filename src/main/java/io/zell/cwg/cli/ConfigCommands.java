package io.zell.cwg.cli;

import io.zell.cwg.config.ConfigException;
import io.zell.cwg.config.ConfigLoader;
import java.io.IOException;
import picocli.CommandLine;

final class ConfigCommands {

  private ConfigCommands() {}

  static int validate(final ConfigCommandMixin config, final CommandLine commandLine) {
    try {
      ConfigLoader.load(config.config(), config.overrides());
      final var out = commandLine.getOut();
      out.println("Configuration is valid.");
      out.flush();
      return CommandLine.ExitCode.OK;
    } catch (final ConfigException e) {
      final var err = commandLine.getErr();
      err.println(e.getMessage());
      err.flush();
      return CommandLine.ExitCode.USAGE;
    } catch (final IOException e) {
      final var err = commandLine.getErr();
      err.printf("Failed to read config: %s%n", e.getMessage());
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  static int validateFutureCommand(
      final ConfigCommandMixin config, final CommandLine commandLine, final String message) {
    final var exitCode = validate(config, commandLine);
    if (exitCode == CommandLine.ExitCode.OK) {
      final var err = commandLine.getErr();
      err.println(message);
      err.flush();
      return CommandLine.ExitCode.SOFTWARE;
    }
    return exitCode;
  }
}
