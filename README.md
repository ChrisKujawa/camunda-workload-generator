# camunda-workload-generator

Experimental Camunda workload generator for creating reproducible Zeebe data and
secondary-storage ingestion artifacts.

> [!WARNING]
> This repository is experimental. APIs, config files, generated artifact layouts,
> and command names can change without notice.

## Goal

`camunda-workload-generator` creates Camunda workload data from BPMN, DMN, form,
and payload resources. It is intended for experiments, fixture generation, and
investigations where reproducible Zeebe data and optional secondary-storage
ingestion artifacts are useful.

## Project boundary

`camunda-workload-generator` produces workload artifacts: Zeebe data,
manifests, reports, and optional secondary-storage ingestion output.

Readers stay separate. ZDB can consume generated Zeebe data the same way it
consumes production copies, copied volumes, or committed fixtures. This
repository should avoid depending on ZDB internals.

## Intended direction

- Deploy resources from a folder.
- Start a configurable number of process instances.
- Complete a configurable number of jobs or instances.
- Auto-detect static BPMN job types where possible.
- Allow config overrides for realistic processes.
- Write Zeebe data as a directory or zip.
- Write reproducibility manifests and run reports.
- Optionally wait for secondary-storage ingestion.

## Status

The first milestone is a non-Docker foundation. The CLI, config foundation,
resource scanning, static BPMN analysis, and manifest/report artifact models
exist. Runtime generation is a planned follow-up slice.

## Development

This project uses Java 21 and Maven.

```bash
mvn test
mvn package
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar --help
```

The initial CLI foundation supports configuration validation and effective
configuration printing without starting Docker:

```bash
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar validate --config workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar print-config --config workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar analyze-resources --config workload.yaml
```

Configuration precedence is:

```text
defaults < config file < CLI flags
```

Example config:

```yaml
runtime:
  image: camunda/camunda:8.8.0
resources:
  directory: resources
  rootProcessId: invoice
workload:
  startInstances: 10
  completeInstances: 4
output:
  path: build/camunda-workload-generator
```

## Dependency updates

Renovate is configured in `renovate.json` for Maven dependencies and GitHub
Actions updates. Non-major updates can merge automatically after CI passes.
Major updates stay manual.

The repository uses Renovate-managed automerge instead of platform automerge so
updates wait for status checks before merging.

## Resource analysis

`analyze-resources` scans the configured resource directory recursively. It
classifies deployable resources separately from payload/config inputs:

- Deployable: `*.bpmn`, `*.dmn`, `*.form`
- Payload/config inputs: `*.json`, `*.yaml`, `*.yml`

BPMN analysis reports process IDs, static Zeebe job types, call activities,
message references, and DMN decision references where they can be read from the
model without executing a runtime.

## Artifact metadata

The generator writes metadata as stable JSON files in the configured output
directory:

- `manifest.json` describes how artifacts were produced: runtime image, workload
  config, resource metadata, and artifact paths.
- `report.json` describes what happened during a run: started/completed/active
  instance counts, incidents, detected job types, completed job counts, and
  secondary-storage ingestion status.

These metadata files can be written and tested without Docker. Runtime-backed
commands will fill them with actual run data in later slices.
