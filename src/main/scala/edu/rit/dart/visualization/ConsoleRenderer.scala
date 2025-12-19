package edu.rit.dart.visualization

import akka.actor.typed.ActorRef
import edu.rit.dart.core.Messages._

/**
 * Simple console-based visualization utilities for neural networks
 */
object ConsoleRenderer {
  
  /**
   * Renders a 2D grid of activation values as ASCII art
   * 
   * @param activations 2D array of activation values
   * @param threshold Activation threshold for display (values below are shown as '.')
   */
  def renderGrid(activations: Array[Array[Double]], threshold: Double = 0.1): String = {
    val sb = new StringBuilder
    
    activations.foreach { row =>
      row.foreach { activation =>
        val char = if (activation < threshold) {
          '·'
        } else if (activation < 0.3) {
          '░'
        } else if (activation < 0.6) {
          '▒'
        } else if (activation < 0.9) {
          '▓'
        } else {
          '█'
        }
        sb.append(char).append(' ')
      }
      sb.append('\n')
    }
    
    sb.toString()
  }
  
  /**
   * Renders activation values with numeric precision
   */
  def renderGridNumeric(activations: Array[Array[Double]]): String = {
    val sb = new StringBuilder
    
    activations.foreach { row =>
      row.foreach { activation =>
        sb.append(f"$activation%5.2f ")
      }
      sb.append('\n')
    }
    
    sb.toString()
  }
  
  /**
   * Renders a color-coded heatmap using ANSI color codes
   */
  def renderHeatmap(activations: Array[Array[Double]]): String = {
    val sb = new StringBuilder
    
    activations.foreach { row =>
      row.foreach { activation =>
        val colorCode = getColorCode(activation)
        sb.append(colorCode).append("██").append(Console.RESET)
      }
      sb.append('\n')
    }
    
    sb.toString()
  }
  
  /**
   * Maps activation value to ANSI color code
   */
  private def getColorCode(activation: Double): String = {
    if (activation < 0.0) Console.BLUE
    else if (activation < 0.2) Console.CYAN
    else if (activation < 0.4) Console.GREEN
    else if (activation < 0.6) Console.YELLOW
    else if (activation < 0.8) Console.MAGENTA
    else Console.RED
  }
  
  /**
   * Prints a header for visualization output
   */
  def printHeader(title: String): Unit = {
    val border = "=" * (title.length + 4)
    println(s"\n$border")
    println(s"  $title")
    println(s"$border\n")
  }
  
  /**
   * Prints network statistics
   */
  def printStats(activations: Array[Array[Double]]): Unit = {
    val flat = activations.flatten
    val mean = flat.sum / flat.length
    val max = flat.max
    val min = flat.min
    val variance = flat.map(x => math.pow(x - mean, 2)).sum / flat.length
    val stdDev = math.sqrt(variance)
    
    println(f"Statistics:")
    println(f"  Mean:     $mean%.3f")
    println(f"  Std Dev:  $stdDev%.3f")
    println(f"  Min:      $min%.3f")
    println(f"  Max:      $max%.3f")
    println()
  }
}
