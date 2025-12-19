package edu.rit.dart.optimization

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import edu.rit.dart.optimization.TransactionManager._
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._

/**
 * Test suite for TransactionManager
 * 
 * Verifies:
 * - Transaction creation
 * - Snapshot storage and retrieval
 * - Commit functionality
 * - Rollback functionality
 * - Statistics tracking
 */
class TransactionManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  
  "TransactionManager" should {
    
    "begin and commit transactions" in {
      val manager = spawn(TransactionManager())
      val probe = createTestProbe[Statistics]()
      
      // Create snapshot
      val snapshot = CategorySnapshot(
        categoryId = 0,
        weights = Vector(1.0, 0.0, 1.0),
        numPresentations = 1,
        timestamp = System.currentTimeMillis()
      )
      
      // Begin transaction
      manager ! BeginTransaction(1L, 0, snapshot)
      
      // Commit transaction
      manager ! CommitTransaction(1L)
      
      // Check statistics
      manager ! GetStatistics(probe.ref)
      val stats = probe.receiveMessage()
      
      stats.totalTransactions should be(1)
      stats.committed should be(1)
      stats.rolledBack should be(0)
    }
    
    "rollback transactions correctly" in {
      val manager = spawn(TransactionManager())
      val rollbackProbe = createTestProbe[RollbackResult]()
      val statsProbe = createTestProbe[Statistics]()
      
      // Create snapshot
      val snapshot = CategorySnapshot(
        categoryId = 5,
        weights = Vector(1.0, 1.0, 0.0, 0.0),
        numPresentations = 3,
        timestamp = System.currentTimeMillis()
      )
      
      // Begin transaction
      manager ! BeginTransaction(2L, 5, snapshot)
      
      // Rollback transaction
      manager ! RollbackTransaction(2L, rollbackProbe.ref)
      val result = rollbackProbe.receiveMessage()
      
      result.success should be(true)
      result.transactionId should be(2L)
      result.snapshot should be(snapshot)
      
      // Check statistics
      manager ! GetStatistics(statsProbe.ref)
      val stats = statsProbe.receiveMessage()
      
      stats.totalTransactions should be(1)
      stats.committed should be(0)
      stats.rolledBack should be(1)
      stats.rollbackRate should be(1.0)
    }
    
    "store and retrieve snapshots" in {
      val manager = spawn(TransactionManager())
      val snapshotProbe = createTestProbe[SnapshotResponse]()
      
      val snapshot = CategorySnapshot(
        categoryId = 10,
        weights = Vector(0.5, 0.5, 0.5),
        numPresentations = 5,
        timestamp = System.currentTimeMillis()
      )
      
      // Begin transaction (stores snapshot)
      manager ! BeginTransaction(3L, 10, snapshot)
      
      // Retrieve snapshot
      manager ! GetSnapshot(10, snapshotProbe.ref)
      val response = snapshotProbe.receiveMessage()
      
      response.categoryId should be(10)
      response.snapshot should be(Some(snapshot))
    }
    
    "track multiple transactions" in {
      val manager = spawn(TransactionManager())
      val statsProbe = createTestProbe[Statistics]()
      
      // Create multiple transactions
      for (i <- 1 to 5) {
        val snapshot = CategorySnapshot(i, Vector.fill(3)(0.5), 1, System.currentTimeMillis())
        manager ! BeginTransaction(i.toLong, i, snapshot)
      }
      
      // Commit some
      manager ! CommitTransaction(1L)
      manager ! CommitTransaction(2L)
      
      // Rollback others
      val rollbackProbe = createTestProbe[RollbackResult]()
      manager ! RollbackTransaction(3L, rollbackProbe.ref)
      rollbackProbe.receiveMessage()
      
      manager ! RollbackTransaction(4L, rollbackProbe.ref)
      rollbackProbe.receiveMessage()
      
      // Check statistics
      manager ! GetStatistics(statsProbe.ref)
      val stats = statsProbe.receiveMessage()
      
      stats.totalTransactions should be(5)
      stats.committed should be(2)
      stats.rolledBack should be(2)
      stats.pending should be(1) // Transaction 5 still pending
      stats.rollbackRate should be(0.4) // 2/5 = 40%
    }
    
    "handle non-existent transaction rollback gracefully" in {
      val manager = spawn(TransactionManager())
      val rollbackProbe = createTestProbe[RollbackResult]()
      
      // Try to rollback non-existent transaction
      manager ! RollbackTransaction(999L, rollbackProbe.ref)
      val result = rollbackProbe.receiveMessage()
      
      result.success should be(false)
      result.transactionId should be(999L)
    }
    
    "calculate rollback rate correctly" in {
      val manager = spawn(TransactionManager())
      val statsProbe = createTestProbe[Statistics]()
      val rollbackProbe = createTestProbe[RollbackResult]()
      
      // Create 10 transactions
      for (i <- 1 to 10) {
        val snapshot = CategorySnapshot(i, Vector.fill(4)(0.5), 1, System.currentTimeMillis())
        manager ! BeginTransaction(i.toLong, i, snapshot)
      }
      
      // Commit 7, rollback 3
      for (i <- 1 to 7) {
        manager ! CommitTransaction(i.toLong)
      }
      
      for (i <- 8 to 10) {
        manager ! RollbackTransaction(i.toLong, rollbackProbe.ref)
        rollbackProbe.receiveMessage()
      }
      
      // Check rollback rate
      manager ! GetStatistics(statsProbe.ref)
      val stats = statsProbe.receiveMessage()
      
      stats.rollbackRate should be(0.3 +- 0.01) // 3/10 = 30%
    }
  }
}
