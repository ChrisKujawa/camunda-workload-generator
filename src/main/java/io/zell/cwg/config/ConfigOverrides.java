package io.zell.cwg.config;

public record ConfigOverrides(
    String image,
    String resourcesDirectory,
    String rootProcessId,
    Integer startInstances,
    Integer completeInstances,
    String outputPath) {

  public static ConfigOverrides none() {
    return new ConfigOverrides(null, null, null, null, null, null);
  }
}
