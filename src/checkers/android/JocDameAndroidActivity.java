package checkers.android;

import java.io.IOException;

import net.jxta.exception.PeerGroupException;

import checkers.p2p.Connection;
import checkers.p2p.event.P2PEvent;
import checkers.p2p.event.P2PListener;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class JocDameAndroidActivity extends Activity implements View.OnClickListener, P2PListener
{

	private Connection managerJXTA;
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

		startJXTAService.setOnClickListener(this);

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

	public void onClick(View v)
	{
		try
		{
			managerJXTA = new Connection("Octa", getDir("jxta", MODE_PRIVATE));
			managerJXTA.addP2PListener(this);
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
	
	public void stateChanged(final P2PEvent event)
	{
		if (event.getTip() == P2PEvent.CONNECTION_READY)
		{
			// managerJXTA.searchPeers();
			this.runOnUiThread(new Runnable() {
				
				public void run()
				{
					searchPeers.setEnabled(true);
				}
			});
		}
		else if (event.getTip() == P2PEvent.PEER_FOUND)
		{
			this.runOnUiThread(new Runnable() {
				
				public void run()
				{
					txt.setText("");
					for(String item: event.getList().values())
					{
						txt.append(item + "\n");
					}
					
				}
			});
			
		}
		else if (event.getTip() == P2PEvent.MESSAGE_RECEIVED)
		{

		}

	}
}