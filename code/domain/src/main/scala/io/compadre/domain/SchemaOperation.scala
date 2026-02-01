package io.compadre.domain

sealed trait SchemaOperation

case class AddField(
  path: FieldPath,
  schemaType: SchemaType,
  isRequired: Boolean,
  description: Option[String] = None
) extends SchemaOperation

case class RemoveField(path: FieldPath) extends SchemaOperation

case class RenameField(path: FieldPath, newName: String) extends SchemaOperation

case class MakeOptional(path: FieldPath) extends SchemaOperation

case class MakeRequired(path: FieldPath) extends SchemaOperation
