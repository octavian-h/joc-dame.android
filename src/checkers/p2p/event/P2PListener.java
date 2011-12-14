package checkers.p2p.event;

/**
 * Interfata pentru listener-ul evenimentelor din reteaua P2P.
 * 
 * @author Hasna Octavian-Lucian
 */
public interface P2PListener extends java.util.EventListener
{
	public void stateChanged(P2PEvent event);
}
