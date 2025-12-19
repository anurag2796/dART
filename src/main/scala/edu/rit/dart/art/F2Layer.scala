package edu.rit.dart.art

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.core.Messages._
import ARTMessages._
import scala.collection.mutable

/**
 * F2 Layer (Recognition Field) for ART-1
 * 
 * The F2 layer represents learned categories and performs:
 * 1. Competitive selection (winner-take-all)
 * 2. Top-down expectation generation
 * 3. Weight learning
 * 
 * Key Mechanisms:
 * - Category Competition: Each F2 node computes activation based on input match
 * - Winner Selection: Highest activation wins (with inhibited categories excluded)
 * - Weight Update: Fast learning on resonance
 * 
 * Weight Update Rule (ART-1):
 * w_new = L * (input ∧ w_old) + w_old * (1 - L)
 * 
 * where L is the learning rate (typically 1.0 for fast learning)
 */
object F2Layer {
  
  final case class Config(
    layerId: String,
    maxCategories: Int,
    inputSize: Int,
    learningRate: Double = 1.0,
    f1Layer: ActorRef[NeuralSignal],
    orientingSubsystem: ActorRef[NeuralSignal]
  )
  
  /**
   * Category node storing learned prototype
   */
  private final case class Category(
    id: Int,
    weights: Vector[Double],
    numPresentations: Int = 0
  )
  
  private final case class LayerState(
    categories: mutable.ArrayBuffer[Category] = mutable.ArrayBuffer.empty,
    inhibitedCategories: Set[Int] = Set.empty,
    currentInput: Vector[Double] = Vector.empty
  )
  
  def apply(config: Config): Behavior[NeuralSignal] = {
    Behaviors.setup { context =>
      context.log.info(
        s"F2 Layer ${config.layerId} initialized " +
        s"(max_categories=${config.maxCategories}, input_size=${config.inputSize}, L=${config.learningRate})"
      )
      idle(config, LayerState())
    }
  }
  
  private def idle(config: Config, state: LayerState): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case BottomUpActivation(activation) =>
          // Store input and compete for category
          val newState = state.copy(
            currentInput = activation,
            inhibitedCategories = Set.empty // Reset inhibition on new input
          )
          
          context.log.debug(s"F2: Received bottom-up activation")
          
          // Find winning category
          val winner = selectWinningCategory(config, newState, context)
          
          winner match {
            case Some(category) =>
              // Send top-down expectation to F1
              context.log.debug(s"F2: Category ${category.id} won, sending expectation")
              config.f1Layer ! TopDownExpectation(category.weights)
              
              awaitingMatch(config, newState, category.id)
            
            case None =>
              // No suitable category, create new one if possible
              if (state.categories.length < config.maxCategories) {
                val newCategory = createNewCategory(state.categories.length, activation)
                state.categories += newCategory
                
                context.log.info(s"F2: Created new category ${newCategory.id} (total: ${state.categories.length})")
                
                // Send expectation (which is just the input for new category)
                config.f1Layer ! TopDownExpectation(newCategory.weights)
                
                awaitingMatch(config, newState, newCategory.id)
              } else {
                context.log.error(s"F2: Maximum categories reached (${config.maxCategories}), cannot learn")
                idle(config, LayerState())
              }
          }
        
        case GetNetworkState =>
          context.log.debug(s"F2: Network has ${state.categories.length} categories")
          Behaviors.same
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  private def awaitingMatch(
    config: Config,
    state: LayerState,
    selectedCategory: Int
  ): Behavior[NeuralSignal] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case MatchSignal(matchDegree, vigilance, reset) =>
          if (reset) {
            // Match failed - inhibit this category and try again
            context.log.debug(
              f"F2: Category $selectedCategory failed match " +
              f"($matchDegree%.3f < $vigilance%.3f), inhibiting and retrying"
            )
            
            val newState = state.copy(
              inhibitedCategories = state.inhibitedCategories + selectedCategory
            )
            
            // Send reset signal to F1 to trigger re-competition
            config.f1Layer ! ResetCategory(selectedCategory)
            
            // Try to find another category
            val winner = selectWinningCategory(config, newState, context)
            
            winner match {
              case Some(category) =>
                context.log.debug(s"F2: Trying category ${category.id}")
                config.f1Layer ! TopDownExpectation(category.weights)
                awaitingMatch(config, newState, category.id)
              
              case None =>
                // No more suitable categories, create new one
                if (state.categories.length < config.maxCategories) {
                  val newCategory = createNewCategory(state.categories.length, state.currentInput)
                  state.categories += newCategory
                  
                  context.log.info(s"F2: All categories failed, created new category ${newCategory.id}")
                  
                  config.f1Layer ! TopDownExpectation(newCategory.weights)
                  awaitingMatch(config, newState, newCategory.id)
                } else {
                  context.log.error("F2: All categories exhausted, no learning possible")
                  idle(config, LayerState())
                }
            }
          } else {
            // Match succeeded - update weights and return to idle
            context.log.debug(
              f"F2: Category $selectedCategory matched " +
              f"($matchDegree%.3f >= $vigilance%.3f), updating weights"
            )
            
            updateCategoryWeights(state, selectedCategory, state.currentInput, config.learningRate, context)
            
            idle(config, LayerState())
          }
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
  
  /**
   * Select winning category based on input activation
   * 
   * Activation of category j:
   *   T_j = |input ∧ w_j| / (α + |w_j|)
   * 
   * where α is a small constant (typically 0.1)
   */
  private def selectWinningCategory(
    config: Config,
    state: LayerState,
    context: akka.actor.typed.scaladsl.ActorContext[NeuralSignal]
  ): Option[Category] = {
    val alpha = 0.1
    
    val validCategories = state.categories.filterNot(cat => 
      state.inhibitedCategories.contains(cat.id)
    )
    
    if (validCategories.isEmpty) {
      return None
    }
    
    // Compute activation for each category
    val activations = validCategories.map { category =>
      val intersection = (state.currentInput zip category.weights).count { 
        case (i, w) => i > 0.5 && w > 0.5 
      }
      val weightMagnitude = category.weights.count(_ > 0.5)
      val activation = intersection.toDouble / (alpha + weightMagnitude.toDouble)
      
      context.log.debug(f"F2: Category ${category.id} activation = $activation%.3f")
      
      (category, activation)
    }
    
    // Select winner (max activation)
    val winner = activations.maxBy(_._2)
    Some(winner._1)
  }
  
  /**
   * Create new category initialized to input pattern
   */
  private def createNewCategory(id: Int, input: Vector[Double]): Category = {
    Category(
      id = id,
      weights = input, // Initialize to input pattern
      numPresentations = 1
    )
  }
  
  /**
   * Update category weights using fast learning
   * 
   * w_new = L * (input ∧ w_old) + w_old * (1 - L)
   * 
   * For L = 1.0 (fast learning):
   *   w_new = input ∧ w_old
   */
  private def updateCategoryWeights(
    state: LayerState,
    categoryId: Int,
    input: Vector[Double],
    learningRate: Double,
    context: akka.actor.typed.scaladsl.ActorContext[NeuralSignal]
  ): Unit = {
    val categoryIndex = state.categories.indexWhere(_.id == categoryId)
    
    if (categoryIndex >= 0) {
      val category = state.categories(categoryIndex)
      
      // Compute new weights (intersection for L=1.0)
      val newWeights = if (learningRate >= 0.99) {
        // Fast learning: w_new = input ∧ w_old
        (input zip category.weights).map { case (i, w) =>
          if (i > 0.5 && w > 0.5) 1.0 else 0.0
        }
      } else {
        // Slow learning: interpolation
        (input zip category.weights).map { case (i, w) =>
          val intersection = if (i > 0.5 && w > 0.5) 1.0 else 0.0
          learningRate * intersection + w * (1.0 - learningRate)
        }
      }
      
      val updatedCategory = category.copy(
        weights = newWeights,
        numPresentations = category.numPresentations + 1
      )
      
      state.categories(categoryIndex) = updatedCategory
      
      context.log.debug(
        s"F2: Updated category $categoryId (presentations: ${updatedCategory.numPresentations})"
      )
    }
  }
}
