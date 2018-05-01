import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

public class TeapotASPSolver {

	private static final boolean DEBUG = true;

	public StateMachine machine = null;
	public PropNet propnet = null;
	public List<Gdl> description;
	public Role role = null;

	public void setData(StateMachine machine, PropNet propnet, List<Gdl> description, Role role) {
		this.machine = machine; this.propnet = propnet; this.description = description; this.role = role;
	}

	public ArrayList<Move> solve(long timeout) {
		if (this.machine == null) return null;
		if (this.propnet == null) return null;
		if (this.role == null) return null;

		int numberOfSteps = this.getNumSteps();
		int numberOfRoles = this.propnet.getRoles().size();
		int uselessRoles  = this.getNumUselessRoles();

		boolean solved = false;

		ArrayList<Move> solverMoves = new ArrayList<>();

		try{
			if (numberOfRoles - uselessRoles == 1) {
				PrintWriter writer = new PrintWriter("input.txt", "UTF-8");
				System.out.println("[TeapotASP] Begin Conversion of GDL from Prefix --> Answer Set Programming");
				for (Gdl g : this.description) {
					String s = g.toASPString();
					s = s.replaceAll("[?]", "Var");
					if (s.contains(",T)")) {
						if (s.contains(":-")) s += ", time(T)";
						else s += " :- time(T)";
					} else if (s.contains("(T)")) {
						if (s.contains(":-")) s += ", time(T)";
						else s += " :- time(T)";
					}
					s += ".";
					writer.println(s);
				}
				writer.println("1 { does(R,M,T) : input(R,M) } 1 :- role(R), not terminated(T), time(T).");
				writer.println("terminated(T) :- terminal(T), time(T).");
				writer.println("terminated(T+1) :- terminated(T), time(T).");
				writer.println(":- does(R,M,T), not legal(R,M,T).");
				writer.println(":- 0 { terminated(T) : time(T) } 0.");
				writer.println(":- terminated(T), not terminated(T-1), role(R), not goal(R,100,T).");
				writer.println("time(1.." + numberOfSteps + ").");
				writer.close();
				System.out.println("[TeapotASP] Finished conversion");

				Set<Proposition> legalProps = propnet.getLegalPropositions().get(this.role);
				Map<String, Move> tempMap = new HashMap<>();
				for (Proposition pp : legalProps) {
					Proposition does = propnet.getLegalInputMap().get(pp);
					String doesString = does.getName().infixString();
					doesString = doesString.replaceAll("\\s+","");

					tempMap.put(doesString, TeapotPropnetStateMachine.getMoveFromProposition(pp));
				}

				System.out.println("[TeapotASP] Running solver");
				// Process p = Runtime.getRuntime().exec(new String[]{"C:\\clingo-5.2.0-win64\\clingo.exe","input.txt"});
				Process p = Runtime.getRuntime().exec(new String[]{"/usr/local/bin/clingo","input.txt"});
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

				String line = "";
				ArrayList<String> temp = new ArrayList<String>();
				while (true) {
					if (timeout - TeapotPlayer.TIMEOUT_BUFFER - 1500 < System.currentTimeMillis()) {
						p.destroy();
						break;
					}
					if (reader.ready()) {
						if (reader.read() == -1) break;
						line = reader.readLine();
						String[] tokens = line.split(" ");
						for (String s : tokens) {
							if (s.contains("does")) {
								if (DEBUG) System.out.println("[" + role.toString() + "]");
								if (numberOfRoles != 1) {
									if (s.contains(role.toString())) {
										if (DEBUG) System.out.println("[TeapotASP] Found " + s);
										temp.add(s);
									}
								} else {
									if (DEBUG) System.out.println("[TeapotASP] Found " + s);
									temp.add(s);
								}
							}
						}
					}
				}

				p.waitFor(timeout - System.currentTimeMillis() - TeapotPlayer.TIMEOUT_BUFFER - 1500, TimeUnit.MILLISECONDS);
				System.out.println("[TeapotASP] ASP Solver complete.");

				for (int i = 0; i < temp.size(); i++) solverMoves.add(null);
				for (String s : temp) {
					String[] parts = s.split(",");
					String lastPart = parts[parts.length - 1];
					lastPart = lastPart.replaceAll("[^0-9]", "");
					int index = Integer.parseInt(lastPart);
					s += ";";
					String sol = s.replaceAll(",[0-9]+?[)];", ")");
					solverMoves.set(index-1, tempMap.get(sol));
				}
				System.out.println("[TeapotASP] Potentially solved with moves " + solverMoves);
				if (solverMoves.size() > 0) {
					solved = true;
					for (int i = 0; i < solverMoves.size(); i++) {
						if (solverMoves.get(i) == null) {
							solved = false;
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("error: " + e);
		}

		return solverMoves.size() == 0 ? null : solverMoves;
	}

	/** Gets the total number of useless roles by analyzing the propnet **/
	private int getNumUselessRoles() {
		int uselessRoles = 0;

		if (propnet.getRoles().size() > 1) {
			Map<Role, Set<Proposition>> rolePropMap = new HashMap<>();
			for (Role r : propnet.getRoles()) {
				Set<Proposition> sp = new HashSet<>();
				for (Proposition p : propnet.getLegalPropositions().get(r)) {
					Proposition does = propnet.getLegalInputMap().get(p);
					forwardprop(does, false);
				}
				for (Proposition p : propnet.getBasePropositions().values()) {
					if (p.connectedToTerminal) sp.add(p);
				}
				for (Component c : propnet.getComponents()) c.connectedToTerminal = false;
				for (Proposition p : sp) {
					forwardprop(p, true);
				}
				for (Proposition p : propnet.getBasePropositions().values()) {
					if (p.connectedToTerminal) sp.add(p);
				}
				for (Component c : propnet.getComponents()) c.connectedToTerminal = false;
				rolePropMap.put(r, sp);
			}

			Set<Proposition> mySet = rolePropMap.get(this.role);
			for (Role r : propnet.getRoles()) {
				if (r.equals(this.role)) continue;
				Set<Proposition> mine = new HashSet<>(mySet);
				Set<Proposition> theirs = rolePropMap.get(r);
				mine.retainAll(theirs);
				if (mine.size() == 0) uselessRoles++;
			}

			System.out.println("[TeapotASP] Useless roles: " + uselessRoles);
		}

		return uselessRoles;
	}

	/** Find the Step Counter **/
	private int getNumSteps() {
		int numberOfSteps = 0;

		Proposition initProposition = this.propnet.getInitProposition();

		try {
			Proposition terminalProp = this.propnet.getTerminalProposition();
			if ((terminalProp.crystalizedGetSingleInput() instanceof Or)) {
				Component or = terminalProp.crystalizedGetSingleInput();
				for (Component c : or.crystalizedGetInputs()) {
					if (checkCounter(c, initProposition)) {
						int ctr = 0;
						Component cc = c;
						while (cc.numberOfInputs() != 0) {
							if (cc.isBase()) ctr++;
							cc = cc.crystalizedGetSingleInput();
						}
						System.out.println("[TeapotASP] Found Step Counter: " + ctr);
						numberOfSteps = ctr;
						break;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("[TeapotASP] Error With Finding Step Counter");
		}

		if (numberOfSteps == 0) {
			System.out.println("[TeapotASP] Can't Find Step Counter: Setting to 100");
			numberOfSteps = 100;
		}

		return numberOfSteps;
	}

	// MARK: - Helper

	private void forwardprop(Component c, boolean start) {
		if (c == null) return;
		if (c.connectedToTerminal) return;
		c.connectedToTerminal = true;
		if (c.isBase() && !start) return;
		for (Component cc : c.getOutputs()) forwardprop(cc, false);
	}

	private boolean checkCounter(Component c, Proposition initProposition) {
		if (c.numberOfInputs() == 0) {
			if (c.isBase()) return true;
			if (initProposition != null && c.equals(initProposition)) return true;
		}
		else if (c.numberOfInputs() == 1) return checkCounter(c.crystalizedGetSingleInput(), initProposition);
		else if (c.numberOfInputs() != 1) return false;
		return false;
	}
}
