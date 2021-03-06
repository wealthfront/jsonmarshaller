package com.twolattes.json;

import static com.twolattes.json.BigDecimalDescriptor.BIG_DECIMAL_DESC;
import static com.twolattes.json.BooleanDescriptor.BOOLEAN_DESC;
import static com.twolattes.json.BooleanDescriptor.BOOLEAN_LITERAL_DESC;
import static com.twolattes.json.ByteDescriptor.BYTE_DESC;
import static com.twolattes.json.ByteDescriptor.BYTE_LITERAL_DESC;
import static com.twolattes.json.CharacterDescriptor.CHARARACTER_DESC;
import static com.twolattes.json.CharacterDescriptor.CHAR_DESC;
import static com.twolattes.json.DoubleDescriptor.DOUBLE_DESC;
import static com.twolattes.json.DoubleDescriptor.DOUBLE_LITERAL_DESC;
import static com.twolattes.json.FloatDescriptor.FLOAT_DESC;
import static com.twolattes.json.FloatDescriptor.FLOAT_LITERAL_DESC;
import static com.twolattes.json.IntegerDescriptor.INTEGER_DESC;
import static com.twolattes.json.IntegerDescriptor.INT_DESC;
import static com.twolattes.json.LongDescriptor.LONG_DESC;
import static com.twolattes.json.LongDescriptor.LONG_LITERAL_DESC;
import static com.twolattes.json.ShortDescriptor.SHORT_DESC;
import static com.twolattes.json.ShortDescriptor.SHORT_LITERAL_DESC;
import static com.twolattes.json.StringDescriptor.STRING_DESC;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.signature.SignatureVisitor;

import com.twolattes.json.DescriptorFactory.EntityDescriptorStore;
import com.twolattes.json.types.JsonType;

/**
 * An entity signature visitor capable of converting a signature (such as
 * {@code Ljava/lang/String;}) into a descriptor.
 */
class EntitySignatureVisitor implements SignatureVisitor {
  public enum State {
    base, entity, collection, map, array;
  }

  private static final Map<Character, Descriptor<?, ?>> baseTypes = new HashMap<Character, Descriptor<?, ?>>();

  private static final Map<String, Descriptor<?, ?>> baseObjectTypes = new HashMap<String, Descriptor<?, ?>>();
  static {
    baseTypes.put('I', INT_DESC);
    baseTypes.put('D', DOUBLE_LITERAL_DESC);
    baseTypes.put('S', SHORT_LITERAL_DESC);
    baseTypes.put('C', CHAR_DESC);
    baseTypes.put('J', LONG_LITERAL_DESC);
    baseTypes.put('Z', BOOLEAN_LITERAL_DESC);
    baseTypes.put('F', FLOAT_LITERAL_DESC);
    baseTypes.put('B', BYTE_LITERAL_DESC);

    putBaseObjectTypes(String.class, STRING_DESC);
    putBaseObjectTypes(Integer.class, INTEGER_DESC);
    putBaseObjectTypes(Double.class, DOUBLE_DESC);
    putBaseObjectTypes(Short.class, SHORT_DESC);
    putBaseObjectTypes(Character.class, CHARARACTER_DESC);
    putBaseObjectTypes(Long.class, LONG_DESC);
    putBaseObjectTypes(Boolean.class, BOOLEAN_DESC);
    putBaseObjectTypes(Float.class, FLOAT_DESC);
    putBaseObjectTypes(Byte.class, BYTE_DESC);
    putBaseObjectTypes(BigDecimal.class, BIG_DECIMAL_DESC);
  }

  private static void putBaseObjectTypes(Class<?> klass, Descriptor<?, ?> desc) {
    baseObjectTypes.put(klass.getName().replace('.', '/'), desc);
  }

  private Descriptor<?, ?> descriptor;

  private State state = State.base;

  private List<EntitySignatureVisitor> next = new ArrayList<EntitySignatureVisitor>(
      2);

  private Class<? extends Collection<?>> collectionType;

  private Class<? extends Map<?, ?>> mapClass;

  private final EntityDescriptorStore store;

  private final String signature;

  private final FieldDescriptor fieldDescriptor;

  private final Map<Type, Class<?>> types;

  private Entity entity;

  EntitySignatureVisitor(String signature, EntityDescriptorStore store,
      FieldDescriptor fieldDescriptor, Map<Type, Class<?>> types) {
    this.signature = signature;
    this.store = store;
    this.fieldDescriptor = fieldDescriptor;
    this.types = types;
  }

  public void visitBaseType(char t) {
    descriptor = baseTypes.get(t);
  }

  @SuppressWarnings("unchecked")
  public void visitClassType(String className) {
    Descriptor<?, ?> d = baseObjectTypes.get(className);
    if (d != null) {
      descriptor = d;
    } else {
      try {
        Class<?> c = Class.forName(className.replace('/', '.'));
        if (types.containsKey(c)) {
          descriptor = new UserTypeDescriptor(
              (JsonType) Instantiator.newInstance(types.get(c)));
        } else if (c.isEnum()) {
          state = State.base;
          if (fieldDescriptor.useOrdinal()) {
            descriptor = new EnumOrdinalDescriptor((Class<? extends Enum>) c);
          } else {
            descriptor = new EnumNameDescriptor((Class<? extends Enum>) c);
          }
        } else if (Collection.class.isAssignableFrom(c)) {
          state = State.collection;
          collectionType = (Class<? extends Collection<?>>) c;
        } else if (Map.class.isAssignableFrom(c)) {
          state = State.map;
          mapClass = (Class<? extends Map<?, ?>>) c;
        } else {
          state = State.entity;
          entity = c.getAnnotation(Entity.class);
          if (store.contains(c)) {
            descriptor = new ProxyEntityDescriptor(c, store);
          } else {
            descriptor = new DescriptorFactory().create(c, store, types).left;
          }
        }
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(className + " is not loadable");
      } catch (IOException e) {
        throw new IllegalArgumentException(className + " is not readable");
      }
    }
  }

  public SignatureVisitor visitArrayType() {
    state = State.array;
    return nextSignatureVisitor();
  }

  public SignatureVisitor visitTypeArgument(char variance) {
    if (SUPER == variance) {
      /*
       * We process only covariant structures, encountering a contravariant
       * marker indicates that the entity is incorrectly specified. If we had
       * List<? super A> then it might as if we had List<Object>.
       */
      throw new IllegalArgumentException("collections contravariant");
    }

    return nextSignatureVisitor();
  }

  private SignatureVisitor nextSignatureVisitor() {
    EntitySignatureVisitor sv = new EntitySignatureVisitor(
        null, store, fieldDescriptor, types);
    next.add(sv);
    return sv;
  }

  @SuppressWarnings("unchecked")
  public Pair<Descriptor, Entity> getDescriptor() {
    if (descriptor == null) {
      switch (state) {
      case collection:
        if (next.size() == 0) {
          throw new IllegalArgumentException(
              "Collection must be parameterized, e.g. List<String>. "
                  + "Signature " + signature);
        } else {
          return Pair.<Descriptor, Entity> of(
              new CollectionDescriptor(collectionType,
                  inlineEntityIfNecessary(next.get(0).getDescriptor())),
                  null);
        }

      case array:
        return Pair.<Descriptor, Entity> of(
            new ArrayDescriptor(
                inlineEntityIfNecessary(next.get(0).getDescriptor())),
                null);

      case map:
        if (next.size() == 2
            && next.get(0).getDescriptor().left.getReturnedClass().equals(
                String.class)) {
          return Pair.<Descriptor, Entity> of(
              new MapDescriptor(
                  MapType.fromClass(mapClass),
                  inlineEntityIfNecessary(next.get(1).getDescriptor())),
              null);
        } else {
          throw new IllegalArgumentException("Map<String, ...> must be used. "
              + "Signature " + signature);
        }
      }
    }
    return Pair.<Descriptor, Entity> of(descriptor, entity);
  }

  public SignatureVisitor visitClassBound() {
    throw new UnsupportedOperationException("visitClassBound");
  }

  public void visitEnd() {
  }

  public SignatureVisitor visitExceptionType() {
    throw new IllegalStateException();
  }

  public void visitFormalTypeParameter(String type) {
    throw new IllegalStateException();
  }

  public void visitInnerClassType(String innerClassType) {
    throw new IllegalStateException();
  }

  public SignatureVisitor visitInterface() {
    throw new IllegalStateException();
  }

  public SignatureVisitor visitInterfaceBound() {
    throw new IllegalStateException();
  }

  public SignatureVisitor visitParameterType() {
    throw new IllegalStateException();
  }

  public SignatureVisitor visitReturnType() {
    throw new IllegalStateException();
  }

  public SignatureVisitor visitSuperclass() {
    return this;
  }

  public void visitTypeArgument() {
    throw new IllegalStateException();
  }

  public void visitTypeVariable(String type) {
    throw new IllegalArgumentException(
        "cannot create a marshaller for parametrized types due to erasure");
  }

  @SuppressWarnings("unchecked")
  private Descriptor inlineEntityIfNecessary(Pair<Descriptor, Entity> pair) {
    return pair.left instanceof EntityDescriptor && pair.right != null && pair.right.inline() ?
        new InlinedEntityDescriptor((EntityDescriptor) pair.left) :
        pair.left;
  }

}
