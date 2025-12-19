package edu.rit.dart.optimization

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.rit.dart.core.Messages._
import scala.collection.mutable

/**
 * TransactionManager handles speculative execution with rollback capability.
 * 
 * Inspired by Pellegrini's work on speculative distributed simulation of SNNs.
 * 
 * Key Concept:
 * - F2 can optimistically update weights before receiving confirmation
 * - If reset signal arrives, transaction is rolled back
 * - If match succeeds, transaction is committed
 * 
 * This reduces latency in distributed scenarios where network delays
 * would otherwise force F2 to wait for Orienting Subsystem response.
 */
object TransactionManager {
  
  /**
   * Snapshot of a category's state at a point in time
   */
  final case class CategorySnapshot(
    categoryId: Int,
    weights: Vector[Double],
    numPresentations: Int,
    timestamp: Long
  )
  
  /**
   * Transaction record tracking speculative updates
   */
  final case class Transaction(
    transactionId: Long,
    categoryId: Int,
    snapshot: CategorySnapshot,
    startTime: Long,
    status: TransactionStatus
  )
  
  sealed trait TransactionStatus
  case object Pending extends TransactionStatus
  case object Committed extends TransactionStatus
  case object RolledBack extends TransactionStatus
  
  // Messages
  sealed trait TransactionMessage extends NeuralSignal
  
  final case class BeginTransaction(
    transactionId: Long,
    categoryId: Int,
    snapshot: CategorySnapshot
  ) extends TransactionMessage
  
  final case class CommitTransaction(
    transactionId: Long
  ) extends TransactionMessage
  
  final case class RollbackTransaction(
    transactionId: Long,
    replyTo: ActorRef[RollbackResult]
  ) extends TransactionMessage
  
  final case class RollbackResult(
    transactionId: Long,
    snapshot: CategorySnapshot,
    success: Boolean
  ) extends TransactionMessage
  
  final case class GetSnapshot(
    categoryId: Int,
    replyTo: ActorRef[SnapshotResponse]
  ) extends TransactionMessage
  
  final case class SnapshotResponse(
    categoryId: Int,
    snapshot: Option[CategorySnapshot]
  ) extends TransactionMessage
  
  // Statistics for analysis
  final case class GetStatistics(
    replyTo: ActorRef[Statistics]
  ) extends TransactionMessage
  
  final case class Statistics(
    totalTransactions: Long,
    committed: Long,
    rolledBack: Long,
    pending: Long,
    rollbackRate: Double
  ) extends TransactionMessage
  
  /**
   * Internal state
   */
  private final case class ManagerState(
    transactions: mutable.Map[Long, Transaction] = mutable.Map.empty,
    categorySnapshots: mutable.Map[Int, CategorySnapshot] = mutable.Map.empty,
    stats: TransactionStats = TransactionStats()
  )
  
  private final case class TransactionStats(
    totalTransactions: Long = 0,
    committed: Long = 0,
    rolledBack: Long = 0
  )
  
  def apply(): Behavior[TransactionMessage] = {
    Behaviors.setup { context =>
      context.log.info("TransactionManager initialized")
      running(ManagerState())
    }
  }
  
  private def running(state: ManagerState): Behavior[TransactionMessage] = {
    Behaviors.receive { (context, message) =>
      message match {
        
        case BeginTransaction(txId, categoryId, snapshot) =>
          // Create new transaction
          val transaction = Transaction(
            transactionId = txId,
            categoryId = categoryId,
            snapshot = snapshot,
            startTime = System.currentTimeMillis(),
            status = Pending
          )
          
          state.transactions += (txId -> transaction)
          state.categorySnapshots += (categoryId -> snapshot)
          
          val newStats = state.stats.copy(
            totalTransactions = state.stats.totalTransactions + 1
          )
          
          context.log.debug(s"Transaction $txId begun for category $categoryId")
          
          running(state.copy(stats = newStats))
        
        case CommitTransaction(txId) =>
          state.transactions.get(txId) match {
            case Some(tx) =>
              val committed = tx.copy(status = Committed)
              state.transactions += (txId -> committed)
              
              val newStats = state.stats.copy(
                committed = state.stats.committed + 1
              )
              
              context.log.debug(s"Transaction $txId committed")
              running(state.copy(stats = newStats))
            
            case None =>
              context.log.warn(s"Commit failed: Transaction $txId not found")
              Behaviors.same
          }
        
        case RollbackTransaction(txId, replyTo) =>
          state.transactions.get(txId) match {
            case Some(tx) =>
              // Mark as rolled back
              val rolledBack = tx.copy(status = RolledBack)
              state.transactions += (txId -> rolledBack)
              
              val newStats = state.stats.copy(
                rolledBack = state.stats.rolledBack + 1
              )
              
              context.log.info(s"Transaction $txId rolled back (category ${tx.categoryId})")
              
              // Send snapshot back for restoration
              replyTo ! RollbackResult(txId, tx.snapshot, success = true)
              
              running(state.copy(stats = newStats))
            
            case None =>
              context.log.error(s"Rollback failed: Transaction $txId not found")
              replyTo ! RollbackResult(txId, null, success = false)
              Behaviors.same
          }
        
        case GetSnapshot(categoryId, replyTo) =>
          val snapshot = state.categorySnapshots.get(categoryId)
          replyTo ! SnapshotResponse(categoryId, snapshot)
          Behaviors.same
        
        case GetStatistics(replyTo) =>
          val pending = state.transactions.values.count(_.status == Pending)
          val rollbackRate = if (state.stats.totalTransactions > 0) {
            state.stats.rolledBack.toDouble / state.stats.totalTransactions.toDouble
          } else 0.0
          
          val stats = Statistics(
            totalTransactions = state.stats.totalTransactions,
            committed = state.stats.committed,
            rolledBack = state.stats.rolledBack,
            pending = pending,
            rollbackRate = rollbackRate
          )
          
          replyTo ! stats
          Behaviors.same
        
        case _ =>
          Behaviors.unhandled
      }
    }
  }
}
