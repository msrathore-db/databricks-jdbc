package com.databricks.jdbc.api.impl.converters;

import static org.junit.jupiter.api.Assertions.*;

import com.databricks.jdbc.exception.DatabricksSQLException;
import org.junit.jupiter.api.Test;

public class BitConverterTest {

  private final BitConverter bitConverter = new BitConverter();

  @Test
  public void testToStringWithBooleanTrue() throws DatabricksSQLException {
    assertEquals("true", bitConverter.toString(true));
  }

  @Test
  public void testToStringWithBooleanFalse() throws DatabricksSQLException {
    assertEquals("false", bitConverter.toString(false));
  }

  @Test
  public void testToStringWithBooleanObject() throws DatabricksSQLException {
    Boolean trueObject = Boolean.TRUE;
    Boolean falseObject = Boolean.FALSE;

    assertEquals("true", bitConverter.toString(trueObject));
    assertEquals("false", bitConverter.toString(falseObject));
  }

  @Test
  public void testToStringWithUnsupportedType() {
    DatabricksSQLException exception =
        assertThrows(DatabricksSQLException.class, () -> bitConverter.toString("not a boolean"));

    assertTrue(exception.getMessage().contains("Unsupported String conversion operation"));
    assertEquals("UNSUPPORTED_OPERATION", exception.getSQLState());
  }

  @Test
  public void testToStringWithNumber() {
    DatabricksSQLException exception =
        assertThrows(DatabricksSQLException.class, () -> bitConverter.toString(123));

    assertTrue(exception.getMessage().contains("Unsupported String conversion operation"));
    assertEquals("UNSUPPORTED_OPERATION", exception.getSQLState());
  }

  @Test
  public void testToBooleanWithBooleanTrue() throws DatabricksSQLException {
    assertTrue(bitConverter.toBoolean(true));
  }

  @Test
  public void testToBooleanWithBooleanFalse() throws DatabricksSQLException {
    assertFalse(bitConverter.toBoolean(false));
  }

  @Test
  public void testToBooleanWithBooleanObject() throws DatabricksSQLException {
    Boolean trueObject = Boolean.TRUE;
    Boolean falseObject = Boolean.FALSE;

    assertTrue(bitConverter.toBoolean(trueObject));
    assertFalse(bitConverter.toBoolean(falseObject));
  }

  @Test
  public void testToBooleanWithNumberZero() throws DatabricksSQLException {
    assertFalse(bitConverter.toBoolean(0));
    assertFalse(bitConverter.toBoolean(0L));
    assertFalse(bitConverter.toBoolean(0.0f));
    assertFalse(bitConverter.toBoolean(0.0));
  }

  @Test
  public void testToBooleanWithNumberNonZero() throws DatabricksSQLException {
    assertTrue(bitConverter.toBoolean(1));
    assertTrue(bitConverter.toBoolean(-1));
    assertTrue(bitConverter.toBoolean(42L));
    assertTrue(bitConverter.toBoolean(3.14f));
    assertTrue(bitConverter.toBoolean(2.71));
  }

  @Test
  public void testToBooleanWithStringTrue() throws DatabricksSQLException {
    assertTrue(bitConverter.toBoolean("true"));
    assertTrue(bitConverter.toBoolean("TRUE"));
    assertTrue(bitConverter.toBoolean("True"));
  }

  @Test
  public void testToBooleanWithStringFalse() throws DatabricksSQLException {
    assertFalse(bitConverter.toBoolean("false"));
    assertFalse(bitConverter.toBoolean("FALSE"));
    assertFalse(bitConverter.toBoolean("False"));
    assertFalse(bitConverter.toBoolean("anything else"));
  }

  @Test
  public void testToBooleanWithUnsupportedType() {
    DatabricksSQLException exception =
        assertThrows(DatabricksSQLException.class, () -> bitConverter.toBoolean(new Object()));

    assertTrue(exception.getMessage().contains("Unsupported type for conversion to BIT"));
    assertTrue(exception.getMessage().contains("Object"));
    assertEquals("UNSUPPORTED_OPERATION", exception.getSQLState());
  }

  @Test
  public void testToBooleanWithNull() {
    DatabricksSQLException exception =
        assertThrows(DatabricksSQLException.class, () -> bitConverter.toBoolean(null));

    assertTrue(exception.getMessage().contains("Unsupported type for conversion to BIT"));
    assertTrue(exception.getMessage().contains("null"));
    assertEquals("UNSUPPORTED_OPERATION", exception.getSQLState());
  }
}
