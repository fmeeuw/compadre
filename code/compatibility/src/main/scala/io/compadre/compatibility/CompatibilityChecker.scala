package io.compadre.compatibility

import io.compadre.domain.*

/**
 * Checks compatibility between schema versions.
 *
 * Determines if a consumer at version X can read data produced at version Y.
 */
object CompatibilityChecker {

  sealed trait CompatibilityResult
  case object Compatible extends CompatibilityResult
  case class Incompatible(reasons: List[IncompatibilityReason]) extends CompatibilityResult

  sealed trait IncompatibilityReason
  case class RequiredFieldMissing(path: FieldPath) extends IncompatibilityReason
  case class FieldTypeChanged(path: FieldPath, from: SchemaType, to: SchemaType) extends IncompatibilityReason

  /**
   * Check if a consumer can read data from a producer.
   *
   * @param producerSchema The schema the producer writes
   * @param consumerSchema The schema the consumer expects
   * @return Compatible if consumer can read producer's data, Incompatible with reasons otherwise
   */
  def canRead(producerSchema: Schema, consumerSchema: Schema): CompatibilityResult = {
    // TODO: Implement compatibility checking
    Compatible
  }

  /**
   * Classify an operation's compatibility impact.
   */
  def classifyOperation(operation: SchemaOperation): OperationCompatibility = {
    operation match
      case AddField(_, _, isRequired, _) =>
        if (isRequired) OperationCompatibility.ConsumerFirst
        else OperationCompatibility.FullyCompatible

      case RemoveField(_) =>
        OperationCompatibility.ProducerFirst

      case RenameField(_, _) =>
        OperationCompatibility.Breaking

      case MakeOptional(_) =>
        OperationCompatibility.FullyCompatible

      case MakeRequired(_) =>
        OperationCompatibility.ConsumerFirst
  }

  enum OperationCompatibility {
    case FullyCompatible  // Safe in any order
    case ConsumerFirst    // Consumers must upgrade before producers
    case ProducerFirst    // Producers must upgrade before consumers
    case Breaking         // Requires coordinated deployment
  }

}
