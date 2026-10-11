# Hosted hazard-assessment contract

`hosted-hazard-assessment-v1.schema.json` freezes the read-only response for the Hosted assessment endpoints.

Compatibility rules:

- Existing fields retain their JSON names and meanings throughout v1.
- Additive optional fields are permitted only in a new compatible schema revision; consumers must ignore fields they do not recognize.
- Removing a required field, narrowing an enum, or changing a field's type requires v2.
- The Hosted API remains assessment-only. It must not expose confirmation, order, task, issue, or other business-write fields/endpoints.
- `UNVERIFIED` risk or `NEED_MORE_INFORMATION` means the business host must require manual review; it is not a positive compliance result.

The corresponding representative response is kept at `rag/src/test/resources/fixtures/hosted/hazard-assessment-v1.json` and locked by `HostedAssessmentContractFixtureTest`.
