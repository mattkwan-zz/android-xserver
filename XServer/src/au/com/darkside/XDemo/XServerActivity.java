/**
 * This activity launches an X server and provides a screen for it.
 */
package au.com.darkside.XDemo;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import au.com.darkside.XServer.R;
import au.com.darkside.XServer.ScreenView;
import au.com.darkside.XServer.XServer;

/**
 * @author Matthew Kwan
 *
 * This class launches an X server.
 */
public class XServerActivity extends Activity {
	private XServer		_xServer;
	private ScreenView	_screenView;
	private WakeLock	_wakeLock;

	private static final int	MENU_KEYBOARD = 1;
	private static final int	MENU_IP_ADDRESS = 2;
	private static final int	MENU_ACCESS_CONTROL = 3;
	private static final int	MENU_REMOTE_LOGIN = 4;
	private static final int	ACTIVITY_ACCESS_CONTROL = 1;

	/**
	 * Called when the activity is first created.
	 *
	 * @param savedInstanceState	Saved state.
	 */
	@Override
	public void
	onCreate (
		Bundle	savedInstanceState
	) {
		super.onCreate (savedInstanceState);
		setContentView (R.layout.main);

		int		port = 6000;
		Intent	intent = getIntent ();

			// If it was launched from an intent, get the port number.
		if (intent != null) {
			Uri		uri = intent.getData ();

			if (uri != null) {
				int		p = uri.getPort ();

				if (p >= 0) {
					if (p < 10)
						port = p + 6000;
					else
						port = p;
				}
			}
		}

		_xServer = new XServer (this, port, null);
		setAccessControl ();

		FrameLayout		fl = (FrameLayout) findViewById (R.id.frame);

		_screenView = _xServer.getScreen ();
		fl.addView (_screenView);

		PowerManager	pm;

		pm = (PowerManager) getSystemService (Context.POWER_SERVICE);
		_wakeLock = pm.newWakeLock (PowerManager.SCREEN_DIM_WAKE_LOCK,
																"XServer");
	}

	/**
	 * Called when the activity resumes.
	 */
	@Override
	public void
	onResume () {
		super.onResume ();
		_wakeLock.acquire ();
	}

	/**
	 * Called when the activity pauses.
	 */
	@Override
	public void
	onPause () {
		super.onPause ();
    	_wakeLock.release ();
	}

	/**
	 * Called when the activity is destroyed.
	 */
	@Override
	public void
	onDestroy () {
		_xServer.stop ();
		super.onDestroy ();
	}

	/**
	 * Called the first time a menu is needed.
	 *
	 * @param menu	The options menu in which you place your items.
	 * @return	True for the menu to be displayed.
	 */
	@Override
	public boolean
	onCreateOptionsMenu (
		Menu		menu
	) {
		MenuItem	item;

		item = menu.add (0, MENU_KEYBOARD, 0, "Keyboard");
		item.setIcon (android.R.drawable.ic_menu_add);

		item = menu.add (0, MENU_IP_ADDRESS, 0, "IP address");
		item.setIcon (android.R.drawable.ic_menu_info_details);

		item = menu.add (0, MENU_ACCESS_CONTROL, 0, "Access control");
		item.setIcon (android.R.drawable.ic_menu_edit);

		item = menu.add (0, MENU_REMOTE_LOGIN, 0, "Remote login");
		item.setIcon (android.R.drawable.ic_menu_upload);

		return true;
	}

	/**
	 * Called when a menu selection has been made.
	 *
	 * @param item	The menu item that was selected.
	 * @return	True if the menu selection has been handled.
	 */
	@Override
	public boolean
	onOptionsItemSelected (
		MenuItem	item
	) {
		super.onOptionsItemSelected (item);

		switch (item.getItemId ()) {
			case MENU_KEYBOARD:
				InputMethodManager	imm = (InputMethodManager)
							getSystemService (Service.INPUT_METHOD_SERVICE);

					// If anyone knows a better way to bring up the soft
					// keyboard, I'd love to hear about it.
				_screenView.requestFocus ();
				imm.hideSoftInputFromWindow (_screenView.getWindowToken(), 0);
				imm.toggleSoftInput (InputMethodManager.SHOW_FORCED, 0);
				return true;
			case MENU_IP_ADDRESS:
				showDialog (MENU_IP_ADDRESS);
				return true;
			case MENU_ACCESS_CONTROL:
				launchAccessControlEditor ();
				return true;
			case MENU_REMOTE_LOGIN:
				launchSshApp ();
				return true;
		}

		return false;
	}

	/**
	 * Return a string describing the IP address(es) of this device.
	 *
	 * @return	A string describing the IP address(es) of this device.
	 */
	private String
	getAddressInfo () {
		String		s = "Listening on port 6000";

		try {
			for (Enumeration<NetworkInterface> nie =
								NetworkInterface.getNetworkInterfaces ();
												nie.hasMoreElements ();) {
				NetworkInterface	ni = nie.nextElement ();

				for (Enumeration<InetAddress> iae = ni.getInetAddresses ();
												iae.hasMoreElements ();) {
					InetAddress		ia = iae.nextElement ();

					if (ia.isLoopbackAddress ())
						continue;

					s += "\n" + ni.getDisplayName () + ": "
													+ ia.getHostAddress ();
				}
			}
		} catch (Exception e) {
			s += "\nError: " + e.getMessage ();
		}

		return s;
	}

    /**
     * This is called when a dialog is requested.
     *
     * @param id	Identifies the dialog to create.
     */
	@Override
	protected Dialog
	onCreateDialog (
		int			id
	) {
		if (id != MENU_IP_ADDRESS)
			return null;

		AlertDialog.Builder		builder = new AlertDialog.Builder (this);

		builder.setTitle ("IP address")
			.setMessage (getAddressInfo ())
			.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
				public void onClick (DialogInterface dialog, int id) {
					dialog.cancel ();
				}
			});
 
		return builder.create ();
    }

	/**
	 * Load the access control hosts from persistent storage.
	 */
	private void
	setAccessControl () {
		SharedPreferences	prefs = getSharedPreferences ("AccessControlHosts",
															MODE_PRIVATE);
		Map<String, ?>		map = prefs.getAll ();
		HashSet<Integer>	hosts = _xServer.getAccessControlHosts ();

		hosts.clear ();
		if (!map.isEmpty ()) {
			Set<String>		set = map.keySet ();

			for (String s: set) {
				try {
					int		host = (int) Long.parseLong (s, 16);

					hosts.add (host);
				} catch (Exception e) {
				}
			}
		}

		_xServer.setAccessControl (!hosts.isEmpty ());
	}

	/**
	 * Called when an activity returns a result.
	 */
	@Override
	protected void
	onActivityResult (
		int			requestCode,
		int			resultCode,
		Intent		data
	) {
		if (requestCode == ACTIVITY_ACCESS_CONTROL && resultCode == RESULT_OK)
			setAccessControl ();
	}

	/**
	 * Launch the access control list editor.
	 */
	private void
	launchAccessControlEditor () {
    	Intent		intent = new Intent (this, AccessControlEditor.class);
    	
    	startActivityForResult (intent, ACTIVITY_ACCESS_CONTROL);
	}

	/**
	 * Launch an application.
	 */
	private boolean
	launchApp (
		String	pkg,
		String	cls
	) {
		Intent		intent = new Intent (Intent.ACTION_MAIN);

		intent.setComponent (new ComponentName (pkg, cls));
		try {
			startActivity (intent);
		} catch (ActivityNotFoundException e) {
			return false;
		}

		return true;
	}

	/**
	 * Launch an application that will allow an SSH login.
	 */
	private void
	launchSshApp () {
		if (launchApp ("org.connectbot", "org.connectbot.HostListActivity"))
			return;
		if (launchApp ("com.madgag.ssh.agent",
									"com.madgag.ssh.agent.HostListActivity"))
			return;
		if (launchApp ("sk.vx.connectbot",
										"sk.vx.connectbot.HostListActivity"))
			return;

		Toast.makeText (this,
						"The ConnectBot application needs to be installed",
						Toast.LENGTH_LONG).show ();
	}
}