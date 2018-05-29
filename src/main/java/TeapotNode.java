import java.util.BitSet;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

class Node {
	// The critical portions
	Node parent = null;
	Move action = null;
	MachineState state = null;
	int indexInParent = 0;

	// true if maxnode, false if min-node
	boolean maxnode;

	// All subnodes have been solved
	boolean finishedComputing = false;

	// Terminal Node
	boolean isTerminal = false;

	int visits = 0;

	double utility = 0; 				// Average Utility
	double backup_utility = 0;		// Previous (non-solved) Utility
	double reference_utility = 0; 	// Method to fill for minimax
	double total_utility = 0;		// Sum of all utilities

	// SOLVER
	BitSet finishedChildren;

	// HEURISTICS
	double heuristic = 0.0;
	double heuristicCtr = 0.0;

	Node[] children = null; //all the immediate states following this one

	// debug data
	int level = 0;

	public Node(Node parent, MachineState state, boolean maxnode, Move action) {
		this.parent = parent; this.state = state; this.maxnode = maxnode; this.action = action;
	}
}