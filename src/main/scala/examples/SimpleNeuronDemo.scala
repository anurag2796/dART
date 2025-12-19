package examples

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.core._
import edu.rit.dart.core.Messages._

import scala.concurrent.duration._

/**
 * Simple demo: Single shunting neuron responding to excitation
 * 
 * This is a minimal demo to verify the shunting neuron works correctly.
 */
object SimpleNeuronDemo extends App {
  
  println("""
    |╔══════════════════════════════════════════════════════════════╗
    |║   dART Phase 1 Demo: Single Shunting Neuron                 ║
    |║   Demonstrating Basic Neuron Dynamics                        ║
    |╚══════════════════════════════════════════════════════════════╝
    |""".stripMargin)
  
  val system = ActorSystem(Behaviors.setup[ActivationResponse] { context =>
    
    // Create a single neuron
    val neuron = context.spawn(
      ShuntingNeuron(ShuntingNeuron.Config(
        neuronId = "test-neuron",
        A = 0.1,
        B = 1.0,
        D = 0.0,
        dt = 10
      )),
      "test-neuron"
    )
    
    println("✓ Created shunting neuron")
    println()
    
    // Apply excitation
    println("► Sending excitatory signal (strength = 0.8)...")
    neuron ! ExcitatorySignal(0.8)
    
    // Wait a bit for integration
    Thread.sleep(500)
    
    // Query activation
    println("► Querying neuron activation...")
    neuron ! GetActivation(context.self)
    
    Behaviors.receiveMessage { response =>
      println(f"✓ Neuron activation: ${response.activation}%.3f")
      println()
      
      // Apply inhibition
      println("► Sending inhibitory signal (strength = 0.3)...")
      neuron ! InhibitorySignal(0.3)
      
      Thread.sleep(500)
      
      neuron ! GetActivation(context.self)
      
      Behaviors.receiveMessage { response2 =>
        println(f"✓ Neuron activation after inhibition: ${response2.activation}%.3f")
        println()
        
        if (response2.activation < response.activation) {
          println("✓ SUCCESS: Inhibition decreased activation as expected!")
        } else {
          println("⚠ WARNING: Unexpected behavior")
        }
        
        println("\nSimulation complete. Shutting down...")
        context.system.terminate()
        Behaviors.stopped
      }
    }
  }, "simple-neuron-demo")
  
  scala.concurrent.Await.result(system.whenTerminated, Duration.Inf)
}
