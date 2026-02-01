package io.compadre.codegen

import io.compadre.domain.*

/**
 * Generates Scala case classes from schema definitions.
 */
object ScalaCodeGenerator {

  case class GeneratedCode(
    packageName: String,
    fileName: String,
    content: String
  )

  /**
   * Generate a case class for a specific schema version.
   */
  def generateForVersion(
    schemaName: String,
    schema: Schema,
    packageName: String
  ): GeneratedCode = {
    val className = toClassName(schemaName, schema.version)
    val fields = generateFields(schema.root)

    val content =
      s"""package $packageName
         |
         |case class $className(
         |$fields
         |)
         |""".stripMargin

    GeneratedCode(packageName, s"$className.scala", content)
  }

  /**
   * Generate a union type for multiple versions (for consumers).
   */
  def generateReaderType(
    schemaName: String,
    versions: List[Schema],
    packageName: String
  ): GeneratedCode = {
    val typeNames = versions.map(s => toClassName(schemaName, s.version))
    val unionType = typeNames.mkString(" | ")
    val readerName = s"${toPascalCase(schemaName)}Reader"

    val content =
      s"""package $packageName
         |
         |type $readerName = $unionType
         |""".stripMargin

    GeneratedCode(packageName, s"$readerName.scala", content)
  }

  private def toClassName(schemaName: String, version: String): String = {
    val name = toPascalCase(schemaName)
    val ver = version.replace(".", "_")
    s"${name}V$ver"
  }

  private def toPascalCase(s: String): String = {
    s.split("[-_]").map(_.capitalize).mkString
  }

  private def generateFields(obj: ObjectType, indent: Int = 2): String = {
    val spaces = " " * indent
    obj.fields.map { case (name, field) =>
      val scalaType = toScalaType(field.schemaType, field.isRequired)
      s"$spaces$name: $scalaType"
    }.mkString(",\n")
  }

  private def toScalaType(schemaType: SchemaType, isRequired: Boolean): String = {
    val baseType = schemaType match
      case PrimitiveType(dataType) => dataType match
        case PrimitiveDataType.String => "String"
        case PrimitiveDataType.Boolean => "Boolean"
        case PrimitiveDataType.Int => "Int"
        case PrimitiveDataType.Long => "Long"
        case PrimitiveDataType.Double => "Double"
      case ObjectType(fields) => "/* nested object */"  // TODO: Handle nested
      case ArrayType(elementType) =>
        s"List[${toScalaType(elementType, isRequired = true)}]"

    if (isRequired) baseType else s"Option[$baseType]"
  }

}
