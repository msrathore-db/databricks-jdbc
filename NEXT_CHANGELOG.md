# NEXT CHANGELOG

## [Unreleased]

### Added

### Updated
- Log timestamps now explicitly display timezone.
- **[Breaking Change]** `PreparedStatement.setTimestamp(int, Timestamp, Calendar)` now properly applies Calendar timezone conversion using LocalDateTime pattern (inline with `getTimestamp`). Previously Calendar parameter was ineffective.
- `DatabaseMetaData.getColumns()` with null catalog parameter now retrieves columns from all available catalogs when using SQL Execution API.

### Fixed
- Fixed primitive types within complex types (ARRAY, MAP, STRUCT) not being correctly parsed when Arrow serialization uses alternate formats: TIMESTAMP/TIMESTAMP_NTZ as epoch microseconds or component arrays, and BINARY as base64-encoded strings.
- Fixed `PARSE_SYNTAX_ERROR` for column names containing special characters (e.g., dots) when `EnableBatchedInserts` is enabled, by re-quoting column names with backticks in reconstructed multi-row INSERT statements.

---
*Note: When making changes, please add your change under the appropriate section
with a brief description.*
