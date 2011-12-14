package checkers.p2p.event;

import java.util.HashMap;

/**
 * Evenimentele din reteaua P2P.
 * 
 * @author Hasna Octavian-Lucian
 */
public class P2PEvent extends java.util.EventObject
{
	private static final long serialVersionUID = 1L;

	public static final int PEER_FOUND = 1;
	public static final int GROUP_FOUND = 2;
	public static final int PEER_SEARCH_FINISHED = 3;
	public static final int GROUP_SEARCH_FINISHED = 4;
	public static final int MESSAGE_RECEIVED = 5;
	public static final int PEER_READY = 6;
	public static final int CONNECTION_READY = 7;

	private int tip;
	private HashMap<String, String> list;
	private String senderID, senderName;
	private String message;

	public P2PEvent(Object source, int tip)
	{
		this(source, tip, null, "", "", "");
	}

	public P2PEvent(Object source, int tip, HashMap<String, String> list)
	{
		this(source, tip, list, "", "", "");
	}

	public P2PEvent(Object source, int tip, String senderID, String senderName, String message)
	{
		this(source, tip, null, senderID, senderName, message);
	}

	private P2PEvent(Object source, int tip, HashMap<String, String> list, String senderID,
			String senderName, String message)
	{
		super(source);
		this.setTip(tip);
		this.setList(list);
		this.setSenderID(senderID);
		this.setSenderName(senderName);
		this.setMessage(message);
	}

	private void setMessage(String message)
	{
		this.message = message;
	}

	public String getMessage()
	{
		return message;
	}

	private void setSenderID(String senderID)
	{
		this.senderID = senderID;
	}

	public String getSenderID()
	{
		return senderID;
	}

	private void setList(HashMap<String, String> list)
	{
		this.list = list;
	}

	public HashMap<String, String> getList()
	{
		return list;
	}

	private void setTip(int tip)
	{
		this.tip = tip;
	}

	public int getTip()
	{
		return tip;
	}

	private void setSenderName(String senderName)
	{
		this.senderName = senderName;
	}

	public String getSenderName()
	{
		return senderName;
	}
}
