package checkers.p2p;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;

import android.util.Log;

import net.jxta.credential.AuthenticationCredential;
import net.jxta.discovery.DiscoveryEvent;
import net.jxta.discovery.DiscoveryListener;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.exception.PeerGroupException;
import net.jxta.id.IDFactory;
import net.jxta.membership.Authenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.protocol.DiscoveryResponseMsg;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.PeerGroupAdvertisement;

import checkers.p2p.event.*;

/**
 * Operatii pe grupuri.
 * 
 * @author Hasna Octavian-Lucian
 * @version 15.12.2011
 */
public class Groups implements DiscoveryListener
{
	private EventListenerList listenerList;

	private DiscoveryService discovery;

	private PeerGroup defaultPeerGroup;
	private HashMap<String, String> groups;
	private PeerGroupAdvertisement firstPeerGroupAdv;

	private boolean isRunning;

	/**
	 * Constructorul pentru clasa Groups.
	 * 
	 * @param netPeerGroup
	 */
	public Groups(PeerGroup netPeerGroup)
	{
		defaultPeerGroup = netPeerGroup;

		listenerList = new EventListenerList();
		discovery = defaultPeerGroup.getDiscoveryService();
		groups = new HashMap<String, String>();
	}

	/**
	 * Porneste serviciile.
	 */
	public void start()
	{
		if (!isRunning)
		{
			isRunning = true;
			discovery.addDiscoveryListener(this);
		}
	}

	/**
	 * Opreste serviciile.
	 */
	public void stop()
	{
		if (isRunning)
		{
			isRunning = false;
			groups.clear();
			discovery.removeDiscoveryListener(this);
		}
	}

	/**
	 * Cauta grupurile care au numele specificat.
	 * 
	 * @param groupName numele grupul de cautat
	 */
	public void search(String groupName, int maxGroups)
	{
		Log.i("Groups","Cautare locala.");
		searchLocal("Name", groupName);

		if (groups.size() < maxGroups)
		{
			Log.i("Groups","Cautare externa.");
			searchRemote("Name", groupName, maxGroups);
		}
	}

	/**
	 * Adauga grupurile gasite in lista.
	 * 
	 * @param advs
	 */
	private void addGroups(Enumeration<Advertisement> advs)
	{
		if (advs != null)
		{
			boolean primul = true;
			int aux = groups.size();
			while (advs.hasMoreElements())
			{
				Advertisement item = advs.nextElement();
				if (item instanceof PeerGroupAdvertisement)
				{
					PeerGroupAdvertisement pga = (PeerGroupAdvertisement) item;
					groups.put(pga.getPeerGroupID().toString(), pga.getName());
					Log.i("Groups","A fost gasit:" + pga.getName());
					if (primul)
					{
						firstPeerGroupAdv = pga;
						primul = false;
					}
				}
			}
			if (groups.size() != aux)
			{
				Thread t = new Thread() {
					public void run()
					{
						fireContentChanged(getGroups());
					}
				};
				t.start();
			}
		}
	}

	/**
	 * Cauta in exterior grupuri care au atributul attr cu valoarea val.
	 * 
	 * @param attr
	 * @param val
	 * @param maxGroups nr maxim de grupuri
	 */
	private void searchRemote(String attr, String val, int maxGroups)
	{
		discovery.getRemoteAdvertisements(null, DiscoveryService.GROUP, attr, val, maxGroups);
	}

	/**
	 * Cauta in cache grupuri care au atributul attr cu valoarea val.
	 * 
	 * @param attr
	 * @param val
	 */
	private void searchLocal(String attr, String val)
	{
		Enumeration<Advertisement> rez;
		try
		{
			rez = discovery.getLocalAdvertisements(DiscoveryService.GROUP, attr, val);
			addGroups(rez);
		}
		catch (IOException e)
		{
			Log.e("Groups","nu s-au putut citi cache-ul local.");
		}
	}

	/**
	 * Sterge grupurile stocate local.
	 */
	public void flush()
	{
		try
		{
			Enumeration<Advertisement> eachAdv = discovery.getLocalAdvertisements(DiscoveryService.GROUP, null, null);
			while (eachAdv.hasMoreElements())
			{
				Advertisement anAdv = (Advertisement) eachAdv.nextElement();
				discovery.flushAdvertisement(anAdv);
			}
			groups.clear();
		}
		catch (IOException e)
		{
			Log.e("Groups","nu s-au putut sterge group advertisements.");
		}
	}

	/**
	 * @return primul grup gasit
	 */
	public PeerGroup getFirstGroup()
	{
		PeerGroup primul = null;
		try
		{
			primul = defaultPeerGroup.newGroup(firstPeerGroupAdv);
		}
		catch (PeerGroupException e)
		{
			Log.e("Groups","nu s-au putut crea grupul din PeerGroupAdvertisement.");
		}

		return primul;
	}

	/**
	 * @return lista de grupuri
	 */
	public HashMap<String, String> getGroups()
	{
		return new HashMap<String, String>(groups);
	}

	/**
	 * Creeaza un grup cu name si description.
	 * 
	 * @param name
	 * @param description
	 * @return grupul creat
	 * @throws Exception
	 */
	public PeerGroup createGroup(String name, String description) throws Exception
	{
		return createGroup(IDFactory.newPeerGroupID(), name, description);
	}

	/**
	 * Creeaza un grup cu groupID, name si description.
	 * 
	 * @param groupID
	 * @param name
	 * @param description
	 * @return grupul creat
	 */
	public PeerGroup createGroup(PeerGroupID groupID, String name, String description)
	{
		PeerGroup newGroup = null;
		try
		{
			ModuleImplAdvertisement implAdv = defaultPeerGroup.getAllPurposePeerGroupImplAdvertisement();
			newGroup = defaultPeerGroup.newGroup(groupID, implAdv, name, description);
			PeerGroupAdvertisement groupAdv = newGroup.getPeerGroupAdvertisement();

			discovery.publish(groupAdv);
			discovery.remotePublish(groupAdv);
		}
		catch (Exception e)
		{
			Log.e("Groups","Nu s-a putut crea noul grup.");
		}

		return newGroup;
	}

	/**
	 * Asociaza partenerul local la group.
	 * 
	 * @param group
	 * @return rezultatul operatiei
	 */
	public boolean joinGroup(PeerGroup group)
	{
		AuthenticationCredential cred = new AuthenticationCredential(group, null, null);
		MembershipService membershipService = group.getMembershipService();
		Authenticator authenticator;
		try
		{
			authenticator = membershipService.apply(cred);
			if (authenticator.isReadyForJoin())
			{
				membershipService.join(authenticator);
				Log.i("Groups","S-a facut join la grupul " + group);
				return true;
			}
		}
		catch (Exception e)
		{
			Log.e("Groups","Nu s-a putut crea noul authenticator-ul.");
		}

		return false;
	}

	/**
	 * Se ocupa cu evenimentul generat de gasirea unui grup.
	 */
	public void discoveryEvent(DiscoveryEvent event)
	{
		DiscoveryResponseMsg rez = event.getResponse();
		addGroups(rez.getAdvertisements());
	}

	/**
	 * Adauga un <code>P2PListener</code> la clasa Groups.
	 */
	public synchronized void addP2PListener(P2PListener listener)
	{
		listenerList.add(P2PListener.class, listener);
	}

	/**
	 * Sterge <code>P2PListener</code> de la clasa Groups.
	 */
	public synchronized void removeP2PListener(P2PListener listener)
	{
		listenerList.remove(P2PListener.class, listener);
	}

	/**
	 * Notifica schimbarea continutului listei groups.
	 * 
	 * @param groupsList
	 */
	private synchronized void fireContentChanged(HashMap<String, String> groupsList)
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.GROUP_FOUND, groupsList));
		}
	}
}
