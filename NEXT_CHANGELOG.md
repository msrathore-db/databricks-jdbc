# NEXT CHANGELOG

## [Unreleased]

### Added

- Added `enableMultipleCatalogSupport` connection parameter to control catalog metadata behavior.
- Added `IgnoreTransactions` connection parameter to silently ignore transaction method calls.
### Updated
- Telemetry data is now captured more efficiently and consistently due to enhancements in the log and connection close flush logic.
- Updated Databricks SDK version to v0.65.0 (This is to fix OAuthClient to properly encode complex query parameters.)
- Added IgnoreTransactions connection parameter to silently ignore transaction method calls.

### Fixed
- Fixed complex data type conversion issues by improving StringConverter to handle Databricks complex objects (arrays/maps/structs), JDBC arrays/structs, and generic collections.
- Fixed ComplexDataTypeParser to correctly parse ISO timestamps with T separators and timezone offsets, preventing Arrow ingestion failures.
---
*Note: When making changes, please add your change under the appropriate section with a brief description.* 
