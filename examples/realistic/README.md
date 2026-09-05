# Realistic workload example

This example is a generator-compatible version of the Camunda load-test
realistic bank dispute workload from
`camunda/camunda/load-tests/load-tester/src/main/resources/bpmn/realistic`.
The source repository notice is `Copyright 2017-2024 Camunda Services GmbH`;
the source repository includes `licenses/APACHE-2.0.txt` and
`licenses/CAMUNDA-LICENSE-1.0.txt`.

Copied resources:

- `determineFraudRatingConfidence.dmn`
- `decide_on_fraud_case.form`
- `payload.json`, based on the reduced load-test payload with the DMN input
  variables included at the root

Simplified resources:

- `bankCustomerComplaintDisputeHandling.bpmn`
- `refundingProcess.bpmn`

The BPMN keeps the source scenario's root dispute process, DMN decision, analyst
user task, refund gateway, and child refund process. It intentionally leaves out
timer boundary events, event subprocesses, receive tasks, multi-instance loops,
message flows, and diagram interchange details so the current generator can
complete the example deterministically.

Run static analysis without Docker:

```bash
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar analyze-resources --config examples/realistic/workload.yaml
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar analyze-process examples/realistic/bankCustomerComplaintDisputeHandling.bpmn --process bankDisputeHandling
```

Generate artifacts in an environment with Docker/Testcontainers support:

```bash
java -jar target/camunda-workload-generator-0.1.0-SNAPSHOT.jar generate --config examples/realistic/workload.yaml
```
