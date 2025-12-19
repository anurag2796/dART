#!/usr/bin/env python3
"""
NumPy Baseline Implementation of ART-1

This is a traditional synchronous implementation of ART-1 using NumPy
for comparison with the distributed Actor-based implementation.

Based on: Carpenter & Grossberg (1987) ART-1 algorithm
"""

import numpy as np
from typing import List, Tuple, Optional
import time


class ART1Network:
    """
    Synchronous ART-1 neural network implementation.
    
    Parameters:
    -----------
    input_size : int
        Size of binary input patterns
    max_categories : int
        Maximum number of categories to learn
    vigilance : float
        Vigilance parameter (0.0 to 1.0)
        Higher = more fine-grained categories
    learning_rate : float
        Fast learning rate (typically 1.0 for ART-1)
    """
    
    def __init__(
        self,
        input_size: int,
        max_categories: int = 100,
        vigilance: float = 0.7,
        learning_rate: float = 1.0
    ):
        self.input_size = input_size
        self.max_categories = max_categories
        self.vigilance = vigilance
        self.learning_rate = learning_rate
        
        # F2 category prototypes (weights)
        # Initially empty, created on demand
        self.categories: List[np.ndarray] = []
        self.num_presentations = []
        
        # Statistics
        self.total_patterns = 0
        self.creation_count = 0
        self.recognition_count = 0
        
    def normalize_binary(self, pattern: np.ndarray) -> np.ndarray:
        """Convert to binary (0 or 1)"""
        return (pattern > 0.5).astype(float)
    
    def compute_match(self, input_pattern: np.ndarray, expectation: np.ndarray) -> float:
        """
        Compute match degree between input and expectation.
        
        match = |input ∧ expectation| / |input|
        """
        intersection = np.logical_and(input_pattern, expectation).sum()
        input_magnitude = input_pattern.sum()
        
        if input_magnitude == 0:
            return 1.0
        
        return intersection / input_magnitude
    
    def compute_activation(self, input_pattern: np.ndarray, weights: np.ndarray) -> float:
        """
        Compute F2 activation for a category.
        
        T_j = |input ∧ weights| / (α + |weights|)
        
        where α is a small constant (0.1)
        """
        alpha = 0.1
        intersection = np.logical_and(input_pattern, weights).sum()
        weight_magnitude = weights.sum()
        
        return intersection / (alpha + weight_magnitude)
    
    def select_winner(
        self,
        input_pattern: np.ndarray,
        inhibited: set
    ) -> Optional[int]:
        """
        Select winning category (highest activation, excluding inhibited).
        
        Returns:
        --------
        category_id : int or None
            Index of winning category, or None if no valid category
        """
        if len(self.categories) == 0:
            return None
        
        activations = []
        for i, weights in enumerate(self.categories):
            if i in inhibited:
                activations.append(-np.inf)
            else:
                activations.append(self.compute_activation(input_pattern, weights))
        
        max_activation = max(activations)
        if max_activation == -np.inf:
            return None
        
        return int(np.argmax(activations))
    
    def update_weights(self, category_id: int, input_pattern: np.ndarray):
        """
        Update category weights using fast learning.
        
        For L = 1.0 (fast learning):
        w_new = input ∧ w_old
        """
        if self.learning_rate >= 0.99:
            # Fast learning: intersection only
            self.categories[category_id] = np.logical_and(
                input_pattern,
                self.categories[category_id]
            ).astype(float)
        else:
            # Slow learning: interpolation
            intersection = np.logical_and(input_pattern, self.categories[category_id]).astype(float)
            self.categories[category_id] = (
                self.learning_rate * intersection +
                (1 - self.learning_rate) * self.categories[category_id]
            )
        
        self.num_presentations[category_id] += 1
    
    def create_category(self, input_pattern: np.ndarray) -> int:
        """
        Create new category initialized to input pattern.
        
        Returns:
        --------
        category_id : int
            Index of newly created category
        """
        if len(self.categories) >= self.max_categories:
            raise ValueError(f"Maximum categories ({self.max_categories}) reached")
        
        self.categories.append(input_pattern.copy())
        self.num_presentations.append(1)
        self.creation_count += 1
        
        return len(self.categories) - 1
    
    def present_pattern(self, pattern: np.ndarray) -> Tuple[int, bool, float]:
        """
        Present a pattern to the network for learning/recognition.
        
        Returns:
        --------
        category_id : int
            Selected category
        is_new : bool
            True if new category was created
        match_degree : float
            Final match degree
        """
        self.total_patterns += 1
        
        # Normalize input
        input_pattern = self.normalize_binary(pattern)
        
        # Match-reset-search loop
        inhibited = set()
        
        while True:
            # Select winner
            winner = self.select_winner(input_pattern, inhibited)
            
            if winner is None:
                # No valid category, create new one
                if len(self.categories) < self.max_categories:
                    category_id = self.create_category(input_pattern)
                    return category_id, True, 1.0
                else:
                    # All categories inhibited and max reached - use best available
                    # This shouldn't happen in practice, but handle gracefully
                    # Just return first inhibited category
                    if len(inhibited) > 0:
                        fallback = list(inhibited)[0]
                        return fallback, False, 0.0
                    raise ValueError("No categories available and max reached")
            
            # Compute match
            expectation = self.categories[winner]
            match_degree = self.compute_match(input_pattern, expectation)
            
            # Check vigilance
            if match_degree >= self.vigilance:
                # Match succeeded - resonance!
                self.update_weights(winner, input_pattern)
                self.recognition_count += 1
                return winner, False, match_degree
            else:
                # Match failed - reset and search
                inhibited.add(winner)
                # Continue loop to find another category
    
    def get_statistics(self) -> dict:
        """Get network statistics"""
        return {
            'total_patterns': self.total_patterns,
            'num_categories': len(self.categories),
            'new_categories': self.creation_count,
            'recognitions': self.recognition_count,
            'vigilance': self.vigilance
        }


def benchmark_art1(
    input_size: int = 64,
    num_patterns: int = 1000,
    vigilance: float = 0.7
) -> dict:
    """
    Benchmark ART-1 performance.
    
    Returns timing and accuracy metrics.
    """
    # Generate random binary patterns
    np.random.seed(42)
    patterns = (np.random.rand(num_patterns, input_size) > 0.5).astype(float)
    
    # Create network
    network = ART1Network(
        input_size=input_size,
        max_categories=100,
        vigilance=vigilance,
        learning_rate=1.0
    )
    
    # Measure learning time
    start_time = time.time()
    
    results = []
    for i, pattern in enumerate(patterns):
        category, is_new, match = network.present_pattern(pattern)
        results.append((category, is_new, match))
    
    end_time = time.time()
    learning_time = end_time - start_time
    
    # Compute metrics
    stats = network.get_statistics()
    stats['learning_time_ms'] = learning_time * 1000
    stats['throughput'] = num_patterns / learning_time  # patterns/sec
    stats['avg_time_per_pattern_ms'] = (learning_time / num_patterns) * 1000
    
    return stats


if __name__ == '__main__':
    print("=" * 60)
    print("NumPy Baseline ART-1 Benchmark")
    print("=" * 60)
    print()
    
    # Test configurations
    configs = [
        {'input_size': 8, 'num_patterns': 100, 'vigilance': 0.7},
        {'input_size': 64, 'num_patterns': 1000, 'vigilance': 0.7},
        {'input_size': 256, 'num_patterns': 1000, 'vigilance': 0.7},
    ]
    
    for config in configs:
        print(f"Configuration: {config}")
        stats = benchmark_art1(**config)
        
        print(f"  Categories created: {stats['num_categories']}")
        print(f"  Learning time: {stats['learning_time_ms']:.2f} ms")
        print(f"  Throughput: {stats['throughput']:.1f} patterns/sec")
        print(f"  Avg time/pattern: {stats['avg_time_per_pattern_ms']:.3f} ms")
        print()
    
    print("=" * 60)
    print("Baseline benchmark complete!")
    print("=" * 60)
