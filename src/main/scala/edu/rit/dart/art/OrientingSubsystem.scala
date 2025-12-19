package edu.rit.dart.art

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.core.Messages._
import ARTMessages._

/**
 * Orienting Subsystem for ART-1
 * 
 * The Orienting Subsystem implements the vigilance test that governs the
 * stability-plasticity dilemma in ART.
 * 
 * Key Function:
 * - Computes match degree between input and top-down expectation
 * - Compares match to vigilance parameter (ρ)
 * - Sends reset signal if match < ρ (template mismatch)
 * - Allows learning if match >= ρ (resonance)
 * 
 * Vigilance Parameter (ρ):
 * - Low ρ (e.g., 0.3): Coarse categories (more generalization, less plasticity)
 * - High ρ (e.g., 0.9): Fine categories (less generalization, more plasticity)
 * 
 * Match Criterion:
 *   match = |input ∧ expectation| / |input|
 * 
 * This is the proportion of input features confirmed by the expectation.
 */
object OrientingSubsystem {
  
  final case class Config(
    subsystemId: String,
    vigilance: Double, // ρ parameter (0.0 to 1.0)
    f1Layer: ActorRef[NeuralSignal],
    f2Layer: ActorRef[NeuralSignal]
  ) {
    require(vigilance >= 0.0 && vigilance <= 1.0, s"Vigilance must be in [0,1], got $vigilance")
  }
  
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      context.log.info(
        s"OrientingSubsystem ${config.subsystemId} initialized (vigilance ρ=${config.vigilance})"
      )
      running(config)
    }
  }
  
  private def running(config: Config): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case CheckMatch(input, expectation) =>
          // Compute match degree
          val matchDegree = computeMatchDegree(input, expectation)
          
          // Check against vigilance
          val reset = matchDegree < config.vigilance
          
          if (reset) {
            context.log.debug(
              f"Orienting: MISMATCH (match=$matchDegree%.3f < ρ=${config.vigilance}%.3f) → RESET"
            )
          } else {
            context.log.debug(
              f"Orienting: MATCH (match=$matchDegree%.3f >= ρ=${config.vigilance}%.3f) → RESONANCE"
            )
          }
          
          // Send match signal to both F1 and F2
          val matchSignal = MatchSignal(matchDegree, config.vigilance, reset)
          config.f1Layer ! matchSignal
          config.f2Layer ! matchSignal
          
          Behaviors.same
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  /**
   * Compute match degree between input and expectation
   * 
   * match = |input ∧ expectation| / |input|
   * 
   * Intuition:
   * - If expectation matches all active input features → match = 1.0
   * - If expectation matches none of the input features → match = 0.0
   * - Partial matches give intermediate values
   */
  private def computeMatchDegree(input: Vector[Double], expectation: Vector[Double]): Double = {
    require(input.length == expectation.length, "Input and expectation must have same length")
    
    val intersection = (input zip expectation).count { 
      case (i, e) => i > 0.5 && e > 0.5 
    }
    val inputMagnitude = input.count(_ > 0.5)
    
    if (inputMagnitude == 0) {
      // Empty input matches anything perfectly (edge case)
      1.0
    } else {
      intersection.toDouble / inputMagnitude.toDouble
    }
  }
}
