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

import net.jxta.exception.PeerGroupException;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkManager;

import android.util.Log;

import checkers.p2p.event.*;

public class JXTAService implements P2PListener
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
	private boolean isRunning, ready;
	private Timer timer;
	private int searchesNumber;

	public JXTAService(String peerName, File cacheFolder) throws IOException
	{
		Log.i("JXTAService", "constructor");

		listenerList = new EventListenerList();
		manager = new NetworkManager(NetworkManager.EDGE, peerName, cacheFolder.toURI());

		timer = new Timer();
		searchesNumber = 0;

		isRunning = false;
		ready = false;
	}

	public void start() throws PeerGroupException, IOException
	{
		start("urn:jxta:uuid-F256F83F63904289A362BDFCF7F226B602", "CheckersGroup",
				"urn:jxta:uuid-59616261646162614E504720503250338BDD512C72FE462EAE54E9948FF4C23E04", "CheckerPipe");
	}

	public void start(String groupId, final String groupName, String pipeId, String pipeName) throws PeerGroupException, IOException
	{
		if (!isRunning)
		{
			Log.i("JXTAService", "S-a deschis conexiunea.");

			manager.startNetwork();
			isRunning = true;
			ready = false;
			this.groupId = groupId;
			this.groupName = groupName;
			this.pipeId = pipeId;
			this.pipeName = pipeName;
			netPeerGroup = manager.getNetPeerGroup();
			groups = new Groups(netPeerGroup);
			groups.start();
			groups.addP2PListener(this);
			groups.flush();

			timer = new Timer();
			searchesNumber = 0;
			timer.schedule(new TimerTask() {

				@Override
				public void run()
				{
					searchesNumber++;
					if (searchesNumber > MAX_SEARCHES_NUMBER || searchedGroup != null)
					{
						timer.cancel();
						// err
						stateChanged(new P2PEvent(this, P2PEvent.GROUP_SEARCH_FINISHED));
					}
					else
					{
						Log.i("JXTAService", "Cautarea nr " + searchesNumber);
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
		return isRunning;
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
		if (isRunning)
		{
			timer.cancel();
			Log.i("JXTAService", "S-a inchis conexiunea.");

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
			isRunning = false;
			ready = false;
		}
	}

	/**
	 * Cauta toti partenerii din grupul CheckersGroup.
	 */
	public void searchPeers()
	{
		if (peers != null)
		{
			// peers.flush();
			peers.search();
		}
	}

	/**
	 * Cauta partenerii care au nume asemanator cu nameFilter.
	 * 
	 * @param nameFilter
	 * @throws Exception
	 */
	public void searchPeers(String nameFilter) throws IllegalArgumentException
	{
		if (peers != null)
		{
			// peers.flush();
			Pattern p = Pattern.compile("\\w*");// a-z A-Z _ 0-9
			Matcher m = p.matcher(nameFilter);
			if (m.matches())
			{
				peers.search("*" + nameFilter + "*", 10);
			}
			else
			{
				throw new IllegalArgumentException("Filtru contine caractere nepermise");
			}
		}
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
	 * Se ocupa cu evenimentul generate de P2P de gasirea unui grup.
	 */
	public void stateChanged(P2PEvent event)
	{
		switch (event.getTip())
		{
			case P2PEvent.GROUP_FOUND:
			{
				Log.i("JXTAService", "A fost gasit grupul.");

				searchedGroup = groups.getFirstGroup();
				/*
				 * if (!groups.joinGroup(searchedGroup)) { Log.e("JXTAService",
				 * "Nu s-a putut face join!"); }
				 */
				peers = new Peers(netPeerGroup, pipeId, pipeName); // searchedGroup
				peers.addP2PListener(this);
				peers.start();
				
				fireConnectionReady();
				break;
			}
			case P2PEvent.GROUP_SEARCH_FINISHED:
			{
				if (searchesNumber > MAX_SEARCHES_NUMBER && searchedGroup == null)
				{
					Log.i("JXTAService", "Creaza grup nou.");
					PeerGroupID peerGroupID = null;
					try
					{
						peerGroupID = (PeerGroupID) IDFactory.fromURI(new URI(groupId));
					}
					catch (URISyntaxException e)
					{
						Log.e("JXTAService", "peer id incorect!");
					}
					searchedGroup = groups.createGroup(peerGroupID, "CheckersGroup", "Group for checkers game.");
					/*
					 * if (!groups.joinGroup(searchedGroup)) {
					 * Log.e("JXTAService", "Nu s-a putut face join!"); }
					 */
					peers = new Peers(netPeerGroup, pipeId, pipeName); // searchedGroup
					peers.addP2PListener(this);
					peers.start();
					
					fireConnectionReady();
				}
				break;
			}
			case P2PEvent.MESSAGE_RECEIVED:
			{
				Log.i("JXTAService", "s-a primit un mesaj de la " + event.getSenderName() + " ce contine [" + event.getMessage() + "]");

				fireMessageReceived(event.getSenderID(), event.getSenderName(), event.getMessage());
				break;
			}
			case P2PEvent.PEER_FOUND:
			{
				fireContentChanged(event.getList());
				break;
			}
			case P2PEvent.PEER_SEARCH_FINISHED:
			{
				// fireSearchFinished(event.getList());
				break;
			}
			case P2PEvent.PEER_READY:
			{
				ready = true;
				// fireConnectionReady();
				// break;
			}
		}
	}

	/**
	 * Notifica asocierea la grupul checkersGroup.
	 */
	private void fireConnectionReady()
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
	private void fireContentChanged(HashMap<String, String> peersList)
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.PEER_FOUND, peersList));
		}
	}

	/**
	 * Notifica terminarea cautarii.
	 * 
	 * @param peersList
	 */
	private void fireSearchFinished(HashMap<String, String> peersList)
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
