package com.twolattes.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Preconditions;

/**
 * An entity descriptor.
 */
final class ConcreteEntityDescriptor<T> extends AbstractDescriptor<T, Object>
    implements EntityDescriptor<T> {

  private final Class<T> entity;
  private final Set<FieldDescriptor> fieldDescriptors;
  private final boolean shouldInline;
  private final String discriminator;
  private final ConcreteEntityDescriptor<?> parent;
  private final Constructor<T> constructor;

  /**
   * Creates an entity descriptor.
   * @param entity the described entity's type
   * @param fieldDescriptors the entity's field descriptors
   */
  @SuppressWarnings("unchecked")
  ConcreteEntityDescriptor(Class<T> entity,
      Set<FieldDescriptor> fieldDescriptors,
      boolean shouldInline,
      ConcreteEntityDescriptor<?> parent) {
    super(entity);
    Entity annotation = entity.getAnnotation(Entity.class);
    this.discriminator = annotation.discriminator();
    this.entity = entity;
    this.fieldDescriptors = fieldDescriptors;
    this.shouldInline = shouldInline;
    this.parent = parent;

    // implementation of the entity
    Class<?> implementedBy = annotation.implementedBy();
    if (!implementedBy.equals(Object.class)) {
      if (!entity.isAssignableFrom(implementedBy)) {
        throw new IllegalArgumentException(entity.toString() +
            "'s implementedBy must reference a subclass.");
      }
    } else {
      implementedBy = entity;
    }

    // getting the no arg constructor and making it accessible
    try {
      this.constructor = (Constructor<T>) implementedBy.getDeclaredConstructor();
      this.constructor.setAccessible(true);
    } catch (SecurityException e) {
      throw new IllegalArgumentException(
          implementedBy + "'s constructor cannot be accessed.");
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          implementedBy + " does not have a no argument constructor.");
    }

    // name conflicts
    Set<String> names = new HashSet<String>();
    parent = this;
    while (parent != null) {
      for (FieldDescriptor descriptor : parent.fieldDescriptors) {
        if (names.contains(descriptor.getJsonName())) {
          throw new IllegalArgumentException(
              "Field with JSON name " + descriptor.getJsonName() + " in " +
              entity + " collides with field in class " +
              parent.getReturnedClass());
        } else {
          names.add(descriptor.getJsonName());
        }
      }
      parent = parent.parent;
    }
  }

  @Override
  public Class<?> getReturnedClass() {
    return entity;
  }

  /**
   * Gets the field descriptors of this entity.
   */
  public Set<FieldDescriptor> getFieldDescriptors() {
    return fieldDescriptors;
  }

  @Override
  public boolean isInlineable() {
    return fieldDescriptors.size() == 1;
  }

  public JSONObject marshall(Object entity) {
    return marshall(entity, null);
  }

  @SuppressWarnings("unchecked")
  public JSONObject marshall(Object entity, String view) {
    if (entity == null) {
      return JSONObject.NULL;
    }

    // entity is not null
    JSONObject jsonObject = new JSONObject();
    try {
      marshallFields(entity, view, jsonObject);
      return jsonObject;
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A helper method to place an entities fields in a {@link JSONObject} based
   * on its descriptor.
   */
  @SuppressWarnings("unchecked")
  private void marshallFields(
      Object entity, String view, JSONObject jsonObject)
      throws JSONException {
    if (parent != null) {
      parent.marshallFields(entity, view, jsonObject);
    }
    for (FieldDescriptor d : getFieldDescriptors()) {
      if (d.isInView(view)) {
        Object fieldValue = d.getFieldValue(entity);
        if (!(d.isOptional() && fieldValue == null)) {
          String jsonName = d.getJsonName();
          Descriptor descriptor = d.getDescriptor();
          if (d.getShouldInline() == null) {
            if (descriptor.shouldInline()) {
              jsonObject.put(jsonName, descriptor.marshallInline(fieldValue, view));
            } else {
              jsonObject.put(jsonName, descriptor.marshall(fieldValue, view));
            }
          } else if (d.getShouldInline()) {
            jsonObject.put(jsonName, descriptor.marshallInline(fieldValue, view));
          } else {
            jsonObject.put(jsonName, descriptor.marshall(fieldValue, view));
          }
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object marshallInline(Object entity, String view) {
    Preconditions.checkState(isInlineable());
    if (entity == null) {
      return JSONObject.NULL;
    }
    FieldDescriptor d = fieldDescriptors.iterator().next();
    Descriptor descriptor = d.getDescriptor();
    if (d.getShouldInline() == null) {
      if (descriptor.shouldInline()) {
        return descriptor.marshallInline(d.getFieldValue(entity), view);
      } else {
        return descriptor.marshall(d.getFieldValue(entity), view);
      }
    } else if (d.getShouldInline()) {
      return descriptor.marshallInline(d.getFieldValue(entity), view);
    } else {
      return descriptor.marshall(d.getFieldValue(entity), view);
    }
  }

  public T unmarshall(Object object) {
    return unmarshall(object, null);
  }

  @SuppressWarnings("unchecked")
  public T unmarshall(Object object, String view) {
    if (JSONObject.NULL.equals(object)) {
      return null;
    }
    try {
      JSONObject jsonObject = (JSONObject) object;
      // is this a polymorphic entity?
      Object entity = constructor.newInstance();
      unmarshallFields(jsonObject, entity, view);
      return (T) entity;
    } catch (InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    } catch (SecurityException e) {
      throw new IllegalStateException(e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A helper method to populate an entity's fields based on a
   * {@link JSONObject} and its descriptor.
   *
   * This function MUST stay private.
   */
  @SuppressWarnings("unchecked")
  private void unmarshallFields(JSONObject jsonObject, Object entity, String view) throws JSONException {
    if (parent != null) {
      parent.unmarshallFields(jsonObject, entity, view);
    }
    for (FieldDescriptor d : getFieldDescriptors()) {
      if (jsonObject.has(d.getJsonName())) {
        if (d.isInView(view)) {
          Descriptor descriptor = d.getDescriptor();
          if (d.getShouldInline() == null) {
            if (descriptor.shouldInline()) {
              d.setFieldValue(entity,
                  descriptor.unmarshallInline(jsonObject.get(d.getJsonName()), view));
            } else {
              d.setFieldValue(entity,
                  descriptor.unmarshall(jsonObject.get(d.getJsonName()), view));
            }
          } else if (d.getShouldInline()) {
            d.setFieldValue(entity,
                descriptor.unmarshallInline(jsonObject.get(d.getJsonName()), view));
          } else {
            d.setFieldValue(entity,
                descriptor.unmarshall(jsonObject.get(d.getJsonName()), view));
          }
        }
      } else {
        if (d.isInView(view) && !d.isOptional()) {
          if (view == null) {
            throw new IllegalStateException("The field " + d.getFieldName() +
                " whose JSON name is " + d.getJsonName() + " has no value. " +
                "If this field is optional, use the @Value(optional = true)" +
                " annotations.");
          } else {
            throw new IllegalStateException("The field " + d.getFieldName() +
                " (in the view " + view +") whose JSON" +
                " name is " + d.getJsonName() + " has no value. If this " +
                "field is optional, use the @Value(optional = true) " +
                "annotations.");
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T unmarshallInline(Object entity, String view) {
    Preconditions.checkState(isInlineable());
    if (JSONObject.NULL.equals(entity)) {
      return null;
    } else {
      JSONObject jsonObject = new JSONObject();
      try {
    	FieldDescriptor d = fieldDescriptors.iterator().next();
        jsonObject.put(d.getJsonName(), entity);
        return unmarshall(jsonObject);
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof EntityDescriptor) {
      EntityDescriptor<?> that = (EntityDescriptor<?>) obj;
      for (FieldDescriptor fd : this.getFieldDescriptors()) {
        if (!that.getFieldDescriptors().contains(fd)) {
          return false;
        }
      }
      return
        this.getFieldDescriptors().size() == that.getFieldDescriptors().size() &&
        this.getReturnedClass().equals(that.getReturnedClass());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getReturnedClass().hashCode();
  }

  @Override
  public String toString() {
    String s = "EntityDescriptor {\n  " + getReturnedClass().getName() + "\n";
    for (FieldDescriptor f : fieldDescriptors) {
      s += "  " + f.toString() + "\n";
    }
    return s + "}";
  }

  @Override
  public boolean shouldInline() {
    return shouldInline;
  }

  public String getDiscriminator() {
    if (discriminator == null || discriminator.length() == 0) {
      throw new IllegalArgumentException(
          "The discriminator option is not defined on this entity: " +
          getReturnedClass());
    } else {
      return discriminator;
    }
  }

}
