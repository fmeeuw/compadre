package io.compadre.domain

import java.time.Instant

case class Schema(timestamp: Instant, version: String, root: ObjectType)
