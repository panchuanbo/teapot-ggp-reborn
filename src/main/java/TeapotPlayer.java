import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class TeapotPlayer extends StateMachineGamer {

	Player p;

	/////////////////
	//  CONSTANTS  //
	/////////////////

	public final static int TIMEOUT_BUFFER = 2500;

	private final static int NEUTRAL_NODE = 0;
	private final static int LOSS_NODE = -1;
	private final static int WIN_NODE = 1;
	private final static int TERMINAL_NODE = 2;

	private final static int CHARGES_PER_NODE = 2;
	private final static int NUM_THREADS = 4;
	private final static boolean MULTITHREADING_ENABLED = false;

	private final static double BRIAN_C_FACTOR = 12.5;

	private final static boolean USE_PROPNET = true;

	private static final boolean USE_ASP_SOLVER = false;

	private static final double EPSILON = 4.9E-324;

	////////////////////
	//  Gamer System  //
	////////////////////

	private StateMachine stateMachine = null;
	private StateMachine[] multiStateMachine = null;
	private DepthCharger[] chargers = null;
	private Thread[] threads = null;

	private Node rootNode = null;

	private long timeout;

	private StateMachine solverStateMachine = null;
	private TeapotASPSolver teapotASP = new TeapotASPSolver();
	private ArrayList<Move> solverMoves = new ArrayList<>();

	@Override
	public StateMachine getInitialStateMachine() {
		if (USE_PROPNET) {
			this.stateMachine = new TeapotPropnetStateMachine();
			this.solverStateMachine = new TeapotPropnetStateMachine();

			this.chargers = new DepthCharger[NUM_THREADS];
			this.multiStateMachine = new StateMachine[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) this.multiStateMachine[i] = new TeapotPropnetStateMachine();
		} else {
			this.stateMachine = new ProverStateMachine();

			this.chargers = new DepthCharger[NUM_THREADS];
			this.multiStateMachine = new StateMachine[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) this.multiStateMachine[i] = new ProverStateMachine();
		}

		this.threads = new Thread[NUM_THREADS];
		for (int i = 0; i < NUM_THREADS; i++) {
			this.chargers[i] = new DepthCharger(this.multiStateMachine[i], null);
			threads[i] = new Thread(chargers[i]);
		}

		return this.stateMachine;
	}

	@SuppressWarnings("unused")
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		System.out.println("[Teapot] Metagame Start");

		this.solverMoves = null;

		if (USE_PROPNET && USE_ASP_SOLVER) {
			TeapotPropnetStateMachine solverSM = (TeapotPropnetStateMachine)this.solverStateMachine;

			solverSM.initialize(getMatch().getGame().getRules());
			this.teapotASP.setData(solverSM, solverSM.propnet, solverSM.description, getRole());
			this.solverMoves = this.teapotASP.solve(timeout);
		}

		if (MULTITHREADING_ENABLED) {
			for (int i = 0; i < NUM_THREADS; i++) {
				this.multiStateMachine[i].initialize(getMatch().getGame().getRules());
				System.out.println("[Teapot] Initialized Multithreaded Gamer #" + i);
			}
		}

		// Create the new node!
		this.rootNode = makeNode(null, getCurrentState(), true, null);
		this.rootNode.level = 0;

		this.timeout = timeout - TIMEOUT_BUFFER;

		// Begin Building MCTS Tree

		while (!reachingTimeout()) {
			if (this.rootNode.finishedComputing && this.rootNode.utility == 100) {
				System.out.println("[Metagame] Game Solved!");
				break;
			}
			runMCTS(this.rootNode);
		}

		System.out.println("[Teapot] Metagame End");
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		this.timeout = timeout - TIMEOUT_BUFFER;

		if (this.solverMoves != null && this.solverMoves.size() > 0) {
			Move s = this.solverMoves.get(0);
			this.solverMoves.remove(0);

			System.out.println("[Teapot] Solved Move: " + s);

			return s;
		}

		// Recreate the Root Node
		Node newRoot = null;

		if (this.rootNode.state.equals(getCurrentState())) {
			newRoot = this.rootNode;
		} else {
			if (this.rootNode.children != null) {
				for (Node n : this.rootNode.children) {
					if (n.children == null) continue;
					for (Node nn : n.children) {
						if (nn.state.equals(getCurrentState())) {
							newRoot = nn;
						}
						if (newRoot != null) break;
					}
					if (newRoot != null) break;
				}
			} else {
				System.out.println("[Select Move] Root Node has no children");
			}
		}

		if (newRoot == null) {
			System.out.println("[Select Move] Lost root node, regenerating...");
			this.rootNode = makeNode(null, getCurrentState(), true, null);
		} else {
			System.out.println("[Select Move] Recovered Root Node from Sub-Tree.");
			newRoot.parent = null;
			this.rootNode = newRoot;
		}

		while (!reachingTimeout()) {
			if (this.rootNode.finishedComputing) break;
			runMCTS(this.rootNode);
		}

		double bestScore = 0.0;
		Node selectedNode = null;

		System.out.println("[Select Move] Utilities:");
		for (Node n : this.rootNode.children) {
			System.out.println("\t" + n.action + " (" + n.utility + ")" + " (Visits: " + n.visits + ") " + (n.finishedComputing ? " (Solved)" : ""));
			if (n.utility >= bestScore) {
				if (n.utility == 100 && n.finishedComputing) {
					bestScore = Double.POSITIVE_INFINITY;
					selectedNode = n;
				} else {
					bestScore = n.utility;
					selectedNode = n;
				}
			}
		}

		if (selectedNode != null) {
			System.out.println("[Select Move] Move: " + selectedNode.action + " Utility: " + selectedNode.utility);
			return selectedNode.action;
		}

		return this.stateMachine.getLegalMoves(getCurrentState(), getRole()).get(0);
	}

	@Override
	public void stateMachineStop() {
		System.out.println("[State Machine] State Machine Stopped.");
	}

	@Override
	public void stateMachineAbort() {
		System.out.println("[State Machine] State Machine Aborted.");
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		return "418 I'm a New Teapot";
	}

	/////////////////////////
	// Node Implementation //
	/////////////////////////

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

		int visits = 0;

		double utility = 0; 			// Average Utility
		double actual_utility = 0;	// Total Utility
		double mean = 0;
		double variance = 0;

		// SOLVER
		int status = NEUTRAL_NODE;
		BitSet finishedChildren;

		// HEURISTICS
		double heuristic = 0.0;
		double heuristicCtr = 0.0;

		int expandedUpTo = 0; // node we've expanded up too
		Node[] children = null; //all the immediate states following this one

		// debug data
		int level = 0;

		public Node(Node parent, MachineState state, boolean maxnode, Move action) {
			this.parent = parent; this.state = state; this.maxnode = maxnode; this.action = action;
		}
	}

	public class DepthCharger implements Runnable {
		private volatile int utility = 0;

		private StateMachine machine = null;
		private MachineState state = null;

		DepthCharger(StateMachine machine, MachineState state) {
			this.machine = machine; this.state = state;
		}

		public void setState(MachineState state) { this.state = state; this.utility = 0; }

		@Override
		public void run() {
			try {
				utility = depthCharge();
			} catch (Exception e) {
				System.out.println("[Depth Charger] Error With Depth Charge");
				e.printStackTrace();
			}
		}

		private int depthCharge() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
			while (!machine.isTerminal(state)) state = machine.getNextState(state, machine.getRandomJointMove(state));
			return machine.getGoal(state, getRole());
		}

		public int getUtility() {
			return utility;
		}
	}

	/////////////////////////
	// MCTS Implementation //
	/////////////////////////

	private void runMCTS(Node n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Node selected = select(n);
		expand(selected);
		double score = simulateDepthCharge(selected, CHARGES_PER_NODE);
		backpropagate(selected, score, 1);
	}

	private Node select(Node n) throws GoalDefinitionException {
		if (n.state != null && (n.children == null)) return n;
		if (n.state != null && this.stateMachine.isTerminal(n.state)) return n;
		if (n.state != null && n.visits == 0) return n;

		for (Node nn : n.children) {
			if (nn.visits == 0 && nn.state != null) return nn;
			else if (nn.visits == 0 && nn.state == null) return select(nn);
		}

		double score = Double.NEGATIVE_INFINITY;
		Node selected = null;

		for (Node nn : n.children) {
			if (nn.finishedComputing) continue;

			double s = selectfn(nn);

			if (s >= score) {
				score = s;
				selected = nn;
			}
		}

		return (selected == null) ? n : select(selected);
	}

	private void expand(Node n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (n.finishedComputing) return;
		if (n.children != null) return;

		List<Move> moves = this.stateMachine.getLegalMoves(n.state, getRole());

		n.children = new Node[moves.size()];

		// Create Children
		for (int i = 0; i < moves.size(); i++) {
			n.children[i] = makeNode(n, n.state, !n.maxnode, moves.get(i));

			// Grandchildren

			Node child = n.children[i];
			child.indexInParent = i;

			List<List<Move>> jointMoves = this.stateMachine.getLegalJointMoves(child.state, getRole(), moves.get(i));
			child.children = new Node[jointMoves.size()];

			for (int j = 0; j < jointMoves.size(); j++) {
				MachineState newState = this.stateMachine.getNextState(child.state, jointMoves.get(j));

				child.children[j] = makeNode(child, newState, !child.maxnode, child.action);

				child.children[j].indexInParent = j;

				if (child.children[j].finishedComputing) {
					// backpropagate(child.children[j], child.children[j].utility, 0);
				}
			}

			// It's the same as parent, so we want to skip.
			child.state = null;
		}
	}

	private double simulateDepthCharge(Node n, double count) {
		// May the depth charge be with you

		if (MULTITHREADING_ENABLED) {
			for (int i = 0; i < NUM_THREADS; i++) threads[i] = new Thread(chargers[i]);

			for (int i = 0; i < NUM_THREADS; i++) {
				chargers[i].setState(n.state);
				threads[i].start();
			}

			for (int i = 0; i < NUM_THREADS; i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					System.out.println("[Teapot] (Multithreading) Depth Charge Error");
					e.printStackTrace();
				}
			}

			double score = 0;

			for (int i = 0; i < NUM_THREADS; i++) {
				score += chargers[i].utility;
			}

			return score / NUM_THREADS;
		} else {
			double total = 0, i = 0;

			for (; i < count; i++) {
				if (reachingTimeout() && i != 0) break;
				try {
					total += depthCharge(n.state);
				} catch (Exception e) {
					System.out.println("[Teapot] Depth Charge Error");
				}
			}

			return (i == 0) ? 0 : total / i;
		}
	}

	private void backpropagate(Node n, double score, int visit) {
		if (n == null) return;

		// Store the average utility
		n.utility = (n.utility * n.visits + score) / (n.visits + 1);
		n.visits += visit;

		// Solver Starts
		if (n.children != null && n.finishedChildren.cardinality() == n.children.length) {
			n.finishedComputing = true;

			double util = (n.maxnode) ? Double.MIN_VALUE : Double.MAX_VALUE;
			for (Node nn : n.children) {
				if (nn == null) continue;
				if (n.maxnode && nn.utility > util) util = nn.utility;
				else if (!n.maxnode && nn.utility < util) util = nn.utility;
			}

			n.utility = util;
		}

		if (n.parent != null && n.maxnode && n.finishedComputing && (n.utility == 0 || n.utility == EPSILON)) {
			n.parent.finishedComputing = true;
			n.parent.utility = n.utility;
		} else if (n.parent != null && !n.maxnode && n.finishedComputing && n.utility == 100) {
			n.parent.finishedComputing = true;
			n.parent.utility = n.utility;
		}

		if (n.finishedComputing && n.parent != null) {
			n.parent.finishedChildren.set(n.indexInParent);
		}
		// Solver Ends

		backpropagate(n.parent, score, visit);
	}

	/////////////////////
	// Select Function //
	/////////////////////

	private double selectfn(Node node) throws GoalDefinitionException {
		double decay = 1.0;//2.0 / node.level;
		double utility = node.utility;
		double heuristic = node.heuristic;

		double c_value = BRIAN_C_FACTOR * decay;

		if (node.finishedComputing && node.utility == 0) return Double.NEGATIVE_INFINITY;

		if (this.stateMachine.findRoles().size() == 1) {
			return (utility + heuristic) + c_value * Math.sqrt(Math.log(node.parent.visits)/node.visits);
		}

		if (node.maxnode) {
			return -(utility + heuristic) + c_value * Math.sqrt(Math.log(node.parent.visits)/node.visits);
		} else {
			return (utility + heuristic) + c_value * Math.sqrt(Math.log(node.parent.visits)/node.visits);
		}
	}

	//////////////
	// Timeout! //
	//////////////

	private boolean reachingTimeout() {
		return System.currentTimeMillis() > this.timeout;
	}

	//////////////
	//  Helper  //
	//////////////

	private int depthCharge(MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		while (!machine.isTerminal(state)) state = machine.getNextState(state, machine.getRandomJointMove(state));
		int score = machine.getGoal(state, getRole());

		return score;
	}

	private Node makeNode(Node parent, MachineState state, boolean maxnode, Move action) throws GoalDefinitionException {
		Node newNode = new Node(parent, state, maxnode, action);

		if (state != null && this.stateMachine.isTerminal(state)) {
			newNode.finishedComputing = true;
			newNode.utility = this.stateMachine.getGoal(state, getRole());
		}

		if (state != null) {
			newNode.heuristic = this.stateMachine.findReward(getRole(), state);

			if (parent != null && parent.state == null) parent.heuristic += newNode.heuristic / parent.children.length;
		}

		newNode.finishedChildren = new BitSet();

		if (parent != null) newNode.level = parent.level + 1;

		return newNode;
	}
}
