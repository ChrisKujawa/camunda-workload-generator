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

No implementation exists yet. The first milestone is a non-Docker foundation:
CLI shape, config parsing, resource scanning, BPMN analysis, and manifest/report
models.
