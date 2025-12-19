package edu.rit.dart.art

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.core.Messages._
import ARTMessages._

/**
 * F1 Layer (Comparison Field) for ART-1
 * 
 * The F1 layer performs pattern comparison and computes the match between:
 * 1. Bottom-up input pattern
 * 2. Top-down expectation from F2
 * 
 * Key Functions:
 * - Receives input patterns
 * - Computes bottom-up activation to F2
 * - Receives top-down expectations from F2
 * - Calculates match degree for vigilance check
 * 
 * Mathematical Operations:
 * - Input normalization: ensures binary (0/1) patterns
 * - Match computation: |input ∧ expectation| / |input|
 * - Bottom-up signal: typically the input itself in ART-1
 */
object F1Layer {
  
  final case class Config(
    layerId: String,
    inputSize: Int,
    f2Layer: ActorRef[NeuralSignal],
    orientingSubsystem: ActorRef[NeuralSignal]
  )
  
  private final case class LayerState(
    currentInput: Vector[Double] = Vector.empty,
    currentExpectation: Vector[Double] = Vector.empty
  )
  
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      context.log.info(s"F1 Layer ${config.layerId} initialized (size=${config.inputSize})")
      idle(config, LayerState())
    }
  }
  
  private def idle(config: Config, state: LayerState): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case PresentPattern(pattern, replyTo) =>
          // Validate pattern size
          if (pattern.length != config.inputSize) {
            context.log.error(
              s"Pattern size mismatch: expected ${config.inputSize}, got ${pattern.length}"
            )
            Behaviors.same
          } else {
            // Store input and send bottom-up activation to F2
            val normalizedPattern = normalizeBinary(pattern)
            val newState = state.copy(currentInput = normalizedPattern)
            
            context.log.debug(s"F1: Received pattern ${patternToString(normalizedPattern)}")
            
            // Send bottom-up activation to F2
            config.f2Layer ! BottomUpActivation(normalizedPattern)
            
            processing(config, newState, replyTo)
          }
        
        case TopDownExpectation(expectation) =>
          context.log.warn("F1: Received top-down expectation while idle (ignoring)")
          Behaviors.same
        
        case ResetCategory(_) =>
          // Reset to idle state
          idle(config, LayerState())
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  private def processing(
    config: Config,
    state: LayerState,
    replyTo: Option[ActorRef[LearningResult]]
  ): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case TopDownExpectation(expectation) =>
          // Received top-down expectation from F2
          val newState = state.copy(currentExpectation = expectation)
          
          context.log.debug(s"F1: Received expectation ${patternToString(expectation)}")
          
          // Compute match between input and expectation
          val matchDegree = computeMatch(state.currentInput, expectation)
          
          context.log.debug(f"F1: Match degree = $matchDegree%.3f")
          
          // Send to orienting subsystem for vigilance check
          config.orientingSubsystem ! CheckMatch(state.currentInput, expectation)
          
          awaitingMatch(config, newState, replyTo)
        
        case ResetCategory(inhibitCat) =>
          // Reset and search for new category
          context.log.debug(s"F1: Reset category $inhibitCat, re-sending input to F2")
          
          // Re-send bottom-up signal to F2 (which will try different category)
          config.f2Layer ! BottomUpActivation(state.currentInput)
          
          Behaviors.same
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  private def awaitingMatch(
    config: Config,
    state: LayerState,
    replyTo: Option[ActorRef[LearningResult]]
  ): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case MatchSignal(matchDegree, vigilance, reset) =>
          if (reset) {
            // Match failed, need to reset and search
            context.log.debug(
              f"F1: Match failed ($matchDegree%.3f < $vigilance%.3f), searching for new category"
            )
            processing(config, state, replyTo)
          } else {
            // Match succeeded, learning can occur
            context.log.debug(
              f"F1: Match succeeded ($matchDegree%.3f >= $vigilance%.3f), resonance achieved"
            )
            // Return to idle (F2 will handle weight update)
            idle(config, LayerState())
          }
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  /**
   * Normalize pattern to binary (0.0 or 1.0)
   */
  private def normalizeBinary(pattern: Vector[Double]): Vector[Double] = {
    pattern.map(x => if (x > 0.5) 1.0 else 0.0)
  }
  
  /**
   * Compute match degree between input and expectation
   * 
   * match = |input ∧ expectation| / |input|
   * 
   * This is the proportion of input features that are confirmed by the expectation.
   */
  private def computeMatch(input: Vector[Double], expectation: Vector[Double]): Double = {
    val intersection = (input zip expectation).count { case (i, e) => i > 0.5 && e > 0.5 }
    val inputMagnitude = input.count(_ > 0.5)
    
    if (inputMagnitude == 0) 0.0
    else intersection.toDouble / inputMagnitude.toDouble
  }
  
  /**
   * Convert pattern to readable string
   */
  private def patternToString(pattern: Vector[Double]): String = {
    pattern.map(x => if (x > 0.5) "1" else "0").mkString("")
  }
}
