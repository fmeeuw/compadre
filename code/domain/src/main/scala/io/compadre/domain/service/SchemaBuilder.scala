package io.compadre.domain.service

import io.compadre.domain.*

object SchemaBuilder {

  sealed trait BuildError
  case class FieldAlreadyExists(path: FieldPath, name: String) extends BuildError
  case class FieldNotFound(path: FieldPath, name: String) extends BuildError
  case class PathNotFound(path: FieldPath) extends BuildError
  case class NotAnObject(path: FieldPath) extends BuildError
  case class InvalidPath(path: FieldPath, reason: String) extends BuildError
  case class VersionNotFound(version: String) extends BuildError
  case object EmptySchemaLog extends BuildError

  def buildSchema(schemaLog: SchemaLog, targetVersion: String): Either[BuildError, Schema] = {
    val relevantChanges = schemaLog.changes.filter(_.version <= targetVersion)
    if (relevantChanges.isEmpty) {
      Left(EmptySchemaLog)
    } else {
      val lastChange = relevantChanges.findLast(_.version == targetVersion)
        .orElse(relevantChanges.lastOption)
        .get

      relevantChanges
        .foldLeft[Either[BuildError, ObjectType]](Right(ObjectType.Empty)) {
          case (schemaEither, change) =>
            schemaEither.flatMap(schema => applyChange(schema, change))
        }
        .map(root => Schema(lastChange.timestamp, lastChange.version, root))
    }
  }

  def buildLatestSchema(schemaLog: SchemaLog): Either[BuildError, Schema] = {
    schemaLog.changes.lastOption match
      case None => Left(EmptySchemaLog)
      case Some(lastChange) => buildSchema(schemaLog, lastChange.version)
  }

  def applyChange(schema: ObjectType, change: SchemaChange): Either[BuildError, ObjectType] = {
    change.operations.foldLeft[Either[BuildError, ObjectType]](Right(schema)) {
      case (schemaEither, operation) =>
        schemaEither.flatMap(s => applyOperation(s, operation))
    }
  }

  def applyOperation(schema: ObjectType, operation: SchemaOperation): Either[BuildError, ObjectType] = {
    operation match
      case AddField(path, schemaType, isRequired, description) =>
        path.name match
          case None => Left(InvalidPath(path, "AddField requires a non-root path"))
          case Some(name) => addField(schema, path.parent, name, Field(schemaType, isRequired, description))

      case RemoveField(path) =>
        path.name match
          case None => Left(InvalidPath(path, "RemoveField requires a non-root path"))
          case Some(name) => removeField(schema, path.parent, name)

      case RenameField(path, newName) =>
        path.name match
          case None => Left(InvalidPath(path, "RenameField requires a non-root path"))
          case Some(oldName) => renameField(schema, path.parent, oldName, newName)

      case MakeOptional(path) =>
        path.name match
          case None => Left(InvalidPath(path, "MakeOptional requires a non-root path"))
          case Some(name) => updateField(schema, path.parent, name)(_.copy(isRequired = false))

      case MakeRequired(path) =>
        path.name match
          case None => Left(InvalidPath(path, "MakeRequired requires a non-root path"))
          case Some(name) => updateField(schema, path.parent, name)(_.copy(isRequired = true))
  }

  private def addField(
    schema: ObjectType,
    path: FieldPath,
    name: String,
    field: Field
  ): Either[BuildError, ObjectType] = {
    if (path.isRoot) {
      if (schema.fields.contains(name)) {
        Left(FieldAlreadyExists(path, name))
      } else {
        Right(ObjectType(schema.fields + (name -> field)))
      }
    } else {
      navigateAndUpdate(schema, path) {
        case obj: ObjectType if obj.fields.contains(name) =>
          Left(FieldAlreadyExists(path, name))
        case obj: ObjectType =>
          Right(ObjectType(obj.fields + (name -> field)))
      }
    }
  }

  private def removeField(
    schema: ObjectType,
    path: FieldPath,
    name: String
  ): Either[BuildError, ObjectType] = {
    if (path.isRoot) {
      if (!schema.fields.contains(name)) {
        Left(FieldNotFound(path, name))
      } else {
        Right(ObjectType(schema.fields - name))
      }
    } else {
      navigateAndUpdate(schema, path) {
        case obj: ObjectType if !obj.fields.contains(name) =>
          Left(FieldNotFound(path, name))
        case obj: ObjectType =>
          Right(ObjectType(obj.fields - name))
      }
    }
  }

  private def renameField(
    schema: ObjectType,
    path: FieldPath,
    oldName: String,
    newName: String
  ): Either[BuildError, ObjectType] = {
    if (path.isRoot) {
      schema.fields.get(oldName) match
        case None => Left(FieldNotFound(path, oldName))
        case Some(field) =>
          if (schema.fields.contains(newName)) {
            Left(FieldAlreadyExists(path, newName))
          } else {
            Right(ObjectType(schema.fields - oldName + (newName -> field)))
          }
    } else {
      navigateAndUpdate(schema, path) {
        case obj: ObjectType =>
          obj.fields.get(oldName) match
            case None => Left(FieldNotFound(path, oldName))
            case Some(field) =>
              if (obj.fields.contains(newName)) {
                Left(FieldAlreadyExists(path, newName))
              } else {
                Right(ObjectType(obj.fields - oldName + (newName -> field)))
              }
      }
    }
  }

  private def updateField(
    schema: ObjectType,
    path: FieldPath,
    name: String
  )(update: Field => Field): Either[BuildError, ObjectType] = {
    if (path.isRoot) {
      schema.fields.get(name) match
        case None => Left(FieldNotFound(path, name))
        case Some(field) =>
          Right(ObjectType(schema.fields + (name -> update(field))))
    } else {
      navigateAndUpdate(schema, path) {
        case obj: ObjectType =>
          obj.fields.get(name) match
            case None => Left(FieldNotFound(path, name))
            case Some(field) =>
              Right(ObjectType(obj.fields + (name -> update(field))))
      }
    }
  }

  private def navigateAndUpdate(
    schema: ObjectType,
    path: FieldPath
  )(update: ObjectType => Either[BuildError, ObjectType]): Either[BuildError, ObjectType] = {
    def navigate(
      current: ObjectType,
      remaining: List[String],
      visited: List[String]
    ): Either[BuildError, ObjectType] = {
      remaining match
        case Nil =>
          update(current).map { updated =>
            rebuildPath(schema, visited, updated)
          }
        case segment :: rest =>
          current.fields.get(segment) match
            case None =>
              Left(PathNotFound(FieldPath(visited :+ segment)))
            case Some(Field(obj: ObjectType, _, _)) =>
              navigate(obj, rest, visited :+ segment)
            case Some(_) =>
              Left(NotAnObject(FieldPath(visited :+ segment)))
    }

    navigate(schema, path.segments, Nil)
  }

  private def rebuildPath(
    root: ObjectType,
    path: List[String],
    updated: ObjectType
  ): ObjectType = {
    path match
      case Nil => updated
      case segment :: rest =>
        val child = root.fields(segment)
        val updatedChild = child.copy(
          schemaType = rebuildPath(
            child.schemaType.asInstanceOf[ObjectType],
            rest,
            updated
          )
        )
        ObjectType(root.fields + (segment -> updatedChild))
  }

}
