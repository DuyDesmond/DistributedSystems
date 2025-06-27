package com.filesync.demo;

import java.util.HashMap;
import java.util.Map;

import com.filesync.common.model.VersionVector;

/**
 * Demonstration of version vector conflict detection
 */
public class VersionVectorDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Distributed File Synchronizer - Version Vector Demo ===\n");
        
        // Simulate three clients working on the same file
        demonstrateVersionVectorConflicts();
        
        System.out.println("\n=== Demo completed successfully! ===");
    }
    
    private static void demonstrateVersionVectorConflicts() {
        System.out.println("1. Creating initial version vectors for three clients:");
        
        // Client A makes first change
        VersionVector clientA = new VersionVector();
        clientA.increment("client-A");
        System.out.println("   Client A: " + clientA.toJson());
        
        // Client B makes first change (concurrent)
        VersionVector clientB = new VersionVector();
        clientB.increment("client-B");
        System.out.println("   Client B: " + clientB.toJson());
        
        // Client C starts from A's version and makes a change
        VersionVector clientC = new VersionVector(clientA.getVectors());
        clientC.increment("client-C");
        System.out.println("   Client C: " + clientC.toJson());
        
        System.out.println("\n2. Conflict Detection Analysis:");
        
        // Check for conflicts between A and B (concurrent)
        if (clientA.isConcurrentWith(clientB)) {
            System.out.println("   ⚠️  CONFLICT: Client A and B have concurrent changes!");
            System.out.println("       Neither dominates the other - manual resolution needed");
        }
        
        // Check relationship between A and C
        if (clientC.dominates(clientA)) {
            System.out.println("   ✅ Client C dominates Client A - C has newer version");
        }
        
        // Check relationship between B and C (concurrent)
        if (clientB.isConcurrentWith(clientC)) {
            System.out.println("   ⚠️  CONFLICT: Client B and C have concurrent changes!");
        }
        
        System.out.println("\n3. Conflict Resolution - Merging vectors:");
        
        // Merge A and B
        VersionVector mergedAB = clientA.merge(clientB);
        System.out.println("   Merged A+B: " + mergedAB.toJson());
        
        // Merge with C
        VersionVector finalMerged = mergedAB.merge(clientC);
        System.out.println("   Final merged: " + finalMerged.toJson());
        
        System.out.println("\n4. Verification:");
        System.out.println("   Final vector dominates A: " + finalMerged.dominates(clientA));
        System.out.println("   Final vector dominates B: " + finalMerged.dominates(clientB));
        System.out.println("   Final vector dominates C: " + finalMerged.dominates(clientC));
        
        // Simulate server incrementing for conflict resolution
        finalMerged.increment("server");
        System.out.println("   Server resolution: " + finalMerged.toJson());
        
        System.out.println("\n5. Real-world scenario simulation:");
        simulateRealWorldSync();
    }
    
    private static void simulateRealWorldSync() {
        System.out.println("   Scenario: Document.txt being edited by multiple users");
        
        Map<String, VersionVector> clientVectors = new HashMap<>();
        
        // Alice starts editing
        VersionVector alice = new VersionVector();
        alice.increment("alice");
        clientVectors.put("alice", alice);
        System.out.println("   Alice saves: " + alice.toJson());
        
        // Bob starts from Alice's version
        VersionVector bob = new VersionVector(alice.getVectors());
        bob.increment("bob");
        clientVectors.put("bob", bob);
        System.out.println("   Bob saves: " + bob.toJson());
        
        // Charlie starts from original and makes changes (conflict!)
        VersionVector charlie = new VersionVector();
        charlie.increment("charlie");
        clientVectors.put("charlie", charlie);
        System.out.println("   Charlie saves: " + charlie.toJson());
        
        // Server processes synchronization
        System.out.println("\n   Server conflict detection:");
        for (Map.Entry<String, VersionVector> entry1 : clientVectors.entrySet()) {
            for (Map.Entry<String, VersionVector> entry2 : clientVectors.entrySet()) {
                if (!entry1.getKey().equals(entry2.getKey())) {
                    String client1 = entry1.getKey();
                    String client2 = entry2.getKey();
                    VersionVector v1 = entry1.getValue();
                    VersionVector v2 = entry2.getValue();
                    
                    if (v1.isConcurrentWith(v2)) {
                        System.out.println("   🔥 CONFLICT between " + client1 + " and " + client2);
                    } else if (v1.dominates(v2)) {
                        System.out.println("   ⬆️  " + client1 + " has newer version than " + client2);
                    }
                }
            }
        }
        
        // Create final merged version
        VersionVector serverVector = new VersionVector();
        for (VersionVector cv : clientVectors.values()) {
            serverVector = serverVector.merge(cv);
        }
        serverVector.increment("server-merge");
        
        System.out.println("   Final server version: " + serverVector.toJson());
        System.out.println("   All conflicts resolved ✅");
    }
}
