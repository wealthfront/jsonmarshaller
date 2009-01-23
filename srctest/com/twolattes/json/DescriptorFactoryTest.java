package com.twolattes.json;

import static com.twolattes.json.StringDescriptor.STRING_DESC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;

import org.junit.Ignore;
import org.junit.Test;


public class DescriptorFactoryTest {
  @Test(expected = IllegalArgumentException.class)
  public void testNotAnEntity() throws IOException {
    create(NotAnEntity.class);
  }

  @Test
  public void testBaseTypeEntity() throws IOException {
    EntityDescriptor<?> d = create(BaseTypeEntity.class);
    assertEquals(BaseTypeEntity.class, d.getReturnedClass());
    assertEquals(8, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertEquals(f.getFieldName(), f.getJsonName());
      Descriptor<?, ?> fd = f.getDescriptor();
      assertEquals(BaseTypeEntity.fields.get(f.getFieldName()), fd.getReturnedClass());
      assertNull(f.getShouldInline());
    }
  }

  @Test
  public void testGetterSetterEntity() throws Exception {
    EntityDescriptor<?> d = create(GetterSetterEntity.class);
    assertEquals(GetterSetterEntity.class, d.getReturnedClass());
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertEquals("name", f.getJsonName());
      assertEquals("name", f.getFieldName());
    }
  }

  @Test
  public void testEntityInterface() throws Exception {
    EntityDescriptor<?> d = create(EntityInterface.class);
    assertEquals(EntityInterface.class, d.getReturnedClass());
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertEquals("whatever", f.getJsonName());
      assertEquals("whatever", f.getFieldName());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEntityInterfaceWrongImplementedBy() throws Exception {
    create(EntityInterfaceWrongImplementedBy.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetSetCollidingWithField() throws Exception {
    create(GetSetCollidingWithField.class);
  }

  @Test
  public void testCollectionEntity() throws IOException {
    EntityDescriptor<?> d = create(CollectionEntity.class);
    assertEquals(CollectionEntity.class, d.getReturnedClass());
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertEquals("buddies", f.getFieldName());
      assertEquals("friends", f.getJsonName());
      assertEquals(
          new CollectionDescriptor(Collection.class, STRING_DESC),
          f.getDescriptor());
      assertNull(f.getShouldInline());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testContravariantCollectionEntity() throws IOException {
    create(ContravariantCollectionEntity.class);
  }

  @Test
  public void testEnitityInEntity() throws IOException {
    EntityDescriptor<?> d = create(EntityInEntity.class);
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      Descriptor<?, ?> fd = f.getDescriptor();
      assertTrue(fd instanceof EntityDescriptor);
      EntityDescriptor<?> ed = (EntityDescriptor<?>) fd;
      assertEquals(EntityInEntity.INNER_ENTITY, ed.getReturnedClass());
      assertEquals(create(EntityInEntity.INNER_ENTITY), fd);
      assertNull(f.getShouldInline());
    }
  }

  @Test
  public void testInlinedEntity() throws IOException {
    EntityDescriptor<?> d = create(User.class);
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertTrue(f.getShouldInline());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInlinedUninlineableEntity1() throws IOException {
    create(Uninlineable1.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInlinedUninlineableEntity2() throws IOException {
    create(Uninlineable2.class);
  }

  @Test
  public void testMap() throws Exception {
    EntityDescriptor<?> d = create(EntityMap.class);
    assertEquals(1, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertTrue(f.getDescriptor() instanceof MapDescriptor);
      MapDescriptor md = (MapDescriptor) f.getDescriptor();
      assertTrue(md.mapDescriptor instanceof EntityDescriptor);
      EntityDescriptor<?> ed = (EntityDescriptor<?>) md.mapDescriptor;
      assertEquals(Email.class, ed.getReturnedClass());
    }
  }

  @Test
  public void testViews() throws Exception {
    EntityDescriptor<?> d = create(MultipleViewEntity.class);
    assertEquals(5, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      if (f.getFieldName().equals("email")) {
        assertTrue(f.isInView("full"));
        assertFalse(f.isInView("simple"));
      } else if (f.getFieldName().equals("motto")) {
        assertTrue(f.isInView("full"));
        assertFalse(f.isInView("simple"));
      } else if (f.getFieldName().equals("name")) {
        assertTrue(f.isInView("full"));
        assertTrue(f.isInView("simple"));
      } else if (f.getFieldName().equals("user")) {
        assertFalse(f.isInView("full"));
        assertTrue(f.isInView("simple"));
      } else if (f.getFieldName().equals("normal")) {
        assertTrue(f.isInView("full"));
        assertTrue(f.isInView("simple"));
      }
    }
  }

  @Ignore("not yet implemented")
  @Test
  public void testBExtendsA() throws IOException {
    EntityDescriptor<?> d = create(B.class);
    // TODO: make the test pass!
    assertEquals(2, d.getFieldDescriptors().size());
    for (FieldDescriptor f : d.getFieldDescriptors()) {
      assertTrue(f.getFieldName().equals("a") || f.getFieldName().equals("b"));
    }
  }

  @Ignore("this is an uncommon case, will implement in next release")
  @Test
  public void testAbisExtendsA() throws IOException {
    create(Abis.class);
    // TODO: make the test pass... what about name classes? field a in Abis and A!
    // this is the least important feature, can implement in v2 only
  }

  private EntityDescriptor<?> create(Class<?> clazz) throws IOException {
    return new DescriptorFactory().create(
        clazz, new DescriptorFactory.EntityDescriptorStore(),
        new HashMap<Type, Class<?>>());
  }
}
