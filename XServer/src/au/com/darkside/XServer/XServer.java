/**
 * This class implements an X Windows server.
 */
package au.com.darkside.XServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * @author Matthew Kwan
 *
 * This class implements an X Windows server.
 */
public class XServer {
	public final short		ProtocolMajorVersion = 11;
	public final short		ProtocolMinorVersion = 0;
	public final String		vendor = "Open source";
	public final int		ReleaseNumber = 0;

	private final Context						_context;
	private final Class<?>						_windowManagerClass;
	private final Vector<Format>				_formats;
	private final Hashtable<Integer, Resource>	_resources;

	private final Vector<ClientComms>		_clients;
	private final int			_clientIdBits = 20;
	private final int			_clientIdStep = (1 << _clientIdBits);
	private int					_clientIdBase = _clientIdStep;

	private final Hashtable<Integer, Atom>		_atoms;
	private final Hashtable<String, Atom>		_atomNames;
	private int									_maxAtomId = 0;
	private final Hashtable<Integer, Selection>	_selections;

	private final Keyboard		_keyboard;
	private final Pointer		_pointer;
	private final Font			_defaultFont;
	private final Visual		_rootVisual;
	private ScreenView			_screen = null;
	private String[]			_fontPath = null;
	private AcceptThread		_acceptThread = null;
	private Date				_timestamp;
	private ClientComms			_grabClient;

	private int				_screenSaverTimeout = 0;
	private int				_screenSaverInterval = 0;
	private int				_preferBlanking = 1;
	private int				_allowExposures = 0;
	private long			_screenSaverTime = 0;
	private CountDownTimer	_screenSaverCountDownTimer = null;

	/**
	 * Constructor.
	 *
	 * @param c	The application context.
	 * @param windowManagerClass	Window manager service. Can be null.
	 */
	public XServer (
		Context		c,
		Class<?>	windowManagerClass
	) {
		_context = c;
		_windowManagerClass = windowManagerClass;
		_formats = new Vector<Format>();
		_resources = new Hashtable<Integer, Resource>();
		_clients = new Vector<ClientComms>();
		_atoms = new Hashtable<Integer, Atom>();
		_atomNames = new Hashtable<String, Atom>();
		_selections = new Hashtable<Integer, Selection>();

		_formats.add (new Format ((byte) 32, (byte) 24, (byte) 8));

		_keyboard = new Keyboard ();
		_pointer = new Pointer ();

		_defaultFont = new Font (1, this, null, null);
		addResource (_defaultFont);
		addResource (new Cursor (2, this, null, (Font) null, (Font) null,
											0, 1, 0xff000000, 0xffffffff));

		_screen = new ScreenView (_context, this, 3, pixelsPerMillimeter ());

		Colormap	cmap = new Colormap (4, this, null, _screen);

		cmap.setInstalled (true);
		addResource (cmap);

		_rootVisual = new Visual (1);
		Atom.registerPredefinedAtoms (this);

		_timestamp = new Date ();
	}

	/**
	 * Start the thread that listens on the socket.
	 * Also start the window manager if one is specified.
	 * 
	 * @return	True if the thread is started successfully.
	 */
	public synchronized boolean
	start () {
		if (_acceptThread != null)
			return true;	// Already running.

		try {
			_acceptThread = new AcceptThread (6000);
			_acceptThread.start ();
		} catch (IOException e) {
			return false;
		}

		if (_windowManagerClass != null)
			_context.startService (new Intent (_context, _windowManagerClass));

		resetScreenSaver ();

		return true;
	}

	/**
	 * Stop listening on the socket and terminate all clients.
	 */
	public synchronized void
	stop () {
		if (_acceptThread != null) {
			_acceptThread.cancel ();
			_acceptThread = null;
		}

		for (ClientComms c: _clients)
			c.cancel ();

		_clients.clear ();
		_grabClient = null;
	}

	/**
	 * Reset the server.
	 * This should be called when the last client disconnects with a
	 * close-down mode of Destroy.
	 */
	private void
	reset () {
		Iterator<Hashtable.Entry<Integer, Resource>>
									it = _resources.entrySet().iterator ();

			// Remove all client-allocated resources.
		while (it.hasNext ()) {
			Hashtable.Entry<Integer, Resource>	entry = it.next ();

			if (entry.getKey () > _clientIdStep)
				it.remove ();
		}

		_screen.removeNonDefaultColormaps ();

		if (_atoms.size () != Atom.numPredefinedAtoms ()) {
			_atoms.clear ();
			_atomNames.clear ();
			Atom.registerPredefinedAtoms (this);
		}

		_selections.clear ();
		_timestamp = new Date ();
	}

	/**
	 * Return the server's application context.
	 *
	 * @return	The server's application context.
	 */
	public Context
	getContext () {
		return _context;
	}

	/**
	 * Return the internet address the server is listening on.
	 *
	 * @return The internet address the server is listening on.
	 */
	public InetAddress
	getInetAddress () {
		if (_acceptThread == null)
			return null;

		return _acceptThread.getInetAddress ();
	}

	/**
	 * Return the number of milliseconds since the last reset.
	 *
	 * @return	The number of milliseconds since the last reset.
	 */
	public synchronized int
	getTimestamp () {
		long	diff = System.currentTimeMillis () - _timestamp.getTime ();

		if (diff <= 0)
			return 1;

		return (int) diff;
	}

	/**
	 * Remove a client from the list of active clients.
	 *
	 * @param client	The client to remove.
	 */
	public synchronized void
	removeClient (
		ClientComms		client
	) {
		for (Selection sel: _selections.values ())
			sel.clearClient (client);

		_clients.remove (client);
		if (_grabClient == client)
			_grabClient = null;

		if (client.getCloseDownMode () == ClientComms.Destroy
												&& _clients.size () == 0)
			reset ();
	}

	/**
	 * Disable all clients except this one.
	 *
	 * @param client	The client issuing the grab.
	 */
	public void
	grabServer (
		ClientComms		client
	) {
		_grabClient = client;
	}

	/**
	 * End the server grab.
	 *
	 * @param client	The client issuing the grab.
	 */
	public void
	ungrabServer (
		ClientComms		client
	) {
		if (_grabClient == client)
			_grabClient = null;
	}

	/**
	 * Return true if processing is allowed. This is only false if the
	 * server has been grabbed by another client.
	 *
	 * @param client	The client checking if processing is allowed.
	 *
	 * @return	True if processing is allowed for the client.
	 */
	public boolean
	processingAllowed (
		ClientComms		client
	) {
		if (_grabClient == null)
			return true;

		return _grabClient == client;
	}

	/**
	 * Get the X server's keyboard.
	 *
	 * @return	The keyboard used by the X server.
	 */
	public Keyboard
	getKeyboard () {
		return _keyboard;
	}

	/**
	 * Get the X server's pointer.
	 *
	 * @return	The pointer used by the X server.
	 */
	public Pointer
	getPointer () {
		return _pointer;
	}

	/**
	 * Get the server's font path.
	 *
	 * @return	The server's font path.
	 */
	public String[]
	getFontPath () {
		return _fontPath;
	}

	/**
	 * Set the server's font path.
	 *
	 * @param path	The new font path.
	 */
	public void
	setFontPath (
		String[]	path
	) {
		_fontPath = path;
	}

	/**
	 * Return the screen attached to the display.
	 *
	 * @return	The screen attached to the display.
	 */
	public ScreenView
	getScreen () {
		return _screen;
	}

	/**
	 * Return the number of pixels per millimeter on the display.
	 *
	 * @return	The number of pixels per millimeter on the display.
	 */
	private float
	pixelsPerMillimeter () {
		DisplayMetrics	metrics = new DisplayMetrics();
		WindowManager	wm = (WindowManager) _context.getSystemService
												(Context.WINDOW_SERVICE);

		wm.getDefaultDisplay().getMetrics (metrics);
		Font.setDpi ((int) metrics.ydpi);	// Use the value since we have it.

		return metrics.xdpi / 25.4f;
	}

	/**
	 * Get the number of pixmap formats.
	 *
	 * @return	The number of pixmap formats.
	 */
	public int
	getNumFormats () {
		return _formats.size ();
	}

	/**
	 * Write details of all the pixmap formats.
	 *
	 * @param io	The input/output stream.
	 * @throws IOException
	 */
	public synchronized void
	writeFormats (
		InputOutput		io
	) throws IOException {
		for (Format f: _formats)
			f.write (io);
	}

	/**
	 * Return the default font.
	 * 
	 * @return	The default font.
	 */
	public Font
	getDefaultFont () {
		return _defaultFont;
	}

	/**
	 * Return the root visual.
	 *
	 * @return	The root visual.
	 */
	public Visual
	getRootVisual () {
		return _rootVisual;
	}

	/**
	 * Add an atom.
	 *
	 * @param a		The atom to add.
	 */
	public synchronized void
	addAtom (
		Atom		a
	) {
		_atoms.put (a.getId (), a);
		_atomNames.put (a.getName (), a);

		if (a.getId () > _maxAtomId)
			_maxAtomId = a.getId ();
	}

	/**
	 * Return the atom with the specified ID.
	 *
	 * @param id	The atom ID.
	 * @return	The specified atom, or null if it doesn't exist.
	 */
	public synchronized Atom
	getAtom (
		int			id
	) {
		if (!_atoms.containsKey (id))	// No such atom.
			return null;

		return _atoms.get (id);
	}

	/**
	 * Return the atom with the specified name.
	 *
	 * @param name	The atom's name.
	 * @return	The specified atom, or null if it doesn't exist.
	 */
	public synchronized Atom
	findAtom (
		final String	name
	) {
		if (!_atomNames.containsKey (name))
			return null;

		return _atomNames.get (name);
	}

	/**
	 * Does the atom with specified ID exist?
	 *
	 * @param id	The atom ID.
	 * @return	True if an atom with the ID exists.
	 */
	public synchronized boolean
	atomExists (
		int			id
	) {
		return _atoms.containsKey (id);
	}

	/**
	 * Get the ID of the next free atom.
	 *
	 * @return	The ID of the next free atom.
	 */
	public synchronized int
	nextFreeAtomId () {
		return ++_maxAtomId;
	}

	/**
	 * Return the selection with the specified ID.
	 *
	 * @param id	The selection ID.
	 * @return	The specified selection, or null if it doesn't exist.
	 */
	public synchronized Selection
	getSelection (
		int			id
	) {
		if (!_selections.containsKey (id))	// No such selection.
			return null;

		return _selections.get (id);
	}

	/**
	 * Add a selection.
	 *
	 * @param sel	The selection to add.
	 */
	public synchronized void
	addSelection (
		Selection	sel
	) {
		_selections.put (sel.getId (), sel);
	}

	/**
	 * Add a resource.
	 *
	 * @param r	The resource to add.
	 */
	public synchronized void
	addResource (
		Resource	r
	) {
		_resources.put (r.getId (), r);
	}

	/**
	 * Return the resource with the specified ID.
	 *
	 * @param id	The resource ID.
	 * @return	The specified resource, or null if it doesn't exist.
	 */
	public synchronized Resource
	getResource (
		int			id
	) {
		if (!resourceExists (id))
			return null;

		return _resources.get (id);
	}

	/**
	 * Does the resource with specified ID exist?
	 *
	 * @param id	The resource ID.
	 * @return	True if a resource with the ID exists.
	 */
	public synchronized boolean
	resourceExists (
		int			id
	) {
		return _resources.containsKey (id);
	}

	/**
	 * Free the resource with the specified ID.
	 *
	 * @param id	The resource ID. 
	 */
	public synchronized void
	freeResource (
		int			id
	) {
		_resources.remove (id);
	}

	/**
	 * If client is null, destroy the resources of all clients that have
	 * terminated in RetainTemporary mode. Otherwise destroy all resources
	 * associated with the client, which has terminated with mode
	 * RetainPermanant or RetainTemporary.
	 *
	 * @param client	The terminated client, or null.
	 */
	public synchronized void
	destroyClientResources (
		ClientComms		client
	) {
		Collection<Resource>	rc = _resources.values ();

		if (client == null) {
			for (Resource r: rc) {
				ClientComms		c = r.getClientComms ();

				if (!c.isConnected () && r.getCloseDownMode ()
											== ClientComms.RetainTemporary)
					r.delete ();
			}
		} else {
			for (Resource r: rc)
				if (r.getClientComms () == client)
					r.delete ();
		}
	}

	/**
	 * Send a MappingNotify to all clients.
	 *
	 * @param request	0=Modifier, 1=Keyboard, 2=Pointer
	 * @param firstKeycode	First keycode in new keyboard map.
	 * @param keycodeCount	Number of keycodes in new keyboard map.
	 */
	public synchronized void
	sendMappingNotify (
		int			request,
		int			firstKeycode,
		int			keycodeCount
	) {
		for (ClientComms c: _clients) {
			try {
				EventCode.sendMappingNotify (c, request, firstKeycode,
															keycodeCount);
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Process a QueryExtension request.
	 * No extensions are supported.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public void
	processQueryExtensionRequest (
		InputOutput		io,
		int				sequenceNumber,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.QueryExtension, 0);
			return;
		}

		int			length = io.readShort ();	// Length of name.
		int			pad = -length & 3;

		io.readSkip (2);	// Unused.
		bytesRemaining -= 4;

		if (bytesRemaining != length + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.QueryExtension, 0);
			return;
		}

		io.readSkip (length);	// Extension name. Not implemented.
		io.readSkip (pad);	// Unused.

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writeByte ((byte) 0);	// Present. 0 = false.
			io.writeByte ((byte) 0);	// Major opcode.
			io.writeByte ((byte) 0);	// First event.
			io.writeByte ((byte) 0);	// First error.
			io.writePadBytes (20);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Process a ChangeHosts request.
	 * Does nothing.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param mode	Change mode. 0=Insert, 1=Delete.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public void
	processChangeHostsRequest (
		InputOutput		io,
		int				sequenceNumber,
		int				mode,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
												RequestCode.ChangeHosts, 0);
			return;
		}

		io.readByte ();	// Family. 0=Internet, 1=DECnet, 2=Chaos.
		io.readSkip (1);	// Unused.

		int			length = io.readShort ();	// Length of address.
		int			pad = -length & 3;

		bytesRemaining -= 4;
		if (bytesRemaining != length + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
												RequestCode.ChangeHosts, 0);
			return;
		}

		io.readSkip (length);	// Address. Not implemented.
		io.readSkip (pad);	// Unused.
	}

	/**
	 * Write the list of extensions supported by the server.
	 * No extensions are supported.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @throws IOException
	 */
	public void
	writeListExtensions (
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writePadBytes (24);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Set the screen saver parameters.
	 *
	 * @param timeout	Timeout period, in seconds. 0=disabled, -1=default.
	 * @param interval	Interval in seconds. 0=disabled, -1=default.
	 * @param preferBlanking	0=No, 1=Yes, 2=Default.
	 * @param allowExposures	0=No, 1=Yes, 2=Default.
	 */
	public void
	setScreenSaver (
		int			timeout,
		int			interval,
		int			preferBlanking,
		int			allowExposures
	) {
		if (timeout == -1)
			_screenSaverTimeout = 0;	// Default timeout.
		else
			_screenSaverTimeout = timeout;

		if (interval == -1)
			_screenSaverInterval = 0;	// Default interval.
		else
			_screenSaverInterval = interval;

		_preferBlanking = preferBlanking;
		_allowExposures = allowExposures;

		resetScreenSaver ();
	}

	/**
	 * Reset the screen saver timer.
	 */
	public void
	resetScreenSaver () {
		long		now = System.currentTimeMillis () / 1000;

		if (now == _screenSaverTime)
			return;

		_screenSaverTime = now;

		if (_screenSaverCountDownTimer != null) {
			_screenSaverCountDownTimer.cancel ();
			_screenSaverCountDownTimer = null;
		}

		if (_screenSaverTimeout != 0) {
			long		time = _screenSaverTimeout * 1000;

			_screenSaverCountDownTimer = new CountDownTimer (time, time + 1) {
				public void onTick (long millis) {}
				public void onFinish () {
					_screen.blank (true);
					_screenSaverCountDownTimer = null;
				}
			};
			_screenSaverCountDownTimer.start ();
		}
	}

	/**
	 * Reply to GetScreenSaver request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @throws IOException
	 */
	public void
	writeScreenSaver (
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writeShort ((short) _screenSaverTimeout);	// Timeout.
			io.writeShort ((short) _screenSaverInterval);	// Interval.
			io.writeByte ((byte) _preferBlanking);	// Prefer blanking.
			io.writeByte ((byte) _allowExposures);	// Allow exposures.
			io.writePadBytes (18);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Reply to a ListHosts request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @throws IOException
	 */
	public void
	writeListHosts (
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length. No hosts.
			io.writePadBytes (22);	// Unused.
		}
		io.flush ();
	}

	/**
	 * This thread runs while listening for incoming connections.
	 * It runs until it is cancelled.
	 *
	 * @author Matthew Kwan
	 */
	private class AcceptThread extends Thread {
		private final ServerSocket	_serverSocket;

		/**
		 * Constructor.
		 *
		 * @param port	The port to listen on.
		 *
		 * @throws IOException
		 */
		AcceptThread (
			int			port
		) throws IOException {
			_serverSocket = new ServerSocket (port);
		}

		/**
		 * Return the internet address that is accepting connections.
		 * May be null.
		 *
		 * @return	The internet address that is accepting connections.
		 */
		public InetAddress
		getInetAddress () {
			return _serverSocket.getInetAddress ();
		}

		/*
		 * Run the thread.
		 */
		public void
		run () {
			while (true) {
				Socket		socket;

				try {
						// This is a blocking call and will only return on a
						// successful connection or an exception.
					socket = _serverSocket.accept ();
				} catch (IOException e) {
					break;
				}

				synchronized (this) {
					ClientComms		c;

					try {
						c = new ClientComms (XServer.this, socket,
											_clientIdBase, _clientIdStep - 1);
						_clients.add (c);
						c.start ();
						_clientIdBase += _clientIdStep;
					} catch (IOException e) {
						try {
							socket.close ();
						} catch (IOException e2) {
						}
					}
				}
            }
		}

		/*
		 * Cancel the thread.
		 */
		public void
		cancel () {
			try {
				_serverSocket.close ();
			} catch (IOException e) {
			}
		}
    }
}