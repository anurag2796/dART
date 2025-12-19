package examples

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.art._
import edu.rit.dart.art.ARTMessages._

import scala.concurrent.duration._

/**
 * Simple ART-1 Demo: Pattern Learning
 * 
 * This demonstrates basic ART-1 learning on binary patterns.
 */
object SimpleART1Demo extends App {
  
  println("""
    |╔══════════════════════════════════════════════════════════════╗
    |║   dART Phase 2 Demo: ART-1 Pattern Learning                 ║
    |║   Demonstrating Adaptive Resonance                           ║
    |╚══════════════════════════════════════════════════════════════╝
    |""".stripMargin)
  
  // Define test patterns (8-bit binary vectors)
  val patternA = Vector(1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0) // Alternating
  val patternB = Vector(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0) // Left half
  val patternC = Vector(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0) // Right half
  
  println("\nTest Patterns:")
  println(s"  Pattern A: ${patternToString(patternA)}")
  println(s"  Pattern B: ${patternToString(patternB)}")
  println(s"  Pattern C: ${patternToString(patternC)}")
  println()
  
  val system = ActorSystem(Behaviors.setup[Unit] { context =>
    
    // Create ART network with medium vigilance
    val network = context.spawn(
      ARTNetwork(ARTNetwork.Config(
        networkId = "simple-art",
        inputSize = 8,
        maxCategories = 10,
        vigilance = 0.7,
        learningRate = 1.0
      )),
      "art-network"
    )
    
    println("✓ Created ART-1 network (vigilance ρ = 0.7)")
    println()
    
    println("Training Phase:")
    println("► Presenting Pattern A...")
    network ! PresentPattern(patternA)
    Thread.sleep(200)
    
    println("► Presenting Pattern B...")
    network ! PresentPattern(patternB)
    Thread.sleep(200)
    
    println("► Presenting Pattern C...")
    network ! PresentPattern(patternC)
    Thread.sleep(200)
    
    println()
    println("Testing Phase:")
    println("► Re-presenting Pattern A (should recognize)...")
    network ! PresentPattern(patternA)
    Thread.sleep(200)
    
    println()
    println("✓ ART-1 demonstration complete!")
    println()
    println("Key Observations:")
    println("- Network learns patterns incrementally")
    println("- Vigilance parameter controls category granularity")
    println("- No catastrophic forgetting (Pattern A remembered)")
    println()
    
    Thread.sleep(500)
    
    println("Shutting down...")
    context.system.terminate()
    
    Behaviors.empty
  }, "simple-art1-demo")
  
  scala.concurrent.Await.result(system.whenTerminated, Duration.Inf)
  
  def patternToString(pattern: Vector[Double]): String = {
    pattern.map(x => if (x > 0.5) "1" else "0").mkString("")
  }
}
