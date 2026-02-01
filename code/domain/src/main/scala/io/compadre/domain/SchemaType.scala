package io.compadre.domain

sealed trait SchemaType

case class PrimitiveType(dataType: PrimitiveDataType) extends SchemaType

case class ObjectType(fields: Map[String, Field]) extends SchemaType

case class ArrayType(elementType: SchemaType) extends SchemaType

object ObjectType {
  val Empty: ObjectType = ObjectType(Map.empty)
}

case class Field(
  schemaType: SchemaType,
  isRequired: Boolean,
  description: Option[String] = None
)

enum PrimitiveDataType {
  case String, Boolean, Int, Long, Double
}
