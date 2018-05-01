package org.ggp.base.util.gdl.grammar;

@SuppressWarnings("serial")
public final class GdlNot extends GdlLiteral
{

	private final GdlLiteral body;
	private transient Boolean ground;

	GdlNot(GdlLiteral body)
	{
		this.body = body;
		ground = null;
	}

	public GdlLiteral getBody()
	{
		return body;
	}

	@Override
	public boolean isGround()
	{
		if (ground == null)
		{
			ground = body.isGround();
		}

		return ground;
	}

	@Override
	public String toString()
	{
		switch (GdlPool.format) {
		case HRF:
			return "~ " + body;
		case KIF:
			return "( not " + body + " )";
		}
		return null;
	}

	@Override
	public String infixString() {
		return "~" + body.infixString();
	}

	@Override
	public String toASPString() {
		return "not " + body.toASPString();
	}
}
