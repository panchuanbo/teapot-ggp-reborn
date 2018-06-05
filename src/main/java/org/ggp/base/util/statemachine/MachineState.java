package org.ggp.base.util.statemachine;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlSentence;

public class MachineState {

	public MachineState() {
		this.contents = null;
		this.activeBits = null;
	}

	/**
	 * Starts with a simple implementation of a MachineState. StateMachines that
	 * want to do more advanced things can subclass this implementation, but for
	 * many cases this will do exactly what we want.
	 */
	private final Set<GdlSentence> contents;
	public MachineState(Set<GdlSentence> contents)
	{
		this.contents = contents;
		this.activeBits = null;
	}

	/**
	 * getContents returns the GDL sentences which determine the current state
	 * of the game being played. Two given states with identical GDL sentences
	 * should be identical states of the game.
	 */
	public Set<GdlSentence> getContents()
	{
		assert(false);

		return contents;
	}

	@Override
	public MachineState clone() {
		if (this.contents == null) {
			BitSet set = (BitSet) this.activeBits.clone();
			return new MachineState(set);
		}

		return new MachineState(new HashSet<GdlSentence>(contents));
	}

	/* Utility methods */
	@Override
	public int hashCode()
	{
		return getContents().hashCode();
	}

	@Override
	public String toString()
	{
		Set<GdlSentence> contents = getContents();
		if(contents == null)
			return "(MachineState with null contents)";
		else
			return contents.toString();
	}

	@Override
	public boolean equals(Object o)
	{
		if ((o != null) && (o instanceof MachineState))
		{
			MachineState state = (MachineState) o;
			if (getContents() == null) {
				return state.getPropContents().equals(getPropContents());
			} else {
				return state.getContents().equals(getContents());
			}
		}

		return false;
	}

	////////////////////
	// Custom Methods //
	////////////////////

	private final BitSet activeBits;

	/**
	 * Let MachineStates manage propositions
	 */
	public MachineState(Set<GdlSentence> contents, BitSet activeBits) {
		this.contents = contents;
		this.activeBits = activeBits;
	}

	/**
	 * MachineState Initialization but without contents
	 */
	public MachineState(BitSet activeBits) {
		this.contents = null;
		this.activeBits = activeBits;
	}

	/**
	 * Have an idea...
	 * @return
	 */
	public boolean[] active;
	private int numBits = -1;
	public MachineState(int bits) {
		this.contents = null;
		this.activeBits = null;
		this.numBits = bits;
		active = new boolean[bits];
	}

	public BitSet getPropContents() {
		return activeBits;
	}
}