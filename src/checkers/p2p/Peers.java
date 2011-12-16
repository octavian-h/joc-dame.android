package checkers.p2p;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;

import android.util.Log;

import net.jxta.discovery.DiscoveryEvent;
import net.jxta.discovery.DiscoveryListener;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.id.IDFactory;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.*;
import net.jxta.protocol.DiscoveryResponseMsg;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;

import checkers.p2p.event.*;

/**
 * Operatii pe parteneri.
 * 
 * @author Hasna Octavian-Lucian
 * @version 15.12.2011
 */
public class Peers implements DiscoveryListener, PipeMsgListener, OutputPipeListener
{
	private PipeAdvertisement pipeAdv;
	private PeerAdvertisement peerAdv;

	private EventListenerList listenerList;

	private DiscoveryService discovery;
	private PipeService pipeService;

	private HashMap<String, String> peers;
	private String numePeer;

	private PeerID peerID;
	private String peerName;
	private InputPipe inputPipe;
	private OutputPipe outputPipe;

	private boolean isRunning;

	/**
	 * Constructorul pentru clasa Peers.
	 * 
	 * @param defaultPeerGroup grupul in care se afla partenerii
	 */
	public Peers(PeerGroup defaultPeerGroup, String pipeId, String pipeName)
	{
		listenerList = new EventListenerList();

		discovery = defaultPeerGroup.getDiscoveryService();
		pipeService = defaultPeerGroup.getPipeService();

		peers = new HashMap<String, String>();
		peerID = defaultPeerGroup.getPeerID();
		peerName = defaultPeerGroup.getPeerName();
		pipeAdv = getPipeAdvertisement(pipeId, pipeName);
		peerAdv = defaultPeerGroup.getPeerAdvertisement();
		Log.i("Peers",peerAdv.toString());
		isRunning = false;
	}

	/**
	 * Creeaza un pipe advertisement din id-ul si umele primit.
	 * 
	 * @param pipeId
	 * @param pipeName
	 * @return pipe advertisement
	 */
	public static PipeAdvertisement getPipeAdvertisement(String pipeId, String pipeName)
	{
		PipeAdvertisement advertisement = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());

		PipeID pipeID = null;
		try
		{
			pipeID = (PipeID) IDFactory.fromURI(new URI(pipeId));
		}
		catch (URISyntaxException e)
		{
			Log.e("Peers","pipe id incorect!");
		}
		advertisement.setPipeID(pipeID);
		advertisement.setType(PipeService.PropagateType);
		advertisement.setName(pipeName);
		return advertisement;
	}

	/**
	 * Anunta prezenta partenerului in retea P2P. Durata de viata = timpul de
	 * expirare = 2 min
	 */
	public void announce()
	{
		announce(60 * 2 * 1000, 60 * 2 * 1000);
	}

	/**
	 * Anunta prezenta partenerului in retea P2P.
	 * 
	 * @param lifetime durata de existenta a acestui advertisement
	 * @param expiration durata de pastrare a acestui advertisement de catre
	 *            ceilalti parteneri
	 */
	public void announce(final long lifetime, final long expiration)
	{
		try
		{
			discovery.publish(pipeAdv, lifetime, expiration);
			discovery.remotePublish(peerID.toString(), pipeAdv, expiration);
			discovery.publish(peerAdv, lifetime, expiration);
			discovery.remotePublish(peerID.toString(), peerAdv, expiration);
		}
		catch (IOException e)
		{
			Log.e("Peers","nu s-a putut publica peer-ul");
		}
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
			try
			{
				announce();
				inputPipe = pipeService.createInputPipe(pipeAdv, this);
				// outputPipe = pipeService.createOutputPipe(pipeAdv, 1000);
				pipeService.createOutputPipe(pipeAdv, this);
			}
			catch (IOException e)
			{
				Log.e("Peers","Nu s-a putut crea input/output pipe.");
			}

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
			peers.clear();
			outputPipe.close();
			outputPipe = null;
			inputPipe.close();
			inputPipe = null;
			discovery.removeDiscoveryListener(this);
		}
	}

	/**
	 * Cauta toti partenerii din defaultPeerGroup.
	 */
	public void search()
	{
		search(null, 10);
	}

	/**
	 * Cauta partenerii care au numele specificat.
	 * 
	 * @param peerName numele partenerului de cautat
	 * @param maxPeers
	 */
	public void search(String peerName, int maxPeers)
	{
		announce();
		numePeer = peerName;
		Log.i("Peers","Cautare locala.");
		searchLocal("Name", numePeer);
		if (peers.size() < maxPeers)
		{
			Log.i("Peers","Cautare externa.");
			searchRemote("Name", peerName, maxPeers);
		}
	}

	/**
	 * Adauga partenerii gasiti in lista.
	 * 
	 * @param advs
	 */
	private void addPeers(Enumeration<Advertisement> advs)
	{
		if (advs != null)
		{
			int aux = peers.size();
			while (advs.hasMoreElements())
			{
				Advertisement item = advs.nextElement();
				if (item instanceof PeerAdvertisement)
				{
					PeerAdvertisement pa = (PeerAdvertisement) item;
					/*if (!pa.getPeerID().equals(peerID))
					{*/
						peers.put(pa.getPeerID().toString(), pa.getName());
						Log.i("Peers","A fost gasit:" + pa.getName());
					/*}
					else Log.i("Peers","Adv-ul meu");*/
				}
			}
			if (peers.size() != aux)
			{
				Thread t = new Thread() {
					public void run()
					{
						fireContentChanged(getPeers());
					}
				};
				t.start();
			}
		}
	}

	/**
	 * Cauta in exterior parteneri care au atributul attr cu valoarea val.
	 * 
	 * @param attr
	 * @param val
	 * @param maxPeers nr maxim de peers
	 */
	private void searchRemote(String attr, String val, int maxPeers)
	{
		discovery.getRemoteAdvertisements(null, DiscoveryService.PEER, attr, val, maxPeers);
	}

	/**
	 * Cauta in cache parteneri care au atributul attr cu valoarea val.
	 * 
	 * @param attr
	 * @param val
	 */
	private void searchLocal(String attr, String val)
	{
		Enumeration<Advertisement> rez;
		try
		{
			rez = discovery.getLocalAdvertisements(DiscoveryService.PEER, attr, val);
			addPeers(rez);
		}
		catch (IOException e)
		{
			Log.e("Peers","nu s-au putut citi cache-ul local.");
		}
	}

	/**
	 * Sterge partenerii stocati local.
	 */
	public void flush()
	{
		try
		{
			Enumeration<Advertisement> eachAdv = discovery.getLocalAdvertisements(DiscoveryService.PEER, null, null);
			while (eachAdv.hasMoreElements())
			{
				Advertisement anAdv = (Advertisement) eachAdv.nextElement();
				discovery.flushAdvertisement(anAdv);
			}
			peers.clear();
		}
		catch (IOException e)
		{
			Log.e("Peers","nu s-au putut sterge peer advertisements.");
		}
	}

	/**
	 * @return lista de parteneri
	 */
	public HashMap<String, String> getPeers()
	{
		return new HashMap<String, String>(peers);
	}

	/**
	 * Se ocupa cu evenimentul generat de gasirea unui partener.
	 */
	public void discoveryEvent(DiscoveryEvent event)
	{
		DiscoveryResponseMsg rez = event.getResponse();
		addPeers(rez.getAdvertisements());
	}

	/**
	 * Trimite un mesaj prin output pipe.
	 * 
	 * @param toID id-ul partenerului la care se trimite mesajul
	 * @param message mesajul de trimis
	 * @return true daca s-a reusit trimiterea
	 */
	public boolean sendMessage(String toID, String message)
	{
		if (outputPipe != null)
		{
			Message msg = new Message();
			StringMessageElement senderID = new StringMessageElement("SenderID", peerID.toString(), null);
			StringMessageElement senderName = new StringMessageElement("SenderName", peerName, null);
			StringMessageElement receiverID = new StringMessageElement("ReceiverID", toID, null);
			StringMessageElement data = new StringMessageElement("Data", message, null);
			msg.addMessageElement(null, senderID);
			msg.addMessageElement(null, senderName);
			msg.addMessageElement(null, receiverID);
			msg.addMessageElement(null, data);
			try
			{
				return outputPipe.send(msg);
			}
			catch (IOException e)
			{
				Log.e("Peers","nu s-a putut trimite mesajul.");
			}
		}
		return false;
	}

	/**
	 * Se ocupa cu evenimentele generate de primirea unui mesaj prin input pipe.
	 */
	public void pipeMsgEvent(PipeMsgEvent event)
	{
		Message msg = event.getMessage();
		Log.i("Peers","PipeMsgEvent");
		if (msg != null)
		{
			final MessageElement receiverID = msg.getMessageElement(null, "ReceiverID");
			if (receiverID != null && receiverID.toString().equals(peerID.toString()))
			{
				final MessageElement senderID = msg.getMessageElement(null, "SenderID");
				if (senderID != null && !senderID.toString().equals(peerID.toString()))
				{
					final MessageElement senderName = msg.getMessageElement(null, "SenderName");
					final MessageElement data = msg.getMessageElement(null, "Data");
					if (senderName != null && data != null)
					{

						Log.i("Peers","s-a primit un mesaj de la " + senderName + " ce contine [" + data.toString() + "]");
						Thread t = new Thread() {
							public void run()
							{
								fireMessageReceived(senderID.toString(), senderName.toString(), data.toString());
							}
						};
						t.start();
					}
				}
			}
		}
	}

	/**
	 * Se ocupa cu evenimentul generat de crearea lui output pipe.
	 */
	public void outputPipeEvent(OutputPipeEvent event)
	{
		outputPipe = event.getOutputPipe();
		fireOutputPipeReady();
	}

	/**
	 * Adauga un <code>P2PListener</code> la clasa Peers.
	 */
	public synchronized void addP2PListener(P2PListener listener)
	{
		listenerList.add(P2PListener.class, listener);
	}

	/**
	 * Sterge <code>P2PListener</code> de la clasa Peers.
	 */
	public synchronized void removeP2PListener(P2PListener listener)
	{
		listenerList.remove(P2PListener.class, listener);
	}

	/**
	 * Notifica schimbarea continutului listei de parteneri.
	 * 
	 * @param peersList
	 */
	private synchronized void fireOutputPipeReady()
	{
		P2PListener[] listeners = listenerList.getListeners(P2PListener.class);

		for (int i = listeners.length - 1; i >= 0; --i)
		{
			listeners[i].stateChanged(new P2PEvent(this, P2PEvent.PEER_READY));
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
