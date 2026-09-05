package io.zell.cwg.config;

import java.util.List;
import java.util.Map;

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
    public String payload;
  }

  public static final class WorkloadSettings {
    public Integer startInstances;
    public Integer completeInstances;
    public Map<String, Map<String, Object>> workerOutputs;
    public List<MessageConfig> messages;
    public List<UserTaskConfig> userTasks;
  }

  public static final class MessageConfig {
    public String name;
    public String correlationKey;
    public String correlationKeyExpression;
    public Map<String, Object> variables;
    public String timing;
  }

  public static final class UserTaskConfig {
    public String elementId;
    public String name;
    public Map<String, Object> variables;
  }

  public static final class OutputConfig {
    public String path;
  }
}
