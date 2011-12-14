package checkers.android;

import java.io.IOException;

import net.jxta.exception.PeerGroupException;

import checkers.p2p.JXTAService;
import checkers.p2p.event.P2PEvent;
import checkers.p2p.event.P2PListener;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class JocDameAndroidActivity extends Activity
{

	private JXTAService managerJXTA;
	private Thread jxtaThread;
	private Button startJXTAService, searchPeers;
	private TextView txt;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button startJXTAService = (Button) findViewById(R.id.startJXTAService);
		searchPeers = (Button) findViewById(R.id.searchPeers);
		txt = (TextView) findViewById(R.id.textView1);

		startJXTAService.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v)
			{
				try
				{
					managerJXTA = new JXTAService("Octa", getDir("jxta", MODE_PRIVATE));
					managerJXTA.addP2PListener(new P2PListener() {

						public void stateChanged(P2PEvent event)
						{
							if (event.getTip() == P2PEvent.CONNECTION_READY)
							{
								searchPeers.setEnabled(true);
								// managerJXTA.searchPeers();
							}
							else if (event.getTip() == P2PEvent.PEER_FOUND)
							{
								txt.append(event.getSenderName() + "\n");								
							}
							else if (event.getTip() == P2PEvent.MESSAGE_RECEIVED)
							{
								
							}

						}
					});
					managerJXTA.start();
				}
				catch (IOException e)
				{
					Log.e("JocDameAndroidActivity", e.getMessage());
				}
				catch (PeerGroupException e)
				{
					Log.e("JocDameAndroidActivity", e.getMessage());
				}

			}
		});

		searchPeers.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v)
			{
				if (managerJXTA != null)
				{
					managerJXTA.searchPeers();
				}
			}
		});
	}
}