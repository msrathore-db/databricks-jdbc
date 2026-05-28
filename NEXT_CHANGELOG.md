# NEXT CHANGELOG

## [Unreleased]

### Added

### Updated

### Fixed
- Fixed `setCatalog()` and `setSchema()` producing invalid SQL (e.g. `SET CATALOG ``name``) when the catalog or schema name was passed already wrapped in backticks. Backticks are now stripped before wrapping, and `getCatalog()`/`getSchema()` return the bare identifier name.
- Fixed silent telemetry loss on SPOG (custom-URL) hosts when connecting to an all-purpose cluster via a Thrift `httpPath` like `sql/protocolv1/o/<workspace-id>/<cluster-id>`. The driver now extracts the workspace ID from the cluster path segment (in addition to the existing `?o=<workspace-id>` query-param extraction) and sets it as the `x-databricks-org-id` header on every outgoing request. Without this, telemetry POSTs to `/telemetry-ext` were redirected to `/login` because the workspace context could not be inferred from the URL.

---
*Note: When making changes, please add your change under the appropriate section
with a brief description.*