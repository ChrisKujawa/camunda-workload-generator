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
