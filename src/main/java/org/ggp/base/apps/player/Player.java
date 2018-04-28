package org.ggp.base.apps.player;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.ggp.base.apps.player.config.ConfigPanel;
import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.match.MatchPanel;
import org.ggp.base.apps.player.network.NetworkPanel;
import org.ggp.base.player.AbstractGamePlayer;
import org.ggp.base.player.GamePlayer;
import org.ggp.base.player.PollingGamePlayer;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.util.gdl.grammar.DataFormat;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.reflection.ProjectSearcher;
import org.ggp.base.util.ui.NativeUI;

import com.google.common.collect.Lists;


@SuppressWarnings("serial")
public final class Player extends JPanel implements ItemListener
{
	private static void createAndShowGUI(Player playerPanel)
	{
		JFrame frame = new JFrame("Game Player");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.setPreferredSize(new Dimension(1024, 768));
		frame.getContentPane().add(playerPanel);

		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) throws IOException
	{
	    initialize(null);
	}

	public static void initialize(String name) {
		NativeUI.setNativeUI();

	    final Player playerPanel = new Player(name);
	    javax.swing.SwingUtilities.invokeLater(new Runnable()
	    {

		@Override
		public void run()
		{
		    createAndShowGUI(playerPanel);
		}
	    });
	}

	private static final String DEFAULT_MSG_URL = "http://ggp.stanford.edu/ggp/arena";
	private static final String GET_URL = "/getmessages.php";
	private static final String PUT_URL = "/putmessages.php";

	private int playerCount;

	private JTextField msgUrlTextField;
	private JComboBox<DataFormat> msgFormatBox;

	private JComboBox<CommunicationFormat> commFormatBox;
	private Map<CommunicationFormat, JPanel> commPanels;
	private GridBagConstraints commGridBag;

	private JPanel managerPanel;

	private JButton createButton;
	private JTabbedPane playersTabbedPane;

	private JTextField portTextField;

	private JComboBox<String> typeComboBox;

	private Integer defaultPort = 9147;

	private List<Class<? extends Gamer>> gamers = Lists.newArrayList(ProjectSearcher.GAMERS.getConcreteClasses());

	public Player()
	{
		super(new GridBagLayout());
		constructor(null);
	}

	public Player(String name) {
		super(new GridBagLayout());
		constructor(name);
	}

	private void constructor(String name) {

		playerCount = 0;
		portTextField = new JTextField(defaultPort.toString());
		typeComboBox = new JComboBox<String>();
		createButton = new JButton(createButtonMethod());
		playersTabbedPane = new JTabbedPane();
		commFormatBox = new JComboBox<CommunicationFormat>();
		commFormatBox.addItemListener(this);
		commPanels = new HashMap<CommunicationFormat, JPanel>();
		msgFormatBox = new JComboBox<DataFormat>();
		msgUrlTextField = new JTextField(DEFAULT_MSG_URL);


		portTextField.setColumns(15);

		List<Class<? extends Gamer>> newBuffer = new ArrayList<Class<? extends Gamer>>();
		for (Class<? extends Gamer> g : gamers) {
			if (g.getName().contains("Teapot")) newBuffer.add(g);
		}

		gamers = newBuffer;

		// Sort the list of gamers before displaying it to the user
		java.util.Collections.sort(gamers, new Comparator<Class<? extends Gamer>>() {
			@Override
			public int compare(Class<? extends Gamer> left, Class<? extends Gamer> right) {
				return left.getSimpleName().compareTo(right.getSimpleName());
			}
		});

		List<Class<? extends Gamer>> gamersCopy = new ArrayList<Class<? extends Gamer>>(gamers);
		for(Class<? extends Gamer> gamer : gamersCopy)
		{
			Gamer g;
			try {
				g = gamer.newInstance();
				typeComboBox.addItem(g.getName());
			} catch(Exception ex) {
			    gamers.remove(gamer);
			}
		}

		if (name != null) {
			typeComboBox.setSelectedItem(name);
		}

		for (CommunicationFormat commType : CommunicationFormat.values()) {
			commFormatBox.addItem(commType);
		}

		for (DataFormat dataType : DataFormat.values()) {
			msgFormatBox.addItem(dataType);
		}

		JPanel pollingPanel = new JPanel(new GridBagLayout());
		pollingPanel.setBorder(new TitledBorder(CommunicationFormat.POLLING.toString()));
		pollingPanel.add(new JLabel("Message Url:"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(20, 5, 5, 5), 5, 5));
		pollingPanel.add(msgUrlTextField, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(20, 5, 5, 5), 5, 5));
		commPanels.put(CommunicationFormat.POLLING, pollingPanel);

		JPanel httpPanel = new JPanel(new GridBagLayout());
		httpPanel.setBorder(new TitledBorder(CommunicationFormat.HTTP_CONNECTION.toString()));
		httpPanel.add(new JLabel("Port:"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(20, 5, 5, 5), 5, 5));
		httpPanel.add(portTextField, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(20, 5, 5, 5), 5, 5));
		commPanels.put(CommunicationFormat.HTTP_CONNECTION, httpPanel);


		managerPanel = new JPanel(new GridBagLayout());
		managerPanel.setBorder(new TitledBorder("Manager"));


		int col = 0;
		managerPanel.add(new JLabel("Type:"), new GridBagConstraints(0, col, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		managerPanel.add(typeComboBox, new GridBagConstraints(1, col, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
		managerPanel.add(new JLabel("Data Format:"), new GridBagConstraints(0, ++col, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		managerPanel.add(msgFormatBox, new GridBagConstraints(1, col, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
		managerPanel.add(new JLabel("Communication Protocol:"), new GridBagConstraints(0, ++col, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(20, 5, 5, 5), 5, 5));
		managerPanel.add(commFormatBox, new GridBagConstraints(1, col, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(20, 5, 5, 5), 5, 5));
		commGridBag = new GridBagConstraints(0, ++col, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5);
		managerPanel.add(createButton, new GridBagConstraints(1, ++col, 1, 1, 1.0, 1.0, GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
		//make sure to add this panel last
		managerPanel.add(httpPanel, commGridBag);


		JPanel playersPanel = new JPanel(new GridBagLayout());
		playersPanel.setBorder(new TitledBorder("Players"));

		playersPanel.add(playersTabbedPane, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));

		this.add(managerPanel, new GridBagConstraints(0, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
		this.add(playersPanel, new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
	    if (managerPanel == null) {
	    	return;
	    }
	    if ((e.getStateChange() == ItemEvent.SELECTED)) {
	    	CommunicationFormat selection = (CommunicationFormat) commFormatBox.getSelectedItem();
	        managerPanel.remove(managerPanel.getComponentCount() - 1);
	       	managerPanel.add(commPanels.get(selection), commGridBag);
	       	revalidate();
	        repaint();
	    }
	}

	private AbstractAction createButtonMethod()
	{
		return new AbstractAction("Create")
		{

			@Override
			public void actionPerformed(ActionEvent evt)
			{
				try
				{
					int port = Integer.valueOf(portTextField.getText());
					String type = (String) typeComboBox.getSelectedItem();
					DataFormat dataFormat = (DataFormat) msgFormatBox.getSelectedItem();

					MatchPanel matchPanel = new MatchPanel();
					NetworkPanel networkPanel = new NetworkPanel();
					DetailPanel detailPanel = null;
					ConfigPanel configPanel = null;
					Gamer gamer = null;

					Class<?> gamerClass = gamers.get(typeComboBox.getSelectedIndex());
					try {
						gamer = (Gamer) gamerClass.newInstance();
					} catch(Exception ex) { throw new RuntimeException(ex); }
					detailPanel = gamer.getDetailPanel();
					configPanel = gamer.getConfigPanel();

					gamer.addObserver(matchPanel);
					gamer.addObserver(detailPanel);

					AbstractGamePlayer player = null;
					String tabName = "";

					CommunicationFormat commFormat = (CommunicationFormat) commFormatBox.getSelectedItem();
					switch (commFormat) {

					case HTTP_CONNECTION:
						player = new GamePlayer(port, gamer);
						GdlPool.format = dataFormat;
						tabName = type + " (" + port + ")";
						break;
					case POLLING:
						String getUrl = msgUrlTextField.getText() + GET_URL;
						String putUrl = msgUrlTextField.getText() + PUT_URL;
						player = new PollingGamePlayer(getUrl, putUrl, gamer, dataFormat);
						tabName = ++playerCount + ". " + type;
						break;
					}
					player.addObserver(networkPanel);
					player.start();

					JTabbedPane tab = new JTabbedPane();
					tab.addTab("Match", matchPanel);
					tab.addTab("Network", networkPanel);
					tab.addTab("Configuration", configPanel);
					tab.addTab("Detail", detailPanel);
					playersTabbedPane.addTab(tabName, tab);
					playersTabbedPane.setSelectedIndex(playersTabbedPane.getTabCount()-1);

					defaultPort++;
					portTextField.setText(defaultPort.toString());

					//allow setting the message format only once,
					//since it is currently a static variable
					if (msgFormatBox.isEnabled()) {
						msgFormatBox.setEnabled(false);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
	}
}