package io.compadre.domain

import java.time.Instant

object TestSchemas {

  val emptySchemaLog: SchemaLog = SchemaLog(List.empty)

  // Example: Tomato schema evolution
  val tomatoSchemaLog: SchemaLog = SchemaLog(
    changes = List(
      // v1.0.0 - Initial schema
      SchemaChange(
        timestamp = Instant.parse("2024-01-01T00:00:00Z"),
        version = "1.0.0",
        operations = List(
          AddField(
            path = FieldPath.Root / "id",
            schemaType = PrimitiveType(PrimitiveDataType.Int),
            isRequired = true,
            description = Some("Unique identifier")
          ),
          AddField(
            path = FieldPath.Root / "name",
            schemaType = PrimitiveType(PrimitiveDataType.String),
            isRequired = true,
            description = Some("Name of the tomato variety")
          ),
          AddField(
            path = FieldPath.Root / "color",
            schemaType = PrimitiveType(PrimitiveDataType.String),
            isRequired = false,
            description = Some("Primary color")
          )
        )
      ),
      // v1.1.0 - Add optional field
      SchemaChange(
        timestamp = Instant.parse("2024-02-01T00:00:00Z"),
        version = "1.1.0",
        operations = List(
          AddField(
            path = FieldPath.Root / "weight",
            schemaType = PrimitiveType(PrimitiveDataType.Double),
            isRequired = false,
            description = Some("Weight in grams")
          )
        )
      ),
      // v2.0.0 - Make color required (breaking change)
      SchemaChange(
        timestamp = Instant.parse("2024-03-01T00:00:00Z"),
        version = "2.0.0",
        operations = List(
          MakeRequired(path = FieldPath.Root / "color")
        )
      )
    )
  )

}
