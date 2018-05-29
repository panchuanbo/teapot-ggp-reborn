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
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class TeapotPlayer extends StateMachineGamer {

	// NOTE: Consider using a more numerically stable method for average

	Player p;

	/////////////////
	//  CONSTANTS  //
	/////////////////

	public final static int TIMEOUT_BUFFER = 2500;

	private final static int CHARGES_PER_NODE = 1;
	private final static int NUM_THREADS = 4;
	private final static boolean MULTITHREADING_ENABLED = false;

	private final static double BRIAN_C_FACTOR = 12.5;

	private final static boolean USE_PROPNET = false;

	private static final boolean USE_ASP_SOLVER = false;
	private static final boolean SEED_HEURISTIC = false;
	private static final boolean USE_HEURISTICS = false;

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

	private TeapotHeuristics teapotHeuristics = new TeapotHeuristics();

	private int totalDepthCharges = 0;

	@Override
	public StateMachine getInitialStateMachine() {
		if (USE_PROPNET) {
			this.stateMachine = new TeapotPropnetStateMachine();
			this.solverStateMachine = new TeapotPropnetStateMachine();

			this.chargers = new DepthCharger[NUM_THREADS];
			this.multiStateMachine = new StateMachine[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) this.multiStateMachine[i] = new TeapotPropnetStateMachine();

			System.out.println("[Teapot] Using PropNet");
		} else {
			this.stateMachine = new CachedStateMachine(new ProverStateMachine());

			this.chargers = new DepthCharger[NUM_THREADS];
			this.multiStateMachine = new StateMachine[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) this.multiStateMachine[i] = new CachedStateMachine(new ProverStateMachine());

			System.out.println("[Teapot] Using Prover");
		}

		this.threads = new Thread[NUM_THREADS];
		for (int i = 0; i < NUM_THREADS; i++) {
			this.chargers[i] = new DepthCharger(this.multiStateMachine[i], null, null);
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
				this.chargers[i].setRole(getRole());
				System.out.println("[Teapot] Initialized Multithreaded Gamer #" + i);
			}
		}

		// Timeout
		this.timeout = timeout - TIMEOUT_BUFFER;

		// Heuristics
		this.teapotHeuristics.setData(this.stateMachine, getRole());
		this.teapotHeuristics.calculate(this.timeout - (this.timeout - System.currentTimeMillis()) / 2);

		// Create the new node!
		this.rootNode = makeNode(null, getCurrentState(), true, null);
		this.rootNode.level = 0;

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
		this.totalDepthCharges = 0;

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

		int mctsCycles = 0;
		while (!reachingTimeout()) {
			if (this.rootNode.finishedComputing && (this.rootNode.utility != 0 || this.rootNode.utility != EPSILON)) break;
			runMCTS(this.rootNode);
			mctsCycles++;
		}
		System.out.println("[Teapot] MCTS Cycles: " + mctsCycles);
		System.out.println("[Teapot] Depth Charges: " + this.totalDepthCharges);

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

		if (bestScore == 0 || bestScore == EPSILON) {
			System.out.println("[Teapot] Gamer Likely Lost - Selecting Best Move");
			System.out.println("[Select Move] Last-Resort Utilities:");
			for (Node n : this.rootNode.children) {
				double score = (n.visits > 0) ? n.total_utility / n.visits : 0;
				System.out.println("\t" + n.action + " (" + score + ")" + " (Visits: " + n.visits + ") " + (n.finishedComputing ? " (Solved)" : ""));
				if (score >= bestScore) {
					bestScore = score;
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
	// MCTS Implementation //
	/////////////////////////

	private void runMCTS(Node n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Node selected = null;
		double score = 0;

		if (!reachingTimeout()) selected = select(n);
		if (!reachingTimeout()) expand(selected);
		if (!reachingTimeout()) score = simulateDepthCharge(selected, CHARGES_PER_NODE);
		if (!reachingTimeout()) backpropagate(selected, score, 1, Double.NEGATIVE_INFINITY);
	}

	private Node select(Node n) throws GoalDefinitionException {
		if (reachingTimeout()) return null;

		if (n.state != null && (n.children == null)) return n;
		if (n.state != null && n.isTerminal) return n;
		if (n.state != null && n.visits <= 1) return n;

		int seed = (SEED_HEURISTIC) ? 1 : 0;

		for (Node nn : n.children) {
			if (nn.visits <= seed && nn.state != null) return nn;
			else if (nn.visits <= seed && nn.state == null) return select(nn);
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
		if (reachingTimeout()) return;
		if (n.finishedComputing) return;
		if (n.isTerminal) return;
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
					// backpropagate(child.children[j], child.children[j].utility, 1);
				}
			}

			// It's the same as parent, so we want to skip.
			child.state = null;
		}
	}

	private double simulateDepthCharge(Node n, double count) {
		// May the depth charge be with you

		if (MULTITHREADING_ENABLED) {
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
				score += chargers[i].getUtility();
			}

			return score / NUM_THREADS;
		} else {
			double total = 0, i = 0;

			for (; i < count; i++) {
				if (reachingTimeout() && i != 0) break;
				try {
					total += depthCharge(n.state);
					this.totalDepthCharges += 1;
				} catch (Exception e) {
					System.out.println("[Teapot] Depth Charge Error");
				}
			}

			return (i == 0) ? 0 : total / i;
		}
	}

	private void backpropagate(Node n, double score, int visit, double prevUtility) {
		if (n == null) return;

		// Quick Check
		if (n.isTerminal) n.finishedComputing = true;

		// Check if score is min of all scores

		/*
		if (this.stateMachine.findRoles().size() > 1) {
			if (n.maxnode && n.children != null) {
				double s = Double.NEGATIVE_INFINITY;
				for (Node nn : n.children) {
					if (nn.visits == 0) continue;
					if (nn.utility > s) s = nn.utility;
				}

				score = (score > s) ? score : s;
			} else if (!n.maxnode && n.children != null) {
				double s = Double.POSITIVE_INFINITY;
				for (Node nn : n.children) {
					if (nn.visits == 0) continue;
					if (nn.utility < s) s = nn.utility;
				}

				score = (score < s) ? score : s;
			}
		}
		 */


		// Store the average utility
		// if (!n.isTerminal) n.utility = (n.utility * n.visits + score) / (n.visits + 1.0);

		n.total_utility += score;
		n.visits += visit;
		if (!n.finishedComputing) n.utility = n.total_utility / n.visits;		// More Numerically Stable Calculation

		// Solver Starts
		if (n.children != null && n.finishedChildren.cardinality() == n.children.length) {
			n.backup_utility = n.utility;
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
			n.parent.backup_utility = n.parent.utility;
			n.parent.utility = n.utility;
		} else if (n.parent != null && !n.maxnode && n.finishedComputing && n.utility == 100) {
			n.parent.finishedComputing = true;
			n.parent.backup_utility = n.parent.utility;
			n.parent.utility = n.utility;
		}

		if (n.finishedComputing && n.parent != null) {
			n.parent.finishedChildren.set(n.indexInParent);
		}
		// Solver Ends

		backpropagate(n.parent, score, visit, n.utility);
	}

	/////////////////////
	// Select Function //
	/////////////////////

	private double selectfn(Node node) throws GoalDefinitionException {
		// double decay = (node.level <= 1) ? 1 : 1.0 / Math.log(node.level);
		double decay = Math.max(0.16, (100.0 - node.level / 2.0) / 100.0);
		double utility = node.utility;
		double heuristic = (SEED_HEURISTIC) ? 0 : node.heuristic;

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

		while (!machine.isTerminal(state) && !reachingTimeout()) {
			state = machine.getNextState(state, machine.getRandomJointMove(state));
		}
		int score = machine.getGoal(state, getRole());

		return score;
	}

	private Node makeNode(Node parent, MachineState state, boolean maxnode, Move action) throws GoalDefinitionException, MoveDefinitionException {
		Node newNode = new Node(parent, state, maxnode, action);

		if (state != null && this.stateMachine.isTerminal(state)) {
			newNode.isTerminal = true;
			newNode.utility = this.stateMachine.getGoal(state, getRole());
		}

		if (state != null && this.teapotHeuristics.hasHeuristics) {
			double heuristic = (USE_HEURISTICS) ? this.teapotHeuristics.compute(state) : 0;
			if (SEED_HEURISTIC) newNode.utility = heuristic;
			newNode.heuristic = heuristic;
			if (parent != null && parent.state == null) parent.heuristic += newNode.heuristic / parent.children.length;
		}

		if (this.teapotHeuristics.hasHeuristics && SEED_HEURISTIC) newNode.visits = 1;

		newNode.reference_utility = (newNode.maxnode) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		newNode.finishedChildren = new BitSet();

		if (parent != null) newNode.level = parent.level + 1;

		return newNode;
	}
}
