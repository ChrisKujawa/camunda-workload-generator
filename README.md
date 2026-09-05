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

## Boundary

The generator creates data. Tools such as ZDB read data.

This repository should not depend on ZDB internals. ZDB should not depend on this
generator to read Zeebe data from production systems, copied volumes, generated
fixtures, or future secondary-storage artifacts.

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
