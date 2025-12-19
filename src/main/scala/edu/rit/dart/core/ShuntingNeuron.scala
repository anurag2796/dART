package edu.rit.dart.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.duration._
import Messages._

/**
 * ShuntingNeuron implements Grossberg's shunting inhibition equation as an Akka Actor.
 * 
 * Mathematical Model:
 * dx/dt = -Ax + (B - x)[I+ + ΣS+] - (D + x)[I- + ΣS-]
 * 
 * where:
 * - x: neuron activation (state variable)
 * - A: passive decay rate
 * - B: maximum excitation level (upper bound)
 * - D: maximum inhibition level (lower bound, typically -B)
 * - I+, I-: external excitatory/inhibitory inputs
 * - S+, S-: synaptic excitatory/inhibitory inputs
 * 
 * Key Properties:
 * - Activation stays bounded between [D, B]
 * - Shunting: inhibition's effect depends on current activation (multiplicative)
 * - Noise saturation: prevents blow-up from excessive input
 * 
 * Implementation Notes:
 * - Uses Euler integration with small time step (dt)
 * - Self-schedules integration ticks for asynchronous dynamics
 * - Buffers inputs between integration steps
 * 
 * @param neuronId Unique identifier for this neuron
 * @param A Passive decay constant (default: 0.1)
 * @param B Maximum excitation bound (default: 1.0)
 * @param D Maximum inhibition bound (default: -1.0)
 * @param dt Integration time step in milliseconds (default: 10ms)
 */
object ShuntingNeuron {
  
  /**
   * Configuration parameters for shunting neuron
   */
  final case class Config(
    neuronId: String,
    A: Double = 0.1,      // Decay rate
    B: Double = 1.0,      // Excitation upper bound
    D: Double = -1.0,     // Inhibition lower bound
    dt: Int = 10          // Integration time step (ms)
  )
  
  /**
   * Internal state of the neuron
   */
  private final case class NeuronState(
    activation: Double = 0.0,           // Current activation x
    excitatoryBuffer: Double = 0.0,     // Buffered excitatory input
    inhibitoryBuffer: Double = 0.0      // Buffered inhibitory input
  )
  
  /**
   * Creates a ShuntingNeuron behavior
   */
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        // Start the integration loop
        timers.startTimerWithFixedDelay(IntegrationTick, config.dt.milliseconds)
        
        context.log.info(s"ShuntingNeuron ${config.neuronId} initialized (A=${config.A}, B=${config.B}, D=${config.D})")
        
        running(config, NeuronState(), timers)
      }
    }
  }
  
  /**
   * Main behavior: running neuron with active integration
   */
  private def running(
    config: Config,
    state: NeuronState,
    timers: TimerScheduler[NeuralSignal]
  ): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case ExcitatorySignal(strength, _) =>
          // Buffer excitatory input
          val newState = state.copy(
            excitatoryBuffer = state.excitatoryBuffer + strength
          )
          running(config, newState, timers)
        
        case InhibitorySignal(strength, _) =>
          // Buffer inhibitory input
          val newState = state.copy(
            inhibitoryBuffer = state.inhibitoryBuffer + strength
          )
          running(config, newState, timers)
        
        case IntegrationTick =>
          // Perform integration step using Grossberg's shunting equation
          val newActivation = integrateShuntingEquation(config, state)
          
          // Reset buffers after integration
          val newState = NeuronState(
            activation = newActivation,
            excitatoryBuffer = 0.0,
            inhibitoryBuffer = 0.0
          )
          
          context.log.debug(
            s"${config.neuronId}: x=${newActivation.formatted("%.3f")} " +
            s"(E+=${state.excitatoryBuffer.formatted("%.3f")}, " +
            s"I-=${state.inhibitoryBuffer.formatted("%.3f")})"
          )
          
          running(config, newState, timers)
        
        case GetActivation(replyTo) =>
          // Respond with current activation
          replyTo ! ActivationResponse(state.activation, config.neuronId)
          Behaviors.same
        
        case Reset =>
          // Reset to baseline state
          context.log.info(s"${config.neuronId}: Reset to baseline")
          running(config, NeuronState(), timers)
        
        case RegisterNeuron(_, _) | UnregisterNeuron(_) =>
          // Not applicable to individual neurons
          Behaviors.unhandled
      }
    }
  }
  
  /**
   * Integrates the shunting equation using Euler method
   * 
   * dx/dt = -Ax + (B - x)[I+ + ΣS+] - (D + x)[I- + ΣS-]
   * 
   * Euler integration: x(t+dt) = x(t) + dt * dx/dt
   */
  private def integrateShuntingEquation(
    config: Config,
    state: NeuronState
  ): Double = {
    val x = state.activation
    val excitation = state.excitatoryBuffer
    val inhibition = state.inhibitoryBuffer
    
    // Compute derivatives
    val decay = -config.A * x
    val excitatoryTerm = (config.B - x) * excitation
    val inhibitoryTerm = (config.D + x) * inhibition
    
    val dxdt = decay + excitatoryTerm - inhibitoryTerm
    
    // Euler step (dt normalized to 1.0 since we use millisecond timesteps)
    val dtNormalized = config.dt / 1000.0  // Convert ms to seconds
    val newX = x + dtNormalized * dxdt
    
    // Clamp to bounds [D, B]
    clamp(newX, config.D, config.B)
  }
  
  /**
   * Clamps value to range [min, max]
   */
  private def clamp(value: Double, min: Double, max: Double): Double = {
    math.max(min, math.min(max, value))
  }
}
