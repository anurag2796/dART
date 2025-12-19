package edu.rit.dart.core

import akka.actor.typed.ActorRef

/**
 * Message protocol for neural communication in the dART system.
 * All neurons communicate via these typed messages.
 */
object Messages {
  
  /**
   * Base trait for all neural signals
   * Not sealed to allow extension from ART package
   */
  trait NeuralSignal
  
  /**
   * Excitatory input signal (increases neuron activation)
   * @param strength Magnitude of excitatory input (typically 0.0 to 1.0)
   * @param source Optional reference to sending neuron
   */
  final case class ExcitatorySignal(
    strength: Double, 
    source: Option[ActorRef[NeuralSignal]] = None
  ) extends NeuralSignal
  
  /**
   * Inhibitory input signal (decreases neuron activation)
   * @param strength Magnitude of inhibitory input (typically 0.0 to 1.0)
   * @param source Optional reference to sending neuron
   */
  final case class InhibitorySignal(
    strength: Double,
    source: Option[ActorRef[NeuralSignal]] = None
  ) extends NeuralSignal
  
  /**
   * Query message to get current neuron activation
   * @param replyTo Actor to receive the activation response
   */
  final case class GetActivation(
    replyTo: ActorRef[ActivationResponse]
  ) extends NeuralSignal
  
  /**
   * Response message containing neuron activation value
   * @param activation Current activation level (typically 0.0 to B)
   * @param neuronId Identifier of the responding neuron
   */
  final case class ActivationResponse(
    activation: Double,
    neuronId: String
  ) extends NeuralSignal
  
  /**
   * Reset neuron to baseline state
   */
  case object Reset extends NeuralSignal
  
  /**
   * Internal message for self-scheduled integration step
   * (Not part of external protocol)
   */
  private[core] case object IntegrationTick extends NeuralSignal
  
  /**
   * Register neuron with inhibitory field
   * Used for lateral inhibition networks
   */
  final case class RegisterNeuron(
    neuron: ActorRef[NeuralSignal],
    neuronId: String
  ) extends NeuralSignal
  
  /**
   * Unregister neuron from inhibitory field
   */
  final case class UnregisterNeuron(
    neuron: ActorRef[NeuralSignal]
  ) extends NeuralSignal
}
