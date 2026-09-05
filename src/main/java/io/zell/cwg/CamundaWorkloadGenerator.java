package io.zell.cwg;

import io.zell.cwg.cli.AnalyzeResourcesCommand;
import io.zell.cwg.cli.GenerateCommand;
import io.zell.cwg.cli.IngestCommand;
import io.zell.cwg.cli.PrintConfigCommand;
import io.zell.cwg.cli.ValidateCommand;
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
      version = "camunda-workload-generator 0.1.0-SNAPSHOT",
      description = "Generate reproducible Camunda workload artifacts.",
      subcommands = {
        GenerateCommand.class,
        IngestCommand.class,
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
}
