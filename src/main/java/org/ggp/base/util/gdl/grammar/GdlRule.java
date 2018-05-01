package org.ggp.base.util.gdl.grammar;

import java.util.List;

import com.google.common.collect.ImmutableList;

@SuppressWarnings("serial")
public final class GdlRule extends Gdl
{

	private final ImmutableList<GdlLiteral> body;
	private transient Boolean ground;
	private final GdlSentence head;

	GdlRule(GdlSentence head, ImmutableList<GdlLiteral> body)
	{
		this.head = head;
		this.body = body;
		ground = null;
	}

	public int arity()
	{
		return body.size();
	}

	private Boolean computeGround()
	{
		for (GdlLiteral literal : body)
		{
			if (!literal.isGround())
			{
				return false;
			}
		}

		return true;
	}

	public GdlLiteral get(int index)
	{
		return body.get(index);
	}

	public GdlSentence getHead()
	{
		return head;
	}

	public List<GdlLiteral> getBody()
	{
		return body;
	}

	@Override
	public boolean isGround()
	{
		if (ground == null)
		{
			ground = computeGround();
		}

		return ground;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		switch (GdlPool.format) {
		case HRF:
			sb.append(head + " :- ");
			for (int i=0; i<body.size(); i++) {
				sb.append(body.get(i));
				if (i < body.size()-1) {
					sb.append(" & ");
				}
			}
			break;

		case KIF:
			sb.append("( <= " + head + " ");
			for (GdlLiteral literal : body) {
				sb.append(literal + " ");
			}
			sb.append(")");
			break;
		}

		return sb.toString();
	}

	@Override
	public String infixString() {
		StringBuilder sb = new StringBuilder();
		sb.append(head.infixString() + " :- ");
		for (GdlLiteral literal : body) {
			sb.append(literal.infixString() + ",");
		}
		if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	@Override
	public String toASPString() {
		StringBuilder sb = new StringBuilder();
		sb.append(head.toASPString() + " :- ");
		for (GdlLiteral literal : body) {
			sb.append(literal.toASPString() + ",");
		}
		if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
}
