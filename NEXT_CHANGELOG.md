# NEXT CHANGELOG

## [Unreleased]

### Added

### Updated
- Bumped the Databricks SDK for Java dependency from `0.106.0` to `0.118.0`.

### Fixed
- Fixed `DatabaseMetaData.getURL()` exposing credentials embedded in the connection URL; secret parameters are now masked (the URL is otherwise unchanged).
- Fixed presigned URL credentials not being fully redacted in logs.
- Fixed access token exposure in DEBUG logs.
- Fixed `StackOverflowError` / hang when closing a `ResultSet` or `Statement` with `closeOnCompletion()` enabled.
- Fixed SQL injection vulnerability in binary parameter handling.
- Fixed `setCatalog()` and `setSchema()` producing invalid SQL (e.g. `SET CATALOG ``name``) when the catalog or schema name was passed already wrapped in backticks. Backticks are now stripped before wrapping, and `getCatalog()`/`getSchema()` return the bare identifier name.
- Fixed metadata SQL generation for catalog, schema, and table identifiers containing backticks.
- Fixed SEA result truncation when direct results are disabled. Large, highly-compressible results that span multiple chunks were delivered inline via the old hybrid path and truncated to the first chunk. The SQL Execution path now uses an async (`0s`) wait timeout when direct results are disabled, so results are returned via external links and fetched in full.
- Fixed `getColumns()` flooding the `DriverManager` log writer with caught-and-recovered `Invalid column index` stack traces.
- Fixed timezone-shifted TIMESTAMP values when retrieving nested complex types (STRUCT/ARRAY/MAP) with `EnableComplexDatatypeSupport=1`.
- Fixed `DatabricksDatabaseMetaData.supportsBatchUpdates()` always returning `false`, which caused batch-aware JDBC clients (e.g. Apache Hop) to skip `executeBatch()` and fall back to one INSERT per row. It now returns `true` when `EnableBatchedInserts=1`, so those clients use the optimized multi-row INSERT path.
- Fixed `ResultSetMetaData.getColumnTypeName()` returning `TIMESTAMP` for `TIMESTAMP_NTZ` columns (e.g. `SELECT MIN(ntz_col) ...`), a regression from 3.0.7. By default the driver now preserves the `TIMESTAMP_NTZ` type name across the SEA, Thrift, and describe-query metadata paths; `getColumnType()` continues to report `java.sql.Types.TIMESTAMP`. Set the new connection property `EnableTimestampNtzTypeName=0` to restore the previous behavior (report `TIMESTAMP`), which matches the legacy (v2.x.x) driver. ([#1495](https://github.com/databricks/databricks-jdbc/issues/1495))

---
*Note: When making changes, please add your change under the appropriate section
with a brief description.*