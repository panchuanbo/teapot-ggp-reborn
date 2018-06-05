import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlDistinct;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlRule;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.LegacyPropNetFactory;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

public class TeapotPropnetStateMachine extends StateMachine {

	/** The PropNet **/
	public PropNet propnet;

	/** Game Description **/
	public List<Gdl> description;

	/** Roles **/
	private List<Role> roles;

	/** Zero Sum **/
	private boolean zeroSum = true;

	/** All Base Props **/
	private Proposition[] basePropositions;

	/** All Input Props **/
	private Proposition[] inputPropositions;

	/** Maps Inputs **/
	private ArrayList<Map<GdlTerm, GdlSentence>> inputMap;

	/** Maps Role and Legals **/
	private Map<Role, Proposition[]> legalPropositions;

	@Override
	public void initialize(List<Gdl> description) {
		this.description = this.sanitizeDistinct(description);

		// Create the PropNet State Machine
		try {
			this.propnet = OptimizingPropNetFactory.create(this.description);
		} catch (InterruptedException e) {
			this.propnet = LegacyPropNetFactory.create(this.description);
		}

		// Get the Roles
		this.roles = this.propnet.getRoles();

		// Determine Zero-Sum Property
		this.determineZeroSum();
		if (this.zeroSum) System.out.println("[PropNet] This Game is ZeroSum");
		else System.out.println("[PropNet] This Game is NOT ZeroSum");

		// mark input/bases
		for (Proposition p : this.propnet.getInputPropositions().values()) p.setInput(true);
		for (Proposition p : this.propnet.getBasePropositions().values()) p.setBase(true);
		for (Role r : this.propnet.getRoles()) {
			for (Proposition p : this.propnet.getLegalPropositions().get(r)) p.setLegal(true);
			for (Proposition p : this.propnet.getGoalPropositions().get(r))  p.setGoal(true);
		}

		// Optimization
		this.optimizeViewPropositions();

		// Crystalization and Finalization
		for (Component c : this.propnet.getComponents()) c.crystalize();

		this.basePropositions = new Proposition[this.propnet.getBasePropositions().size()];
		this.inputPropositions = new Proposition[this.propnet.getInputPropositions().size()];
		this.legalPropositions = new HashMap<>();

		for (Component c : this.propnet.getComponents()) {
			if ((c instanceof Constant)) c.setPreviousValue(!c.getValue());
			// else if ((c instanceof Not)) c.setPreviousValue(!c.getValue());
			else c.setPreviousValue(false);
		}

		List<Proposition> bases = new ArrayList<Proposition>(this.propnet.getBasePropositions().values());
		for (int i = 0; i < this.basePropositions.length; i++) {
			this.basePropositions[i] = bases.get(i);
			this.basePropositions[i].setBase(true);
		}

		List<Proposition> inputs = new ArrayList<Proposition>(this.propnet.getInputPropositions().values());
		for (int i = 0; i < this.inputPropositions.length; i++) {
			this.inputPropositions[i] = inputs.get(i);
			this.inputPropositions[i].setInput(true);
		}

		Map<Role, Set<Proposition>> legalprops = this.propnet.getLegalPropositions();
		for (Role r : this.roles) {
			Set<Proposition> legals = legalprops.get(r);
			Proposition[] ps = new Proposition[legals.size()];
			int i = 0;
			for (Proposition p : legals) {
				ps[i] = p;
				i++;
			}
			this.legalPropositions.put(r, ps);
		}

		this.inputMap = new ArrayList<>();
		for (Role r : this.roles) {
			Set<Proposition> moves = this.propnet.getLegalPropositions().get(r);
			Map<GdlTerm, GdlSentence> buf = new HashMap<>();
			for (Proposition p : moves) {
				GdlSentence moveSentence = ProverQueryBuilder.toDoes(r, new Move(p.getName().get(1)));
				buf.put(p.getName().get(1), moveSentence);
			}
			this.inputMap.add(buf);
		}

		System.out.println("[PropNet] Finished Initializing StateMachine");
	}

	@Override
	public List<Move> findActions(Role role) throws MoveDefinitionException {
		Set<Proposition> legalProps = this.propnet.getLegalPropositions().get(role);
		ArrayList<Move> legalMoves = new ArrayList<Move>();
		for (Proposition p : legalProps) {
			legalMoves.add(TeapotPropnetStateMachine.getMoveFromProposition(p));
		}
		return legalMoves;
	}

	@Override
	public int getGoal(MachineState state, Role role) throws GoalDefinitionException {
		this.markbases(state);
		for (Component c : this.basePropositions) forwardprop(c);

		Set<Proposition> goalProps = this.propnet.getGoalPropositions().get(role);
		for (Proposition p : goalProps) {
			if (p.getValue()) return this.getGoalValue(p);
		}

		return 0;
	}

	@Override
	public boolean isTerminal(MachineState state) {
		this.markbases(state);
		for (Component c : this.basePropositions) forwardprop(c);
		return this.propnet.getTerminalProposition().getValue();
	}

	@Override
	public List<Role> getRoles() {
		return this.roles;
	}

	@Override
	public MachineState getInitialState() {
		for (Component c : this.propnet.getComponents()) { // check what subclass of component
			if ((c instanceof And)) ((And) c).useFastMethod = true;
			if ((c instanceof Or)) ((Or) c).useFastMethod = true;
			if ((c instanceof Transition)) ((Transition) c).useFastMode = true;
			if ((c instanceof Not)) ((Not) c).useFastMethod = true;
		}
		for (Component c : this.propnet.getComponents()) if ((c instanceof Constant)) forwardprop(c);
		Proposition initProp = this.propnet.getInitProposition();
		if (initProp != null) {
			this.propnet.getInitProposition().setValue(true);
			for (Component c : this.propnet.getComponents()) if ((c instanceof Not)) forwardprop(c);
			forwardprop(this.propnet.getInitProposition());
		}

		BitSet activeStates = new BitSet(this.basePropositions.length);
		for (int i = 0; i < this.basePropositions.length; i++) {
			boolean val = this.basePropositions[i].crystalizedGetSingleInput().getValue();
			activeStates.set(i, val);
		}

		System.out.println("[PropNet] INITIAL STATE VALUES: " + activeStates);

		if (initProp != null) {
			this.propnet.getInitProposition().setValue(false);
			forwardprop(this.propnet.getInitProposition());
			System.out.println("[PropNet] Turning off init prop...");
		}

		return new MachineState(activeStates);
	}

	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		this.markbases(state);
		for (Component c : this.basePropositions) forwardprop(c);
		Proposition[] legalProps = this.legalPropositions.get(role);
		ArrayList<Move> moves = new ArrayList<Move>();
		for (Proposition p : legalProps) {
			if (p.getValue()) moves.add(TeapotPropnetStateMachine.getMoveFromProposition(p));
		}
		return moves;
	}

	@Override
	public MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException {
		this.markactions(moves);
		this.markbases(state);
		for (Component c : this.basePropositions) forwardprop(c);
		for (Component c : this.inputPropositions) forwardprop(c);

		BitSet activeStates = new BitSet(this.basePropositions.length);
		for (int i = 0; i < this.basePropositions.length; i++) {
			boolean val = this.basePropositions[i].crystalizedGetSingleInput().getValue();
			activeStates.set(i, val);
		}

		return new MachineState(activeStates);
	}

	/////////////////////
	// PropNet Helpers //
	/////////////////////

	private void forwardprop(Component c) {
		boolean c_val = c.getValue();
		if (c_val == c.previousValue()) return;
		c.setPreviousValue(c_val);
		if ((c instanceof Transition)) return;
		for (Component o : c.crystalizedGetOutputs()) {
			if ((o instanceof Proposition)) {
				o.setPreviousValue(o.getValue());
				((Proposition) o).setValue(c_val);
			}
			else if ((o instanceof And)) ((And) o).counter += (c_val) ? 1 : -1;
			else if ((o instanceof Or)) ((Or) o).counter += (c_val) ? 1 : -1;
			else if ((o instanceof Not)) ((Not) o).reversed = !c_val;
			else if ((o instanceof Transition)) {
				o.setPreviousValue(o.getValue());
				((Transition) o).setValue(c_val);
			}
			forwardprop(o);
		}
	}

	private void markbases(MachineState s) {
		BitSet activeBits = s.getPropContents();
		for (int i = 0; i < this.basePropositions.length; i++) {
			this.basePropositions[i].setPreviousValue(this.basePropositions[i].getValue());
			this.basePropositions[i].setValue(activeBits.get(i));
		}
	}

	private void markactions(List<Move> moves) {
		for (Proposition p : this.inputPropositions) {
			p.setPreviousValue(p.getValue());
			p.setValue(false);
		}
		GdlSentence[] gdlMoves = toDoes(moves);
		for (GdlSentence m : gdlMoves) {
			this.propnet.getInputPropositions().get(m).setValue(true);
		}
	}

	private GdlSentence[] toDoes(List<Move> moves) {
		GdlSentence[] doeses = new GdlSentence[moves.size()];
		for (int i = 0; i < moves.size(); i++) {
			doeses[i] = this.inputMap.get(i).get(moves.get(i).getContents());
		}
		return doeses;
	}

	/////////////
	// Returns //
	/////////////

	public boolean isZeroSum() {
		return this.zeroSum;
	}

	/////////////////////
	// Generic Helpers //
	/////////////////////

	/**
	 * Determines whether or not the game is zero-sum
	 */
	private void determineZeroSum() {
		for (Role r : this.propnet.getRoles()) {
			for (Proposition p : this.propnet.getGoalPropositions().get(r)) {
				if (this.getGoalValue(p) != 0 && this.getGoalValue(p) != 100 && this.getGoalValue(p) != 50) {
					this.zeroSum = false;
				}
			}

			if (!this.zeroSum) break;
		}
	}

	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */
	private int getGoalValue(Proposition goalProposition) {
		GdlRelation relation = (GdlRelation) goalProposition.getName();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}

	/**
	 * Remove unused view propositions
	 */
	private void optimizeViewPropositions() {
		// optimization
		System.out.println("[PropNet] Begin Optimizing View Propositions");
		int numberTrimmed = 0;
		Set<Proposition> propsToRemove = new HashSet<>();
		for (Proposition p : this.propnet.getPropositions()) {
			if (p.isBase() || p.isInput() || p.isGoal() || p.isLegal()) continue;
			else if (p.equals(this.propnet.getInitProposition())) continue;
			else if (p.equals(this.propnet.getTerminalProposition())) continue;
			else if (this.propnet.getLegalInputMap().get(p) != null) continue;
			else {
				if (p.getInputs().size() == 1 && p.getOutputs().size() == 1) {
					Component inputComp = p.getSingleInput();
					Component outputComp = p.getSingleOutput();
					inputComp.removeOutput(p);
					outputComp.removeInput(p);
					inputComp.addOutput(outputComp);
					outputComp.addInput(inputComp);
					propsToRemove.add(p);
					numberTrimmed++;
				}
			}
		}
		for (Proposition p : propsToRemove) this.propnet.removeComponent(p);
		System.out.println("[PropNet] Trimmed " + numberTrimmed + " View Propositions");
	}

	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition p) {
		return new Move(p.getName().get(1));
	}

	/////////////////////
	// Sanitize Helper //
	/////////////////////

	private void sanitizeDistinctHelper(Gdl gdl, List<Gdl> in, List<Gdl> out) {
		if (!(gdl instanceof GdlRule)) {
			out.add(gdl);
			return;
		}
		GdlRule rule = (GdlRule) gdl;
		for (GdlLiteral lit : rule.getBody()) {
			if (lit instanceof GdlDistinct) {
				GdlDistinct d = (GdlDistinct) lit;
				GdlTerm a = d.getArg1();
				GdlTerm b = d.getArg2();
				if (!(a instanceof GdlFunction) && !(b instanceof GdlFunction)) continue;
				if (!(a instanceof GdlFunction && b instanceof GdlFunction)) return;
				GdlSentence af = ((GdlFunction) a).toSentence();
				GdlSentence bf = ((GdlFunction) b).toSentence();
				if (!af.getName().equals(bf.getName())) return;
				if (af.arity() != bf.arity()) return;
				for (int i = 0; i < af.arity(); i++) {
					List<GdlLiteral> ruleBody = new ArrayList<>();
					for (GdlLiteral newLit : rule.getBody()) {
						if (newLit != lit) ruleBody.add(newLit);
						else ruleBody.add(GdlPool.getDistinct(af.get(i), bf.get(i)));
					}
					GdlRule newRule = GdlPool.getRule(rule.getHead(), ruleBody);
					in.add(newRule);
				}
				return;
			}
		}
		for (GdlLiteral lit : rule.getBody()) {
			if (lit instanceof GdlDistinct) {
				break;
			}
		}
		out.add(rule);
	}

	private List<Gdl> sanitizeDistinct(List<Gdl> description) {
		List<Gdl> out = new ArrayList<>();
		for (int i = 0; i < description.size(); i++) {
			sanitizeDistinctHelper(description.get(i), description, out);
		}
		return out;
	}
}
