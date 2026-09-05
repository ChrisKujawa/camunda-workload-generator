package io.zell.cwg.resources;

public enum ResourceType {
  BPMN,
  DMN,
  FORM,
  JSON,
  YAML,
  OTHER;

  public boolean isDeployable() {
    return this == BPMN || this == DMN || this == FORM;
  }

  public boolean isPayloadOrConfig() {
    return this == JSON || this == YAML;
  }
}
