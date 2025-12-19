package edu.rit.dart.art

import akka.actor.typed.ActorRef
import edu.rit.dart.core.Messages.NeuralSignal

/**
 * Message protocol for ART-1 network components
 * All messages extend NeuralSignal from core package
 */
object ARTMessages {
  
  /**
   * Present an input pattern to F1 layer
   * @param pattern Binary input vector (0.0 or 1.0 values)
   * @param replyTo Actor to receive learning result
   */
  final case class PresentPattern(
    pattern: Vector[Double],
    replyTo: Option[ActorRef[LearningResult]] = None
  ) extends NeuralSignal
  
  /**
   * Top-down expectation from F2 to F1
   * @param expectation Expected pattern from category
   */
  final case class TopDownExpectation(
    expectation: Vector[Double]
  ) extends NeuralSignal
  
  /**
   * Bottom-up activation from F1 to F2
   * @param activation Activation vector
   */
  final case class BottomUpActivation(
    activation: Vector[Double]
  ) extends NeuralSignal
  
  /**
   * Request from F2 for category prototype
   * @param categoryId ID of the category
   * @param replyTo Actor to receive the prototype
   */
  final case class GetCategoryPrototype(
    categoryId: Int,
    replyTo: ActorRef[CategoryPrototype]
  ) extends NeuralSignal
  
  /**
   * Response containing category prototype
   */
  final case class CategoryPrototype(
    categoryId: Int,
    weights: Vector[Double]
  ) extends NeuralSignal
  
  /**
   * Winning category selected by F2
   * @param categoryId ID of winning category
   * @param activation Activation strength
   */
  final case class CategorySelected(
    categoryId: Int,
    activation: Double
  ) extends NeuralSignal
  
  /**
   * Match/mismatch signal from orienting subsystem
   * @param matchDegree How well input matches expectation (0.0 to 1.0)
   * @param vigilance Vigilance threshold
   * @param reset True if reset needed (matchDegree < vigilance)
   */
  final case class MatchSignal(
    matchDegree: Double,
    vigilance: Double,
    reset: Boolean
  ) extends NeuralSignal
  
  /**
   * trigger for orienting subsystem to check match
   */
  final case class CheckMatch(
    input: Vector[Double],
    expectation: Vector[Double]
  ) extends NeuralSignal
  
  /**
   * Update category weights (learning)
   * @param categoryId Category to update
   * @param newWeights Updated weight vector
   */
  final case class UpdateWeights(
    categoryId: Int,
    newWeights: Vector[Double]
  ) extends NeuralSignal
  
  /**
   * Result of pattern presentation (learning/recognition)
   * @param categoryId Which category was selected
   * @param isNewCategory True if new category was created
   * @param matchDegree Final match degree
   */
  final case class LearningResult(
    categoryId: Int,
    isNewCategory: Boolean,
    matchDegree: Double
  ) extends NeuralSignal
  
  /**
   * Reset current category and search for new one
   * @param inhibitCategory Category to inhibit during search
   */
  final case class ResetCategory(
    inhibitCategory: Int
  ) extends NeuralSignal
  
  /**
   * Query network state
   */
  case object GetNetworkState extends NeuralSignal
  
  /**
   * Network state response
   * @param numCategories Number of learned categories
   * @param vigilance Current vigilance parameter
   */
  final case class NetworkState(
    numCategories: Int,
    vigilance: Double
  ) extends NeuralSignal
}
