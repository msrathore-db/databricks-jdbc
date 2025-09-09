package com.databricks.jdbc.common.util;

import static com.databricks.jdbc.common.MetadataResultConstants.NULL_STRING;

import com.databricks.jdbc.api.impl.ImmutableSqlParameter;
import com.databricks.jdbc.exception.DatabricksValidationException;
import com.databricks.jdbc.model.core.ColumnInfoTypeName;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLInterpolator {
  private static String escapeApostrophes(String input) {
    if (input == null) return null;
    return input.replace("'", "''");
  }

  private static String formatObject(ImmutableSqlParameter object) {
    if (object == null || object.value() == null) {
      return NULL_STRING;
    } else if (object.type() == ColumnInfoTypeName.BINARY) {
      // Don't wrap within quotes. Don't treat hex literals as string.
      return object.value().toString();
    } else if (object.value() instanceof String) {
      return "'" + escapeApostrophes((String) object.value()) + "'";
    } else {
      return object.value().toString();
    }
  }

  private static int countPlaceholders(String sql) {
    int count = 0;
    for (char c : sql.toCharArray()) {
      if (c == '?') {
        count++;
      }
    }
    return count;
  }

  /**
   * Interpolates the given SQL string by replacing placeholders with the provided parameters.
   *
   * <p>This method splits the SQL string by placeholders (question marks) and replaces each
   * placeholder with the corresponding parameter from the provided map. The map keys are 1-based
   * indexes, aligning with the SQL parameter positions.
   *
   * @param sql the SQL string containing placeholders ('?') to be replaced.
   * @param params a map of parameters where the key is the 1-based index of the placeholder in the
   *     SQL string, and the value is the corresponding {@link ImmutableSqlParameter}.
   * @return the interpolated SQL string with placeholders replaced by the corresponding parameters.
   * @throws DatabricksValidationException if the number of placeholders in the SQL string does not
   *     match the number of parameters provided in the map.
   */
  public static String interpolateSQL(String sql, Map<Integer, ImmutableSqlParameter> params)
      throws DatabricksValidationException {
    String[] parts = sql.split("\\?");
    if (countPlaceholders(sql) != params.size()) {
      throw new DatabricksValidationException(
          "Parameter count does not match. Provide equal number of parameters as placeholders. SQL "
              + sql);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      sb.append(parts[i]);
      if (i < params.size()) {
        sb.append(formatObject(params.get(i + 1))); // because we have 1 based index in params
      }
    }
    return sb.toString();
  }

  /**
   * Surrounds unquoted placeholders (?) with single quotes, preserving already quoted ones. This is
   * crucial for DESCRIBE QUERY commands as unquoted placeholders will cause a parse_syntax_error.
   */
  public static String surroundPlaceholdersWithQuotes(String sql) {
    if (sql == null || sql.isEmpty()) {
      return sql;
    }
    // This pattern matches any '?' that is NOT already inside single quotes
    StringBuilder sb = new StringBuilder();
    Matcher m = Pattern.compile("(?<!')\\?(?!')").matcher(sql);
    while (m.find()) {
      m.appendReplacement(sb, "'?'");
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
