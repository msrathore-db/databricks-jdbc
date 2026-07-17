package com.databricks.jdbc.model.client.sqlexec;

import com.databricks.sdk.service.sql.StatementParameterListItem;
import com.databricks.sdk.support.ToStringer;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.Objects;

/**
 * Represents a single set of parameters within a batch of parameter sets, sent via the {@code
 * parameter_sets} field of {@link ExecuteStatementRequest} for batch parameterized inserts.
 *
 * <p>TODO: Replace this class with the corresponding SDK implementation once it becomes available
 */
public class StatementParameterSet {
  @JsonProperty("parameters")
  private Collection<StatementParameterListItem> parameters;

  public Collection<StatementParameterListItem> getParameters() {
    return parameters;
  }

  public StatementParameterSet setParameters(Collection<StatementParameterListItem> parameters) {
    this.parameters = parameters;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(parameters);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }
    StatementParameterSet that = (StatementParameterSet) o;
    return Objects.equals(parameters, that.parameters);
  }

  @Override
  public String toString() {
    return new ToStringer(StatementParameterSet.class).add("parameters", parameters).toString();
  }
}
