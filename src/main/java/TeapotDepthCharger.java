import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

class DepthCharger implements Runnable {
	private volatile int utility = 0;

	private StateMachine machine = null;
	private MachineState state = null;
	private Role role = null;

	DepthCharger(StateMachine machine, MachineState state, Role role) {
		this.machine = machine; this.state = state; this.role = role;
	}

	public void setState(MachineState state) { this.state = state; this.utility = 0; }

	public void setRole(Role role) { this.role = role; }

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
		return machine.getGoal(state, role);
	}

	public int getUtility() {
		return utility;
	}
}