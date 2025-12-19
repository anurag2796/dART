package edu.rit.dart.art

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import edu.rit.dart.core.Messages._
import ARTMessages._

/**
 * ARTNetwork orchestrates F1, F2, and Orienting Subsystem
 * 
 * This is the top-level actor that coordinates the ART-1 learning process:
 * 1. Receives pattern presentation requests
 * 2. Manages the F1-F2-Orienting interaction
 * 3. Returns learning results
 * 
 * Usage:
 *   val network = spawn(ARTNetwork(config))
 *   network ! PresentPattern(binaryPattern, Some(replyTo))
 */
object ARTNetwork {
  
  final case class Config(
    networkId: String,
    inputSize: Int,
    maxCategories: Int = 100,
    vigilance: Double = 0.7,
    learningRate: Double = 1.0
  )
  
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      context.log.info(
        s"ARTNetwork ${config.networkId} initializing " +
        s"(input_size=${config.inputSize}, max_categories=${config.maxCategories}, " +
        s"vigilance=${config.vigilance}, learning_rate=${config.learningRate})"
      )
      
      // Create the three subsystems
      // Note: We need to create them in a specific order due to dependencies
      
      val orientingSubsystem = context.spawn(
        Behaviors.setup[NeuralSignal] { orientingContext =>
          // Will be configured with F1/F2 refs in a moment
          Behaviors.empty
        },
        "orienting-subsystem"
      )
      
      val f1Layer = context.spawn(
        Behaviors.setup[NeuralSignal] { f1Context =>
          Behaviors.empty
        },
        "f1-layer"
      )
      
      val f2Layer = context.spawn(
        Behaviors.setup[NeuralSignal] { f2Context =>
          Behaviors.empty
        },
        "f2-layer"
      )
      
      // Now recreate with proper references
      context.stop(orientingSubsystem)
      context.stop(f1Layer)
      context.stop(f2Layer)
      
      val orientingReal = context.spawn(
        OrientingSubsystem(OrientingSubsystem.Config(
          subsystemId = s"${config.networkId}-orienting",
          vigilance = config.vigilance,
          f1Layer = f1Layer,
          f2Layer = f2Layer
        )),
        "orienting-subsystem-real"
      )
      
      val f2Real = context.spawn(
        F2Layer(F2Layer.Config(
          layerId = s"${config.networkId}-f2",
          maxCategories = config.maxCategories,
          inputSize = config.inputSize,
          learningRate = config.learningRate,
          f1Layer = f1Layer,
          orientingSubsystem = orientingReal
        )),
        "f2-layer-real"
      )
      
      val f1Real = context.spawn(
        F1Layer(F1Layer.Config(
          layerId = s"${config.networkId}-f1",
          inputSize = config.inputSize,
          f2Layer = f2Real,
          orientingSubsystem = orientingReal
        )),
        "f1-layer-real"
      )
      
      context.log.info(s"ARTNetwork ${config.networkId} initialized successfully")
      
      running(config, NetworkComponents(f1Real, f2Real, orientingReal))
    }
  }
  
  private final case class NetworkComponents(
    f1: ActorRef[NeuralSignal],
    f2: ActorRef[NeuralSignal],
    orienting: ActorRef[NeuralSignal]
  )
  
  private def running(
    config: Config,
    components: NetworkComponents
  ): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case pattern: PresentPattern =>
          // Forward to F1 layer to begin learning/recognition cycle
          context.log.debug(s"ARTNetwork: Presenting pattern to F1")
          components.f1 ! pattern
          Behaviors.same
        
        case GetNetworkState =>
          // Forward to F2 to get number of categories
          components.f2 ! GetNetworkState
          Behaviors.same
        
        case _ =>
          // Forward other messages appropriately
          Behaviors.unhandled
      }
    }
  }
}
