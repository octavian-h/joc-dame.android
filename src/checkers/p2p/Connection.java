package checkers.p2p;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import net.jxta.exception.PeerGroupException;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.ConfigurationFactory;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.platform.NetworkManager;

import checkers.p2p.event.*;

/**
 * Conexiunea cu retea P2P prin protocolul JXTA.
 * 
 * @author Hasna Octavian-Lucian
 * @version 15.12.2011
 */
public class Connection implements P2PListener
{
	private final static long TIMER_PERIOD = 1000;
	private final static long TIMER_FIRST_DELAY = 200;
	private final static int MAX_SEARCHES_NUMBER = 5;

	protected EventListenerList listenerList;
	private NetworkManager manager;

	private PeerGroup searchedGroup, netPeerGroup;
	private Groups groups;
	private Peers peers;
	private String groupId, groupName, pipeId, pipeName;
	private boolean started, ready, searching;
	private Timer timer;
	private int searchesNumber;


	/**
	 * Constructorul pentru clasa Connection.
	 * 
	 * @param peerName numele partenerului local
	 * @param cacheFolder folder-ul in care se salveaza fisierele de configurare
	 * @throws IOException
	 */
	public Connection(String peerName, File cacheFolder) throws IOException
	{
		Log.i("Connection","constructor");

		listenerList = new EventListenerList();
		manager = new NetworkManager(NetworkManager.ADHOC, peerName, cacheFolder.toURI());
		NetworkConfigurator config = manager.getConfigurator();
		config.setName(peerName);
		config.setMode(NetworkConfigurator.HTTP_CLIENT + NetworkConfigurator.HTTP_SERVER);
		
		ConfigurationFactory cf = ConfigurationFactory.newInstance();
		cf.setName(peerName);
		
		timer = new Timer();
		searchesNumber = 0;

		started = false;
		ready = false;
		searching = false;
	}

	public void start() throws PeerGroupException, IOException
	{
		start("urn:jxta:uuid-F256F83F63904289A362BDFCF7F226B602", "CheckersGroup",
				"urn:jxta:uuid-59616261646162614E504720503250338BDD512C72FE462EAE54E9948FF4C23E04", "CheckerPipe");
	}

	/**
	 * Porneste conexiunea prin protocolul JXTA.
	 * 
	 * @param groupId
	 * @param groupName
	 * @param pipeId
	 * @param pipeName
	 * @throws PeerGroupException
	 * @throws IOException
	 */
	public void start(String groupId, final String groupName, String pipeId, String pipeName) throws PeerGroupException, IOException
	{
		if (!started)
		{
			Log.i("Connection","S-a deschis conexiunea.");

			manager.startNetwork();
			started = true;
			ready = false;
			this.groupId = groupId;
			this.groupName = groupName;
			this.pipeId = pipeId;
			this.pipeName = pipeName;
			netPeerGroup = manager.getNetPeerGroup();
			groups = new Groups(netPeerGroup);
			//groups.flush();
			groups.addP2PListener(this);
			groups.start();
			

			timer = new Timer();
			searchesNumber = 0;
			searching = true;
			timer.schedule(new TimerTask() {

				@Override
				public void run()
				{
					searchesNumber++;
					if (searchesNumber > MAX_SEARCHES_NUMBER || searchedGroup != null)
					{
						timer.cancel();
						searching = false;
						stateChanged(new P2PEvent(this, P2PEvent.GROUP_SEARCH_FINISHED));
					}
					else
					{
						Log.i("Groups","Cautarea nr " + searchesNumber);
						groups.search(groupName, 1);
					}
				}
			}, TIMER_FIRST_DELAY, TIMER_PERIOD);
		}
	}

	public String getGroupId()
	{
		return groupId;
	}

	public String getGroupName()
	{
		return groupName;
	}

	public String getPipeId()
	{
		return pipeId;
	}

	public String getPipeName()
	{
		return pipeName;
	}

	public boolean isStarted()
	{
		return started;
	}

	public boolean isReady()
	{
		return ready;
	}

	/**
	 * Opreste conexiunea.
	 */
	public void stop()
	{
		if (started)
		{
			timer.cancel();
			Log.i("Connection","S-a inchis conexiunea.");

			if (peers != null)
			{
				peers.removeP2PListener(this);
				peers.stop();
				peers = null;
			}
			groups.removeP2PListener(this);
			groups.stop();
			groups = null;
			searchedGroup = null;
			manager.stopNetwork();
			started = false;
			ready = false;
		}
	}

	/**
	 * Cauta toti partenerii din grupul CheckersGroup.
	 */
	public void searchPeers()
	{
		if (ready && !searching)
		{
			timer = new Timer();
			searchesNumber = 0;
			searching = true;
			timer.schedule(new TimerTask() {

				@Override
				public void run()
				{
					searchesNumber++;
					if (searchesNumber > MAX_SEARCHES_NUMBER)
					{
						timer.cancel();
						searching = false;
						firePeersSearchFinished(peers.getPeers());
					}
					else
					{
						Log.i("Peers","Cautarea nr " + searchesNumber);
						peers.search();
					}
				}
			}, TIMER_FIRST_DELAY, TIMER_PERIOD);			
		}
	}

	/**
	 * Cauta partenerii care au nume asemanator cu nameFilter.
	 * 
	 * @param nameFilter
	 * @throws Exception
	 */
	public void searchPeers(final String nameFilter) throws IllegalArgumentException
	{
		if (ready && !searching)
		{
			Pattern p = Pattern.compile("\\w*");// a-z A-Z _ 0-9
			Matcher m = p.matcher(nameFilter);
			if (!m.matches())
			{
				throw new IllegalArgumentException("Filtru contine caractere nepermise");
			}
			
			timer = new Timer();
			searchesNumber = 0;
			searching = true;
			timer.schedule(new TimerTask() {

				@Override
				public void run()
				{
					searchesNumber++;
					if (searchesNumber > MAX_SEARCHES_NUMBER)
					{
						timer.cancel();
						searching = false;
						firePeersSearchFinished(peers.getPeers());
					}
					else
					{
						Log.i("Peers","Cautarea nr " + searchesNumber);
						peers.search("*" + nameFilter + "*", 10);
					}
				}
			}, TIMER_FIRST_DELAY, TIMER_PERIOD);			
		}
	}

	/**
	 * Opresete cautarea de parteneri.
	 */
	public void stopSearch()
	{
		timer.cancel();
		searching = false;
	}
	
	/**
	 * Trimite un mesaj prin output pipe.
	 * 
	 * @param receiverID id-ul partenerului la care se trimite
	 * @param message mesajul de trimis
	 */
	public void sendMessage(String receiverID, String message)
	{
		if (peers != null) peers.sendMessage(receiverID, message);
	}

	/**
	 * @return lista de parteneri (id, nume_partener)
	 */
	public HashMap<String, String> getPeers()
	{
		if (peers != null) return peers.getPeers();
		else return null;
	}

	/**
	 * Adauga un <code>P2PListener</code> la clasa Connection.
	 */
	public void addP2PListener(P2PListener listener)
	{
		listenerList.add(P2PListener.class, listener);
	}

	/**
	 * Sterge <code>P2PListener</code> de la clasa Connection.
	 */
	public void removeP2PListener(P2PListener listener)
	{
		listenerList.remove(P2PListener.class, listener);
	}

	/**
	 * Se ocupa cu evenimentele generate de retea P2P.
	 */
	public void stateChanged(P2PEvent event)
	{
		switch (event.getTip())
		{
			case P2PEvent.GROUP_FOUND:
			{
				groups.removeP2PListener(this);
				Log.i("Connection","A fost gasit grupul.");

				searchedGroup = groups.getFirstGroup();
				/*
				 * if (!groups.joinGroup(searchedGroup)) { Log.e("Connection",
				 * "Nu s-a putut face join!"); }
				 */
				
				peers = new Peers(netPeerGroup, pipeId, pipeName); // searchedGroup
				peers.addP2PListener(this);
				peers.start();
				break;
			}
			case P2PEvent.GROUP_SEARCH_FINISHED:
			{
				if (searchedGroup == null)
				{
					groups.removeP2PListener(this);					
					Log.i("Connection","Creaza grup nou.");
					
					PeerGroupID peerGroupID = null;
					try
					{
						peerGroupID = (PeerGroupID) IDFactory.fromURI(new URI(groupId));
					}
					catch (URISyntaxException e)
					{
						Log.e("Connection","peer id incorect!");
					}
					searchedGroup = groups.createGroup(peerGroupID, "CheckersGroup", "Group for checkers game.");
					/*
					 * if (!groups.joinGroup(searchedGroup)) {
					 * Log.e("Connection","Nu s-a putut face join!"); }
					 */
					peers = new Peers(netPeerGroup, pipeId, pipeName); // searchedGroup
					peers.addP2PListener(this);
					peers.start();
				}
				break;
			}
			case P2PEvent.MESSAGE_RECEIVED:
			{
				Log.i("Connection","s-a primit un mesaj de la " + event.getSenderName() + " ce contine [" + event.getMessage() + "]");

				fireMessageReceived(event.getSenderID(), event.getSenderName(), event.getMessage());
				break;
			}
			case P2PEvent.PEER_FOUND:
			{
				fireContentChanged(event.getList());
				break;
			}
			case P2PEvent.PEER_READY:
			{
				ready = true;
				fireConnectionReady();
				// break;
			}
		}
	}

	/**
	 * Notifica realizare conexiunii.
	 */
	private synchronized void fireConnectionReady()
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);
		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.CONNECTION_READY));
		}
	}

	/**
	 * Notifica schimbarea continutului listei de parteneri.
	 * 
	 * @param peersList
	 */
	private synchronized void fireContentChanged(HashMap<String, String> peersList)
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.PEER_FOUND, peersList));
		}
	}

	/**
	 * Notifica terminarea cautarii de parteneri.
	 * 
	 * @param peersList
	 */
	private synchronized void firePeersSearchFinished(HashMap<String, String> peersList)
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.PEER_SEARCH_FINISHED, peersList));
		}
	}

	/**
	 * Notifica primirea unui mesaj.
	 * 
	 * @param senderID id-ul partenerului care a trimis
	 * @param senderName numele partenerului care a trimis
	 * @param data mesajul trimis
	 */
	private synchronized void fireMessageReceived(String senderID, String senderName, String data)
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.MESSAGE_RECEIVED, senderID, senderName, data));
		}
	}
}
