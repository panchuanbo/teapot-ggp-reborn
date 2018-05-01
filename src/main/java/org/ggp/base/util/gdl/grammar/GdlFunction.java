package org.ggp.base.util.gdl.grammar;

import java.util.List;

import com.google.common.collect.ImmutableList;

@SuppressWarnings("serial")
public final class GdlFunction extends GdlTerm
{

	private final ImmutableList<GdlTerm> body;
	private transient Boolean ground;
	private final GdlConstant name;

	GdlFunction(GdlConstant name, ImmutableList<GdlTerm> body)
	{
		this.name = name;
		this.body = body;
		ground = null;
	}

	public int arity()
	{
		return body.size();
	}

	private boolean computeGround()
	{
		for (GdlTerm term : body)
		{
			if (!term.isGround())
			{
				return false;
			}
		}

		return true;
	}

	public GdlTerm get(int index)
	{
		return body.get(index);
	}

	public GdlConstant getName()
	{
		return name;
	}

	public List<GdlTerm> getBody()
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
	public GdlSentence toSentence()
	{
		return GdlPool.getRelation(name, body);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		switch (GdlPool.format) {
		case HRF:
			sb.append(name + "(");
			for (int i=0; i<body.size(); i++) {
				sb.append(body.get(i));
				if (i < body.size()-1) {
					sb.append(", ");
				}
			}
			sb.append(")");
			break;

		case KIF:
			sb.append("( " + name + " ");
			for (GdlTerm term : body)
			{
				sb.append(term + " ");
			}
			sb.append(")");
			break;
		}

		return sb.toString();
	}

	@Override
	public String infixString() {
		StringBuilder sb = new StringBuilder();
		sb.append("" + name.infixString() + "(");
		for (GdlTerm term : body) {
			sb.append(term.infixString() + ",");
		}
		if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
		sb.append(")");

		return sb.toString();
	}

	@Override
	public String toASPString() {
		StringBuilder sb = new StringBuilder();
		sb.append("" + name.toASPString() + "(");
		for (GdlTerm term : body) {
			sb.append(term.toASPString() + ",");
		}
		if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
		sb.append(")");

		return sb.toString();
	}
}
