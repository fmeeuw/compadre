package io.compadre.registry

import io.compadre.domain.*
import io.compadre.compatibility.*

/**
 * Validates schema changes and service deployments.
 */
object ValidationService {

  sealed trait ValidationResult
  case object Allowed extends ValidationResult
  case class Rejected(reason: String, details: List[String] = Nil) extends ValidationResult
  case class Warning(message: String, details: List[String] = Nil) extends ValidationResult

  /**
   * Validate a new schema change before it's added.
   *
   * Checks if existing consumers can handle the new version.
   */
  def validateSchemaChange(
    schemaLog: SchemaLog,
    newChange: SchemaChange,
    consumers: List[ConsumerRegistration]
  ): ValidationResult = {
    // TODO: Implement
    // - Check if operations are breaking
    // - Check if consumers can translate from their version to new version
    Allowed
  }

  /**
   * Validate a producer deployment.
   *
   * Checks if all consumers can handle the producer's version.
   */
  def validateProducerDeploy(
    producer: ProducerRegistration,
    consumers: List[ConsumerRegistration],
    topicVersions: Set[String]
  ): ValidationResult = {
    // TODO: Implement
    // - Check if all consumers support the producer's version
    Allowed
  }

  /**
   * Validate a consumer deployment.
   *
   * Checks if consumer can handle all versions present in the topic.
   */
  def validateConsumerDeploy(
    consumer: ConsumerRegistration,
    producers: List[ProducerRegistration],
    topicVersions: Set[String],
    schemaLog: SchemaLog
  ): ValidationResult = {
    // TODO: Implement
    // - Check if consumer can read all versions in topic
    // - Check if consumer can read all active producer versions
    Allowed
  }

}
