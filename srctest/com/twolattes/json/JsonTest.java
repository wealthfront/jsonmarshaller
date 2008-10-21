package com.twolattes.json;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.StringWriter;
import java.math.BigDecimal;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

public class JsonTest {

  private StringWriter writer;

  @Before
  public void before() throws Exception {
    writer = new StringWriter();
  }

  @Test
  public void writeNull() throws Exception {
    Json.NULL.write(writer);
    assertEquals("null", writer.toString());
  }

  @Test
  public void writeBooleanTrue() throws Exception {
    Json.TRUE.write(writer);
    assertEquals("true", writer.toString());
  }

  @Test
  public void writeBooleanFalse() throws Exception {
    Json.FALSE.write(writer);
    assertEquals("false", writer.toString());
  }

  @Test
  public void writeNumber1() throws Exception {
    new Json.Number(56.3).write(writer);
    assertEquals("56.3", writer.toString());
  }

  @Test
  public void writeNumber2() throws Exception {
    new Json.Number(new BigDecimal("789798793182739721789798793182739721")).write(writer);
    assertEquals("789798793182739721789798793182739721", writer.toString());

    double number = new JSONArray("[" + writer.toString() + "]").getDouble(0);
    assertEquals(789798793182739721789798793182739721.0, number);
  }

  @Test
  public void writeString1() throws Exception {
    new Json.String("yeah!").write(writer);
    assertEquals("\"yeah!\"", writer.toString());
  }

  @Test
  public void writeString2() throws Exception {
    new Json.String("ye\"ah!").write(writer);
    assertEquals("\"ye\\\"ah!\"", writer.toString());
  }

  @Test
  public void writeString3() throws Exception {
    new Json.String("ye\\ah!").write(writer);
    assertEquals("\"ye\\\\ah!\"", writer.toString());
  }

  @Test
  public void writeString4() throws Exception {
    assertEquals("\"\\b\"", new Json.String("\b").toString());
    assertEquals("\"\\f\"", new Json.String("\f").toString());
    assertEquals("\"\\n\"", new Json.String("\n").toString());
    assertEquals("\"\\r\"", new Json.String("\r").toString());
    assertEquals("\"\\t\"", new Json.String("\t").toString());
  }

  @Test
  public void writeArray1() throws Exception {
    new Json.Array().write(writer);
    assertEquals("[]", writer.toString());
  }

  @Test
  public void writeArray2() throws Exception {
    new Json.Array(Json.TRUE, Json.FALSE).write(writer);
    assertEquals("[true,false]", writer.toString());
  }

  @Test
  public void writeObject1() throws Exception {
    new Json.Object().write(writer);
    assertEquals("{}", writer.toString());
  }

  @Test
  public void writeObject2() throws Exception {
    Json.Object o = new Json.Object();
    o.put(new Json.String("a"), Json.NULL);
    o.write(writer);
    assertEquals("{\"a\":null}", writer.toString());
  }

  @Test
  public void writeObject3() throws Exception {
    Json.Object o = new Json.Object();
    o.put(new Json.String("a"), Json.NULL);
    o.put(new Json.String("b"), Json.NULL);
    o.write(writer);
    assertEquals("{\"a\":null,\"b\":null}", writer.toString());
  }

  @Test
  public void readNull1() throws Exception {
    assertEquals(Json.NULL, Json.fromString("null"));
  }

  @Test
  public void readNull2() throws Exception {
    assertEquals(Json.NULL, Json.fromString("  null "));
  }

  @Test
  public void readBoolean1() throws Exception {
    assertEquals(Json.TRUE, Json.fromString("true"));
  }

  @Test
  public void readBoolean2() throws Exception {
    assertEquals(Json.FALSE, Json.fromString("false"));
  }

  @Test
  public void readBoolean3() throws Exception {
    assertEquals(Json.FALSE, Json.fromString("\tfalse"));
  }

  @Test
  public void readString1() throws Exception {
    assertEquals(new Json.String(""), Json.fromString("\"\""));
  }

  @Test
  public void readString2() throws Exception {
    assertEquals(new Json.String("a"), Json.fromString("\"a\""));
  }

  @Test
  public void readString3() throws Exception {
    assertEquals(new Json.String(" "), Json.fromString("\" \""));
  }

  @Test
  public void readString4() throws Exception {
    assertEquals(new Json.String("\""), Json.fromString("\"\\\"\""));
    assertEquals(new Json.String("\\"), Json.fromString("\"\\\\\""));
    assertEquals(new Json.String("/"), Json.fromString("\"\\/\""));
    assertEquals(new Json.String("\b"), Json.fromString("\"\\b\""));
    assertEquals(new Json.String("\f"), Json.fromString("\"\\f\""));
    assertEquals(new Json.String("\n"), Json.fromString("\"\\n\""));
    assertEquals(new Json.String("\r"), Json.fromString("\"\\r\""));
    assertEquals(new Json.String("\t"), Json.fromString("\"\\t\""));
    assertEquals(new Json.String("\ua098"), Json.fromString("\"\\ua098\""));
    assertEquals(new Json.String("\u2931"), Json.fromString("\"\\u2931\""));
  }

  @Test(expected = IllegalArgumentException.class)
  public void readString5() throws Exception {
    Json.fromString("\"");
  }

  @Test
  public void readString6() throws Exception {
    assertEquals(
        new Json.String("can you parse me ? this aleph (\u05D0)"),
        Json.fromString("\"can you parse me ? this aleph (\u05D0)\""));
  }

  @Test
  public void readNumber1() throws Exception {
    assertEquals(new Json.Number(1), Json.fromString("1"));
  }

  @Test
  public void readNumber2() throws Exception {
    assertEquals(new Json.Number(-1), Json.fromString("-1"));
  }

  @Test
  public void readNumber3() throws Exception {
    assertEquals(new Json.Number(0.78), Json.fromString("0.78"));
  }

  @Test
  public void readNumber4() throws Exception {
    // We allow multiple 0 to make the parse faster.
    Json.fromString("00.78");
  }

  @Test
  public void readNumber5() throws Exception {
    assertEquals(new Json.Number(10), Json.fromString("1e+1"));
    assertEquals(new Json.Number(10), Json.fromString("1E+1"));
    assertEquals(new Json.Number(400), Json.fromString("4e2"));
    assertEquals(new Json.Number(400), Json.fromString("4E2"));
    assertEquals(new Json.Number(0.1), Json.fromString("1e-1"));
    assertEquals(new Json.Number(0.1), Json.fromString("1E-1"));
    assertEquals(new Json.Number(8), Json.fromString("8e+00"));
    assertEquals(new Json.Number(9), Json.fromString("9E+0"));
    assertEquals(new Json.Number(125), Json.fromString("125e-0"));
    assertEquals(new Json.Number(3), Json.fromString("3E-0000"));
  }

  @Test(expected = NumberFormatException.class)
  public void readNumber6() throws Exception {
    Json.fromString("1e");
  }

  @Test(expected = NumberFormatException.class)
  public void readNumber7() throws Exception {
    Json.fromString("1E+");
  }

  @Test(expected = NumberFormatException.class)
  public void readNumber8() throws Exception {
    Json.fromString("1e-");
  }

  @Test
  public void readNumber9() throws Exception {
    assertEquals(
        new Json.Number(new BigDecimal("78923187432674312231.78923187432674312231e21733")),
        Json.fromString("78923187432674312231.78923187432674312231e21733"));
  }

  @Test
  public void readArray1() throws Exception {
    assertEquals(new Json.Array(), Json.fromString("[]"));
  }

  @Test
  public void readArray2() throws Exception {
    assertEquals(new Json.Array(Json.TRUE), Json.fromString("[true]"));
  }

  @Test
  public void readArray3() throws Exception {
    assertEquals(
        new Json.Array(Json.TRUE, Json.NULL),
        Json.fromString("[  true , null \t]"));
  }

  @Test
  public void readArray4() throws Exception {
    assertEquals(
        new Json.Array(Json.TRUE, Json.NULL, new Json.String("")),
        Json.fromString("[  true , null \t,\"\" ]"));
    assertEquals(
        new Json.Array(Json.TRUE, Json.NULL, new Json.String("")),
        Json.fromString("[true,null,\"\"]"));
    assertEquals(
        new Json.Array(Json.TRUE, Json.NULL, new Json.String("")),
        Json.fromString("[true, null,\"\"]"));
  }

  @Test
  public void readArray5() throws Exception {
    assertEquals(
        new Json.Array(
            new Json.Array(
                new Json.Array(
                    new Json.Array(Json.FALSE)))),
        Json.fromString("[[[[false]]]]"));
  }

  @Test
  public void readArray6() throws Exception {
    assertEquals(
        new Json.Array(
            new Json.Array(
                new Json.Array(
                    new Json.Array(Json.FALSE))),
        Json.NULL,
        new Json.Array(Json.FALSE)),
        Json.fromString("[[[[false]]],null,[false]]"));
  }

  @Test
  public void readArray7() throws Exception {
    assertEquals(
        new Json.Array(new Json.Number(89), new Json.Number(8.7), new Json.Number(42)),
        Json.fromString("[89,8.7,4.2e1]"));
  }

  @Test
  public void readObject1() throws Exception {
    assertEquals(new Json.Object(), Json.fromString("{}"));
  }

  @Test
  public void readObject2() throws Exception {
    Json.Object object = new Json.Object();
    object.put(new Json.String("a"), new Json.Number(5));
    assertEquals(object, Json.fromString("{\"a\":5}"));
  }

  @Test
  public void readObject3() throws Exception {
    Json.Object object = new Json.Object();
    object.put(new Json.String("a"), new Json.Number(5));
    object.put(new Json.String("b"), Json.FALSE);
    assertEquals(object, Json.fromString("{\"a\":5,\"b\":false}"));
  }

  @Test
  public void fromHex() {
    assertEquals(0, Json.fromHex('0'));
    assertEquals(3, Json.fromHex('3'));
    assertEquals(8, Json.fromHex('8'));
    assertEquals(9, Json.fromHex('9'));
    assertEquals(10, Json.fromHex('a'));
    assertEquals(15, Json.fromHex('f'));
    assertEquals(10, Json.fromHex('A'));
    assertEquals(15, Json.fromHex('F'));
  }

  @Test
  public void objectEqualsAndHashCode1() throws Exception {
    testEqualsAndHashCode(
        new Json.Object(),
        new Json.Object());
  }

  @Test
  public void objectEqualsAndHashCode2() throws Exception {
    Json.Object o2 = new Json.Object();
    o2.put(new Json.String("a"), new Json.Boolean(true));
    testNotEquals(
        new Json.Object(),
        o2);
  }

  @Test
  public void objectEqualsAndHashCode3() throws Exception {
    Json.Object o1 = new Json.Object();
    o1.put(new Json.String("a"), new Json.Boolean(true));

    Json.Object o2 = new Json.Object();
    o2.put(new Json.String("a"), new Json.Boolean(true));
    testEqualsAndHashCode(o1, o2);
  }

  @Test
  public void stringEqualsAndHashCode() throws Exception {
    testEqualsAndHashCode(
        new Json.String("hello"),
        new Json.String("hello"));
  }

  @Test
  public void numberEqualsAndHashCode() throws Exception {
    testEqualsAndHashCode(
        new Json.Number(9.0),
        new Json.Number(new BigDecimal(9.0)));
    testEqualsAndHashCode(
        new Json.Number(new BigDecimal("0.00")),
        new Json.Number(new BigDecimal("0.0")));
  }

  @Test
  public void booleanEqualsAndHashCode() throws Exception {
    testEqualsAndHashCode(
        new Json.Boolean(true),
        new Json.Boolean(true));
    testEqualsAndHashCode(
        new Json.Boolean(false),
        new Json.Boolean(false));
    testNotEquals(new Json.Boolean(true), new Json.Boolean(false));
  }

  @Test
  public void nullEqualsAndHashCode() throws Exception {
    testEqualsAndHashCode(
        new Json.Null(),
        new Json.Null());
  }

  private void testEqualsAndHashCode(Json v1, Json v2) {
    assertEquals(v1, v2);
    assertEquals(v2, v1);
    assertTrue(v1.hashCode() == v2.hashCode());
  }

  private void testNotEquals(Json v1, Json v2) {
    assertFalse(v1.equals(v2));
    assertFalse(v2.equals(v1));
  }

}