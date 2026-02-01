package io.compadre.registry

import io.compadre.domain.*
import io.compadre.compatibility.*

/**
 * Central schema registry that tracks:
 * - Schema definitions (logs of operations)
 * - Producers and which version they write
 * - Consumers and which versions they support
 * - Topic version information
 */
trait SchemaRegistry {

  // Schema management
  def registerSchema(name: String, schemaLog: SchemaLog): Either[RegistryError, Unit]
  def getSchema(name: String): Either[RegistryError, SchemaLog]
  def addSchemaChange(name: String, change: SchemaChange): Either[RegistryError, Unit]

  // Producer registration
  def registerProducer(producer: ProducerRegistration): Either[RegistryError, Unit]
  def getProducers(schemaName: String): List[ProducerRegistration]

  // Consumer registration
  def registerConsumer(consumer: ConsumerRegistration): Either[RegistryError, Unit]
  def getConsumers(schemaName: String): List[ConsumerRegistration]

  // Topic version tracking
  def getTopicVersions(topic: String): Set[String]
  def updateTopicVersions(topic: String, versions: Set[String]): Unit

}

case class ProducerRegistration(
  serviceName: String,
  schemaName: String,
  topic: String,
  writesVersion: String
)

case class ConsumerRegistration(
  serviceName: String,
  schemaName: String,
  topic: String,
  readsVersion: String
)

sealed trait RegistryError
case class SchemaNotFound(name: String) extends RegistryError
case class SchemaAlreadyExists(name: String) extends RegistryError
case class ValidationFailed(reasons: List[String]) extends RegistryError
