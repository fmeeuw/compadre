package io.compadre.domain

import java.time.Instant

case class SchemaLog(changes: List[SchemaChange])
case class SchemaChange(
    timestamp: Instant,
    version: String,
    operations: List[SchemaOperation]
)
