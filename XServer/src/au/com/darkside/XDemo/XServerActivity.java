/**
 * This activity launches an X server and provides a screen for it.
 */
package au.com.darkside.XDemo;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
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

		_xServer = new XServer (this, null);

		FrameLayout		fl = (FrameLayout) findViewById (R.id.frame);

		_screenView = _xServer.getScreen ();
		fl.addView (_screenView);

		PowerManager	pm;

		pm = (PowerManager) getSystemService (Context.POWER_SERVICE);
		_wakeLock = pm.newWakeLock (PowerManager.SCREEN_DIM_WAKE_LOCK,
																"XServer");
		_wakeLock.acquire ();
	}

	/**
	 * Called with the activity is destroyed.
	 */
	@Override
	public void
	onDestroy () {
		super.onDestroy ();

    	_wakeLock.release ();
		_xServer.stop ();
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
}