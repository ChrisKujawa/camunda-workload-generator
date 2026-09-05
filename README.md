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

The CLI, config foundation, resource scanning, static BPMN analysis,
manifest/report artifact models, managed runtime resource deployment, and
basic workload execution, Zeebe data artifact output, and optional
secondary-storage ingestion reporting exist.

## Development

This project uses Java 21 and Maven.

```bash
mvn test
mvn package
mvn test -Dgroups=docker -Dsurefire.excludedGroups=
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar --help
```

The default Maven test path skips Docker-tagged tests. Run the `docker` group
explicitly when validating managed runtime behavior. Docker tests are skipped
when Testcontainers cannot find a usable Docker environment. The
`Docker integration` GitHub Actions workflow runs the same Docker-tagged test
group manually.

The CLI supports configuration validation, effective configuration printing,
resource analysis, managed runtime deployment, and basic workload execution:

```bash
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar validate --config workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar print-config --config workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar analyze-resources --config workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar analyze-process model.bpmn --process invoice
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar generate --config workload.yaml
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
  payload: payload.json
workload:
  startInstances: 10
  completeInstances: 4
  workerOutputs:
    charge-card:
      approved: true
  messages:
    - name: payment-received
      correlationKeyExpression: =customer.orderId
      variables:
        paid: true
  userTasks:
    - name: Approve invoice
      variables:
        approved: true
secondaryStorage:
  mode: disabled
  type: opensearch
  waitForIngestion: false
  waitTimeout: PT2M
output:
  path: build/camunda-workload-generator
  zipZeebeData: false
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

BPMN analysis reports process IDs, static Zeebe job types, user tasks, call
activities, message references, and DMN decision references where they can be
read from the model without executing a runtime.

`analyze-process` focuses on one BPMN model. It prints the selected process,
the static happy-path flow node instance estimate, the happy-path nodes, static
job types, distinct worker job types, user tasks, call activities, message
references, and DMN decision references. The happy-path estimate follows the first outgoing
sequence flow, or a gateway's default flow when one is configured; it does not
evaluate FEEL conditions or execute the model.

## Artifact metadata

The generator writes metadata as stable JSON files in the configured output
directory:

- `manifest.json` describes how artifacts were produced: runtime image, workload
  config, resource metadata, and artifact paths.
- `report.json` describes what happened during a run: started/completed/active
  instance counts, incidents, detected job types, completed job counts, and
  secondary-storage ingestion status.
- `zeebe-data/` contains the copied broker data from the managed runtime.
- `zeebe-data.zip` is written when `output.zipZeebeData` is `true`.

These metadata files can be written and tested without Docker. Runtime-backed
commands fill resource metadata after deploying the configured deployable files.
Workload execution fills started/completed/active instance counts and completed
job counts. Runtime-backed generation writes Zeebe data artifact paths and basic
file/byte counts.

`secondaryStorage` is optional. The default `disabled` mode keeps managed
runtime startup small and sets `report.json` secondary-storage status to
`skipped`. `managed` mode starts an OpenSearch or Elasticsearch container and
configures Camunda to use it. `attached` mode points Camunda at an existing
endpoint:

```yaml
secondaryStorage:
  mode: managed
  type: opensearch
  waitForIngestion: true
  waitTimeout: PT3M
```

```yaml
secondaryStorage:
  mode: attached
  type: elasticsearch
  url: http://localhost:9200
  waitForIngestion: true
```

When waiting is enabled, generation polls `_cat/indices?format=json&bytes=b`
until at least one document is visible or the timeout expires. `report.json`
records the secondary-storage mode, type, endpoint, status, index count,
document count, and reported store size in bytes.

## Managed runtime

`generate` starts the configured Camunda image with Testcontainers, disables
secondary storage unless `secondaryStorage.mode` enables it, runs the broker
profile, deploys scanned BPMN/DMN/form files,
shuts the runtime down cleanly, and writes `manifest.json` plus `report.json` to
the configured output directory. During shutdown, the generator copies the
broker data directory to `zeebe-data/`; set `output.zipZeebeData: true` to also
write `zeebe-data.zip`.

After deployment, `generate` opens generic workers for statically detected
Zeebe job types, completes `workload.completeInstances` root process instances
with `withResult()`, closes the workers, then starts the remaining configured
instances so they stay active. If `resources.payload` or `--payload` points to a
JSON object, that object is sent as start variables for both completed and active
instances. Relative payload paths are resolved from the configured resources
directory. `workload.workerOutputs` maps job types to variables that generic
workers write when completing matching jobs, and `report.json` records how many
times each configured output was applied. `workload.messages` publishes explicit
messages after each completed process instance is started. A message can use a
static `correlationKey` or a simple `correlationKeyExpression` path resolved
from the start payload, and `report.json` records published message counts.
`workload.userTasks` completes explicitly configured user tasks by BPMN element
ID or task name with optional variables, and `report.json` records completed
user-task counts. Dynamic job types, incidents, and connector behavior are
handled by later slices.
