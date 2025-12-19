package edu.rit.dart.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import Messages._
import scala.collection.mutable

/**
 * InhibitoryField implements distributed lateral inhibition using a mean-field approach.
 * 
 * Problem:
 * Traditional lateral inhibition requires O(N²) messages (each neuron inhibits all others).
 * 
 * Solution:
 * Use a central "Inhibitory Field" actor that:
 * 1. Collects excitation from all neurons (O(N) messages)
 * 2. Computes mean-field inhibition
 * 3. Broadcasts inhibition back to all neurons (O(N) messages)
 * 
 * Total complexity: O(N) instead of O(N²)
 * 
 * Biological Justification:
 * Mimics interneuron pools in cortex that aggregate activity and provide
 * diffuse inhibition to a local region.
 * 
 * Mathematical Model:
 * For each neuron i:
 *   Inhibition_i = β * (Σ_j activation_j - activation_i)
 * 
 * where β is the inhibitory strength parameter.
 * 
 * @param inhibitoryStrength Scaling factor for inhibition (default: 0.5)
 */
object InhibitoryField {
  
  final case class Config(
    fieldId: String,
    inhibitoryStrength: Double = 0.5
  )
  
  /**
   * Internal state tracking registered neurons and their activations
   */
  private final case class FieldState(
    neurons: mutable.Map[ActorRef[NeuralSignal], String] = mutable.Map.empty,
    activations: mutable.Map[ActorRef[NeuralSignal], Double] = mutable.Map.empty
  )
  
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      context.log.info(s"InhibitoryField ${config.fieldId} initialized (β=${config.inhibitoryStrength})")
      running(config, FieldState())
    }
  }
  
  private def running(config: Config, state: FieldState): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case RegisterNeuron(neuron, neuronId) =>
          // Register a new neuron in the field
          state.neurons += (neuron -> neuronId)
          state.activations += (neuron -> 0.0)
          context.log.info(s"${config.fieldId}: Registered neuron $neuronId (total: ${state.neurons.size})")
          Behaviors.same
        
        case UnregisterNeuron(neuron) =>
          // Remove neuron from field
          val neuronId = state.neurons.get(neuron)
          state.neurons -= neuron
          state.activations -= neuron
          context.log.info(s"${config.fieldId}: Unregistered neuron $neuronId (remaining: ${state.neurons.size})")
          Behaviors.same
        
        case ExcitatorySignal(strength, Some(source)) =>
          // Update activation for source neuron
          state.activations += (source -> strength)
          
          // Compute and broadcast mean-field inhibition
          broadcastInhibition(config, state, context)
          Behaviors.same
        
        case ExcitatorySignal(_, None) =>
          context.log.warn(s"${config.fieldId}: Received ExcitatorySignal without source")
          Behaviors.same
        
        case GetActivation(replyTo) =>
          // Report field statistics
          val meanActivation = if (state.activations.nonEmpty) {
            state.activations.values.sum / state.activations.size
          } else {
            0.0
          }
          replyTo ! ActivationResponse(meanActivation, config.fieldId)
          Behaviors.same
        
        case Reset =>
          // Reset all tracked activations
          state.activations.keys.foreach { neuron =>
            state.activations += (neuron -> 0.0)
          }
          context.log.info(s"${config.fieldId}: Reset all activations")
          Behaviors.same
        
        case InhibitorySignal(_, _) | IntegrationTick =>
          // Not applicable to field
          Behaviors.unhandled
      }
    }
  }
  
  /**
   * Computes mean-field inhibition and broadcasts to all neurons
   * 
   * Each neuron receives inhibition proportional to:
   *   I_i = β * (mean_activation - activation_i)
   * 
   * This creates "on-center, off-surround" dynamics:
   * - Neurons with above-average activation receive less inhibition
   * - Neurons with below-average activation receive more inhibition
   * - Result: contrast enhancement
   */
  private def broadcastInhibition(
    config: Config,
    state: FieldState,
    context: akka.actor.typed.scaladsl.ActorContext[NeuralSignal]
  ): Unit = {
    if (state.activations.isEmpty) return
    
    // Compute mean activation
    val meanActivation = state.activations.values.sum / state.activations.size
    
    context.log.debug(s"${config.fieldId}: Mean activation = ${meanActivation.formatted("%.3f")}")
    
    // Send inhibitory signal to each neuron
    state.activations.foreach { case (neuron, activation) =>
      // Inhibition is proportional to how much above mean this neuron is
      val inhibition = config.inhibitoryStrength * (meanActivation - activation)
      
      // Only send if inhibition is positive (we don't send negative inhibition = excitation)
      if (inhibition > 0) {
        neuron ! InhibitorySignal(inhibition)
      }
    }
  }
}
