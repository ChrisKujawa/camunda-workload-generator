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
package io.kujava.cwg.config;

import java.util.List;
import java.util.Map;

public final class RawWorkloadConfig {

  public RuntimeConfig runtime;
  public ResourcesConfig resources;
  public WorkloadSettings workload;
  public SecondaryStorageConfig secondaryStorage;
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

  public static final class SecondaryStorageConfig {
    public String mode;
    public String type;
    public String url;
    public String image;
    public Boolean waitForIngestion;
    public String waitTimeout;
  }

  public static final class OutputConfig {
    public String path;
    public Boolean zipZeebeData;
  }
}
