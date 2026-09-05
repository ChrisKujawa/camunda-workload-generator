package io.zell.cwg.config;

public final class RawWorkloadConfig {

  public RuntimeConfig runtime;
  public ResourcesConfig resources;
  public WorkloadSettings workload;
  public OutputConfig output;

  public static final class RuntimeConfig {
    public String image;
  }
  public static final class ResourcesConfig {
    public String directory;
    public String rootProcessId;
  }

  public static final class WorkloadSettings {
    public Integer startInstances;
    public Integer completeInstances;
  }

  public static final class OutputConfig {
    public String path;
  }
}
