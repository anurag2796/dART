package edu.rit.dart.core

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import edu.rit.dart.core.Messages._
import scala.concurrent.duration._

/**
 * Test suite for ShuntingNeuron actor
 * 
 * Verifies:
 * - Bounded activation (stays within [D, B])
 * - Excitation increases activation
 * - Inhibition decreases activation
 * - Decay returns to baseline
 * - Reset functionality
 */
class ShuntingNeuronSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  
  "A ShuntingNeuron" must {
    
    "maintain activation within bounds [D, B]" in {
      val config = ShuntingNeuron.Config(
        neuronId = "test-neuron",
        A = 0.1,
        B = 1.0,
        D = 0.0,
        dt = 10
      )
      
      val neuron = spawn(ShuntingNeuron(config))
      val probe = createTestProbe[ActivationResponse]()
      
      // Apply strong excitation
      for (_ <- 0 until 100) {
        neuron ! ExcitatorySignal(1.0)
      }
      
      // Wait for integration
      Thread.sleep(500)
      
      // Check activation is bounded by B
      neuron ! GetActivation(probe.ref)
      val response = probe.receiveMessage(1.second)
      
      assert(response.activation <= config.B, s"Activation ${response.activation} exceeds upper bound ${config.B}")
      assert(response.activation >= config.D, s"Activation ${response.activation} below lower bound ${config.D}")
    }
    
    "increase activation in response to excitatory input" in {
      val config = ShuntingNeuron.Config(
        neuronId = "test-neuron-exc",
        A = 0.1,
        B = 1.0,
        D = 0.0,
        dt = 10
      )
      
      val neuron = spawn(ShuntingNeuron(config))
      val probe = createTestProbe[ActivationResponse]()
      
      // Get baseline activation
      neuron ! GetActivation(probe.ref)
      val baseline = probe.receiveMessage(1.second).activation
      
      // Apply excitation
      neuron ! ExcitatorySignal(0.5)
      
      // Wait for integration
      Thread.sleep(200)
      
      // Check activation increased
      neuron ! GetActivation(probe.ref)
      val afterExcitation = probe.receiveMessage(1.second).activation
      
      assert(afterExcitation > baseline, s"Activation did not increase: baseline=$baseline, after=$afterExcitation")
    }
    
    "decrease activation in response to inhibitory input" in {
      val config = ShuntingNeuron.Config(
        neuronId = "test-neuron-inh",
        A = 0.05, // Lower decay for this test
        B = 1.0,
        D = 0.0,
        dt = 10
      )
      
      val neuron = spawn(ShuntingNeuron(config))
      val probe = createTestProbe[ActivationResponse]()
      
      // First, raise activation with excitation
      neuron ! ExcitatorySignal(0.8)
      Thread.sleep(300)
      
      neuron ! GetActivation(probe.ref)
      val raised = probe.receiveMessage(1.second).activation
      
      // Now apply inhibition
      neuron ! InhibitorySignal(0.5)
      Thread.sleep(200)
      
      neuron ! GetActivation(probe.ref)
      val afterInhibition = probe.receiveMessage(1.second).activation
      
      assert(afterInhibition < raised, s"Activation did not decrease: before=$raised, after=$afterInhibition")
    }
    
    "decay toward baseline without input" in {
      val config = ShuntingNeuron.Config(
        neuronId = "test-neuron-decay",
        A = 0.2, // Higher decay rate
        B = 1.0,
        D = 0.0,
        dt = 10
      )
      
      val neuron = spawn(ShuntingNeuron(config))
      val probe = createTestProbe[ActivationResponse]()
      
      // Raise activation
      neuron ! ExcitatorySignal(1.0)
      Thread.sleep(200)
      
      neuron ! GetActivation(probe.ref)
      val peak = probe.receiveMessage(1.second).activation
      
      // Wait for decay (no input)
      Thread.sleep(1000)
      
      neuron ! GetActivation(probe.ref)
      val decayed = probe.receiveMessage(1.second).activation
      
      assert(decayed < peak, s"Activation did not decay: peak=$peak, decayed=$decayed")
      assert(decayed < peak * 0.5, s"Decay was too slow: peak=$peak, decayed=$decayed")
    }
    
    "reset to baseline when Reset message received" in {
      val config = ShuntingNeuron.Config(
        neuronId = "test-neuron-reset",
        A = 0.1,
        B = 1.0,
        D = 0.0,
        dt = 10
      )
      
      val neuron = spawn(ShuntingNeuron(config))
      val probe = createTestProbe[ActivationResponse]()
      
      // Raise activation
      neuron ! ExcitatorySignal(0.8)
      Thread.sleep(300)
      
      // Reset
      neuron ! Reset
      Thread.sleep(100)
      
      // Check activation is near zero
      neuron ! GetActivation(probe.ref)
      val afterReset = probe.receiveMessage(1.second).activation
      
      assert(afterReset < 0.1, s"Activation not reset: $afterReset")
    }
  }
}
