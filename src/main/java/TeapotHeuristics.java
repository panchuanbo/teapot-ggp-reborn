import java.util.ArrayList;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class TeapotHeuristics {
	public StateMachine machine = null;
	public Role role = null;

	public boolean hasHeuristics = true;

	private double feasible = 0;

	/** Goal Heuristic **/
	private ArrayList<Double> goalHeuristicBuffer = new ArrayList<>();

	/** Focus Heuristic **/
	private ArrayList<Double> focusHeuristicBuffer = new ArrayList<>();

	/** NStep Heuristic (DISABLED) **/
	private ArrayList<Double> nStepHeuristicBuffer = new ArrayList<>();

	/** Mobility Heuristic **/
	private ArrayList<Double> mobilityHeuristicBuffer = new ArrayList<>();

	/** Final Scores **/
	private ArrayList<Double> finalScores = new ArrayList<>();

	private double[] heuristics = {};

	public void setData(StateMachine machine, Role role) {
		this.machine = machine; this.role = role;
	}

	public void calculate(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		this.goalHeuristicBuffer.clear(); this.mobilityHeuristicBuffer.clear(); this.focusHeuristicBuffer.clear();
		this.finalScores.clear();
		this.feasible = this.machine.findActions(this.role).size();

		MachineState initial = this.machine.getInitialState();

		int numSimulations = 0;
		while (System.currentTimeMillis() < timeout) {
			MachineState current = initial;

			double goal = goalHeuristic(current);
			double focus = focusHeuristic(current);
			double mobility = mobilityHeuristic(current);

			double numSteps = 1;

			// computeHeuristics(current);
			while (!this.machine.isTerminal(current)) {
				current = this.machine.getRandomNextState(current);
				// computeHeuristics(current);

				goal += goalHeuristic(current);
				focus += focusHeuristic(current);
				mobility += mobilityHeuristic(current);

				numSteps++;
			}

			double finalScore = this.machine.getGoal(current, this.role);

			this.goalHeuristicBuffer.add(goal / numSteps);
			this.focusHeuristicBuffer.add(focus / numSteps);
			this.nStepHeuristicBuffer.add(numSteps);
			this.mobilityHeuristicBuffer.add(mobility / numSteps);

			while (this.finalScores.size() < this.goalHeuristicBuffer.size()) {
				this.finalScores.add(finalScore);
			}

			numSimulations++;
		}

		System.out.println("[TeapotHeuristics] Games Simulated: " + numSimulations);

		double[] primScores = this.primitiveArray(this.finalScores);

		double goalHeuristic = new PearsonsCorrelation().correlation(this.primitiveArray(this.goalHeuristicBuffer), primScores);
		double focusHeuristic = new PearsonsCorrelation().correlation(this.primitiveArray(this.focusHeuristicBuffer), primScores);
		double nStepHeuristic = 0; //new PearsonsCorrelation().correlation(this.primitiveArray(this.nStepHeuristicBuffer), primScores);
		double mobilityHeuristic = new PearsonsCorrelation().correlation(this.primitiveArray(this.mobilityHeuristicBuffer), primScores);

		System.out.println("[TeapotHeuristics] Raw Heuristics:");
		System.out.println("\tGoal: " + goalHeuristic);
		System.out.println("\tFocus: " + focusHeuristic);
		System.out.println("\tNStep: " + nStepHeuristic);
		System.out.println("\tMobility: " + mobilityHeuristic);

		goalHeuristic = validate(goalHeuristic);
		focusHeuristic = validate(focusHeuristic);
		nStepHeuristic = validate(nStepHeuristic);
		mobilityHeuristic = validate(mobilityHeuristic);

		System.out.println("[TeapotHeuristics] Used Heuristics:");
		System.out.println("\tGoal: " + goalHeuristic);
		System.out.println("\tFocus: " + focusHeuristic);
		System.out.println("\tNStep: " + nStepHeuristic);
		System.out.println("\tMobility: " + mobilityHeuristic);

		this.heuristics = new double[]{goalHeuristic, focusHeuristic, mobilityHeuristic};

		double total = 0.0;
		for (double d : this.heuristics) {
			if (d != 0) { total++; }
		}

		if (total != 0) {
			for (int i = 0; i < this.heuristics.length; i++) {
				this.heuristics[i] /= total;
			}
			this.hasHeuristics = true;
		} else {
			this.hasHeuristics = false;
		}

		if (!this.hasHeuristics) System.out.println("[TeapotHeuristics] No Heuristics Enabled");
	}

	public double compute(MachineState state) throws GoalDefinitionException, MoveDefinitionException {
		if (!this.hasHeuristics) return 0.0;

		return ((this.heuristics[0] == 0) ? 0 : goalHeuristic(state) * this.heuristics[0])
				+ ((this.heuristics[1] == 0) ? 0 : focusHeuristic(state) * this.heuristics[1])
				+ ((this.heuristics[2] == 0) ? 0 : mobilityHeuristic(state) * this.heuristics[2]);
	}

	// MARK: - Private

	private void computeHeuristics(MachineState state) throws GoalDefinitionException, MoveDefinitionException {
		this.goalHeuristicBuffer.add(goalHeuristic(state));
		this.focusHeuristicBuffer.add(focusHeuristic(state));
		this.mobilityHeuristicBuffer.add(mobilityHeuristic(state));
	}

	private double goalHeuristic(MachineState state) throws GoalDefinitionException {
		return this.machine.getGoal(state, this.role);
	}

	private double focusHeuristic(MachineState state) throws MoveDefinitionException {
		try {
			//System.out.println(100.0 - (this.machine.getLegalMoves(state, this.role).size() / feasible) * 100);
			return 100.0 - (this.machine.getLegalMoves(state, this.role).size() / feasible) * 100;
		} catch (Exception e) {
			return 0;
		}
	}

	private double mobilityHeuristic(MachineState state) throws MoveDefinitionException {
		try {
			//System.out.println(this.machine.getLegalMoves(state, this.role).size() );
			return (this.machine.getLegalMoves(state, this.role).size() / feasible) * 100;
		} catch (Exception e) {
			return 0;
		}
	}

	private double validate(double g) {
		if (Double.isNaN(g) || g == Double.NEGATIVE_INFINITY || g == Double.POSITIVE_INFINITY) return 0;
		else if (g < 0.30) return 0.0;

		return g;
	}

	private double[] primitiveArray(ArrayList<Double> arr) {
		double[] prim = new double[arr.size()];

		for (int i = 0; i < arr.size(); i++) {
			prim[i] = arr.get(i).doubleValue();
		}

		return prim;
	}
}
