/**
 * This class implements an X Windows screen.
 */
package au.com.darkside.XServer;

import java.io.IOException;
import java.util.Vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * @author Matthew Kwan
 *
 * This class implements an X Windows screen.
 * It also implements the screen's root window.
 */
public class ScreenView extends View {
	private final XServer			_xServer;
	private final int				_rootId;
	private Window					_rootWindow = null;
	private Colormap				_defaultColormap = null;
	private final Vector<Colormap>	_installedColormaps;
	private final float				_pixelsPerMillimeter;

	private Cursor		_currentCursor;
	private int			_currentCursorX;
	private int			_currentCursorY;
	private Cursor		_drawnCursor = null;
	private int			_drawnCursorX;
	private int			_drawnCursorY;
	private Window		_motionWindow = null;
	private int			_motionX;
	private int			_motionY;
	private int			_buttons = 0;

	private Window		_grabPointerWindow = null;
	private int			_grabPointerTime = 0;
	private boolean		_grabPointerOwnerEvents = false;
	private boolean		_grabPointerSynchronous = false;
	private Window		_grabKeyboardWindow = null;
	private int			_grabKeyboardTime = 0;
	private boolean		_grabKeyboardOwnerEvents = false;
	private boolean		_grabKeyboardSynchronous = false;
	private Cursor		_grabCursor = null;
	private Window		_grabConfineWindow = null;
	private int			_grabEventMask = 0;

	/**
	 * Constructor.
	 *
	 * @param c	The application context.
	 * @param xServer	The X server.
	 * @param rootId	The ID of the root window, to be created later.
	 * @param pixelsPerMillimeter	Screen resolution.
	 */
	public ScreenView (
		Context		c,
		XServer		xServer,
		int			rootId,
		float		pixelsPerMillimeter
	) {
		super (c);

		setFocusable (true);
		setFocusableInTouchMode (true);

		_xServer = xServer;
		_rootId = rootId;
		_installedColormaps = new Vector<Colormap>();
		_pixelsPerMillimeter = pixelsPerMillimeter;
	}

	/**
	 * Return the screen's root window.
	 *
	 * @return	The screen's root window.
	 */
	public Window
	getRootWindow () {
		return _rootWindow;
	}

	/**
	 * Return the screen's default colormap.
	 *
	 * @return	The screen's default colormap.
	 */
	public Colormap
	getDefaultColormap () {
		return _defaultColormap;
	}

	/**
	 * Return the current pointer X coordinate.
	 *
	 * @return	The current pointer X coordinate.
	 */
	public int
	getPointerX () {
		return _currentCursorX;
	}

	/**
	 * Return the current pointer Y coordinate.
	 *
	 * @return	The current pointer Y coordinate.
	 */
	public int
	getPointerY () {
		return _currentCursorY;
	}

	/**
	 * Return a mask indicating the current state of the pointer and
	 * modifier buttons.
	 *
	 * @return	A mask indicating the current state of the buttons.
	 */
	public int
	getButtons () {
		return _buttons;
	}

	/**
	 * Add an installed colormap.
	 *
	 * @param cmap	The colormap to add.
	 */
	public void
	addInstalledColormap (
		Colormap		cmap
	) {
		_installedColormaps.add (cmap);
		if (_defaultColormap == null)
			_defaultColormap = cmap;
	}

	/**
	 * Remove an installed colormap.
	 *
	 * @param cmap	The colormap to remove.
	 */
	public void
	removeInstalledColormap (
		Colormap		cmap
	) {
		_installedColormaps.remove (cmap);
		if (_defaultColormap == cmap) {
			if (_installedColormaps.size () == 0)
				_defaultColormap = null;
			else
				_defaultColormap = _installedColormaps.firstElement ();
		}
	}

	/**
	 * Remove all colormaps except the default one.
	 */
	public void
	removeNonDefaultColormaps () {
		if (_installedColormaps.size () < 2)
			return;

		_installedColormaps.clear ();
		if (_defaultColormap != null)
			_installedColormaps.add (_defaultColormap);
	}

	/**
	 * Called when a window is deleted, usually due to a client disconnecting.
	 * Removes all references to the window.
	 *
	 * @param w	The window being deleted.
	 */
	public void
	deleteWindow (
		Window		w
	) {
		if (_grabPointerWindow == w || _grabConfineWindow == w) {
			_grabPointerWindow = null;
			_grabCursor = null;
			_grabConfineWindow = null;
			updatePointer (2);
		} else {
			updatePointer (0);
		}

		if (_grabKeyboardWindow == w)
			_grabKeyboardWindow = null;
	}

	/**
	 * Called when the view needs drawing.
	 *
	 * @param canvas	The canvas on which the view will be drawn.
	 */
	@Override
	protected void
	onDraw (
		Canvas		canvas
	) {
		if (_rootWindow == null) {
			super.onDraw (canvas);
			return;
		}

		Paint		paint = new Paint ();
		Rect		bounds = canvas.getClipBounds ();

		if (bounds.right == 0 && bounds.bottom == 0)
			bounds = null;

		_rootWindow.draw (canvas, paint, bounds);

		canvas.drawBitmap (_currentCursor.getBitmap (),
					_currentCursorX - _currentCursor.getHotspotX (),
					_currentCursorY - _currentCursor.getHotspotY (), null);

		_drawnCursor = _currentCursor;
		_drawnCursorX = _currentCursorX;
		_drawnCursorY = _currentCursorY;
	}

	/**
	 * Called when the size changes.
	 * Create the root window.
	 *
	 * @param width	The new width.
	 * @param height	The new height.
	 * @param oldWidth	The old width.
	 * @param oldHeight	The old height.
	 */
	@Override
	protected void
	onSizeChanged (
		int			width,
		int			height,
		int			oldWidth,
		int			oldHeight
	) {
		super.onSizeChanged (width, height, oldWidth, oldHeight);

		_rootWindow = new Window (_rootId, _xServer, null, this, null,
											0, 0, width, height, 0, false);
		_xServer.addResource (_rootWindow);

		_currentCursor = _rootWindow.getCursor ();
		_currentCursorX = width / 2;
		_currentCursorY = height / 2;
		_drawnCursorX = _currentCursorX;
		_drawnCursorY = _currentCursorY;
		_motionX = _currentCursorX;
		_motionY = _currentCursorY;
		_motionWindow = _rootWindow;

			// Everything set up, so start listening for clients.
		_xServer.start ();
	}

	/**
	 * Move the pointer on the screen.
	 *
	 * @param x	New X coordinate.
	 * @param y	New Y coordinate.
	 * @param cursor	The cursor to draw.
	 */
	private void
	movePointer (
		int			x,
		int			y,
		Cursor		cursor
	) {
		if (_drawnCursor != null) {
			int			left = _drawnCursorX - _drawnCursor.getHotspotX ();
			int			top = _drawnCursorY - _drawnCursor.getHotspotY ();
			Bitmap		bm = _drawnCursor.getBitmap ();

			postInvalidate (left, top, left + bm.getWidth (),
													top + bm.getHeight ());
			_drawnCursor = null;
		}

		_currentCursor = cursor;
		_currentCursorX = x;
		_currentCursorY = y;

		int			left = x - cursor.getHotspotX ();
		int			top = y - cursor.getHotspotY ();
		Bitmap		bm = cursor.getBitmap ();

		postInvalidate (left, top, left + bm.getWidth (),
													top + bm.getHeight ());
	}

	/**
	 * Update the location of the pointer.
	 *
	 * @param x	New X coordinate.
	 * @param y	New Y coordinate.
	 * @param mode	0=Normal, 1=Grab, 2=Ungrab
	 */
	public void
	updatePointerPosition (
		int			x,
		int			y,
		int			mode
	) {
		Window		w;
		Cursor		c;

		if (_grabConfineWindow != null) {
			Rect		rect = _grabConfineWindow.getIRect ();

			if (x < rect.left)
				x = rect.left;
			else if (x >= rect.right)
				x = rect.right - 1;

			if (y < rect.top)
				y = rect.top;
			else if (y >= rect.bottom)
				y = rect.bottom - 1;
		}

		if (_grabPointerWindow != null)
			w = _grabPointerWindow;
		else
			w = _rootWindow.windowAtPoint (x, y);

		if (_grabCursor != null)
			c = _grabCursor;
		else
			c = w.getCursor ();

		if (c != _currentCursor || x != _currentCursorX
													|| y != _currentCursorY)
			movePointer (x, y, c);

		if (w != _motionWindow) {
			_motionWindow.leaveEnterNotify (x, y, w, mode);
			_motionWindow = w;
			_motionX = x;
			_motionY = y;
		} else if (x != _motionX || y != _motionY) {
			if (_grabPointerWindow == null) {
				w.motionNotify (x, y, _buttons & 0xff00, null);
			} else if (!_grabPointerSynchronous) {
				w.grabMotionNotify (x, y, _buttons & 0xff00, _grabEventMask,
													_grabPointerOwnerEvents);
			}	// Else need to queue the events for later.

			_motionX = x;
			_motionY = y;
		}
	}

	/**
	 * Update the pointer in case its glyph has changed.
	 *
	 * @param mode	0=Normal, 1=Grab, 2=Ungrab
	 */
	public void
	updatePointer (
		int			mode
	) {
		updatePointerPosition (_currentCursorX, _currentCursorY, mode);
	}

	/**
	 * Called when a pointer button is pressed/released.
	 *
	 * @param button	The button that was pressed/released.
	 * @param pressed	True if the button was pressed.
	 */
	private void
	updatePointerButtons (
		int			button,
		boolean		pressed
	) {
		int			mask = 0x80 << button;

		if (pressed)
			_buttons |= mask;
		else
			_buttons &= ~mask;

		if (_grabPointerWindow == null) {
			Window		w = _rootWindow.windowAtPoint (_motionX, _motionY);

			w.buttonNotify (pressed, _motionX, _motionY, button, null);
			if (pressed) {
				int			em = w.getEventMask ();

				_grabPointerWindow = w;
				_grabCursor = w.getCursor ();
				_grabConfineWindow = null;
				_grabEventMask = em & EventCode.MaskAllPointer;
				_grabPointerOwnerEvents = (em & EventCode.MaskOwnerGrabButton)
																		!= 0;
				_grabPointerSynchronous = false;
				_grabKeyboardSynchronous = false;
			}
		} else {
			if (!_grabPointerSynchronous) {
				_grabPointerWindow.grabButtonNotify (pressed, _motionX,
										_motionY, button, _grabEventMask,
												_grabPointerOwnerEvents);
			}	// Else need to queue the events for later.

			if (!pressed && (_buttons & 0xff00) == 0) {
				_grabPointerWindow = null;
				_grabCursor = null;
				_grabConfineWindow = null;
			}
		}
	}

	/**
	 * Called when shift/alt keys are pressed/released.
	 *
	 * @param keycode	Keycode of the key.
	 * @param pressed	True if pressed, false if released.
	 * @param state	Current state of the modifier keys.
	 */
	private void
	updateModifiers (
		int			keycode,
		boolean		pressed,
		int			state
	) {
		int			mask = 0;

		if ((state & KeyEvent.META_SHIFT_ON) != 0)
			mask |= 1;	// Shift.
		if ((state & KeyEvent.META_ALT_ON) != 0)
			mask |= 8;	// Mod1.

		_buttons = (_buttons & 0xff00) | mask;
	}

	/**
	 * Called when a key is pressed or released.
	 *
	 * @param keycode	Keycode of the key.
	 * @param pressed	True if pressed, false if released.
	 */
	private void
	notifyKeyPressedReleased (
		int			keycode,
		boolean		pressed
	) {
		if (_grabKeyboardWindow == null) {
			Window		w = _rootWindow.windowAtPoint (_motionX, _motionY);

			w.keyNotify (pressed, _motionX, _motionY, keycode, null);
		} else if (!_grabKeyboardSynchronous){
			_grabKeyboardWindow.grabKeyNotify (pressed, _motionX, _motionY,
										keycode, _grabKeyboardOwnerEvents);
		}	// Else need to queue keyboard events.

		_xServer.getKeyboard().updateKeymap (keycode, pressed);
	}

	/**
	 * Called when there is a touch event.
	 *
	 * @param event	The touch event.
	 * @return	True if the event was handled.
	 */
	@Override
	public boolean
	onTouchEvent (
		MotionEvent		event
	) {
		if (_rootWindow == null)
			return false;

		updatePointerPosition ((int) event.getX (), (int) event.getY (), 0);

		return true;
	}

	/**
	 * Called when there is a key down event.
	 *
	 * @param keycode	The value in event.getKeyCode().
	 * @param event	The key event.
	 * @return	True if the event was handled.
	 */
	@Override
	public boolean
	onKeyDown (
		int			keycode,
		KeyEvent	event
	) {
		if (_rootWindow == null)
			return false;

		boolean		sendEvent = false;

		switch (keycode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				return false;
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				updatePointerButtons (1, true);
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				updatePointerButtons (2, true);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				updatePointerButtons (3, true);
				break;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				updateModifiers (keycode, true, event.getMetaState ());
				sendEvent = true;
				break;
			default:
				sendEvent = true;
				break;
		}

		if (sendEvent)
			notifyKeyPressedReleased (keycode, true);

		return true;
	}

	/**
	 * Called when there is a key up event.
	 *
	 * @param keycode	The value in event.getKeyCode().
	 * @param event	The key event.
	 * @return	True if the event was handled.
	 */
	@Override
	public boolean
	onKeyUp (
		int			keycode,
		KeyEvent	event
	) {
		if (_rootWindow == null)
			return false;

		boolean		sendEvent = false;

		switch (keycode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				return false;
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				updatePointerButtons (1, false);
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				updatePointerButtons (2, false);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				updatePointerButtons (3, false);
				break;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				updateModifiers (keycode, false, event.getMetaState ());
				sendEvent = true;
				break;
			default:
				sendEvent = true;
				break;
		}

		if (sendEvent)
			notifyKeyPressedReleased (keycode, false);

		return true;
	}

	/**
	 * Write details of the screen.
	 *
	 * @param io	The input/output stream.
	 * @throws IOException
	 */
	public void
	write (
		InputOutput		io
	) throws IOException {
		Visual			vis = _xServer.getRootVisual ();

		io.writeInt (_rootWindow.getId ());		// Root window ID.
		io.writeInt (_defaultColormap.getId ());	// Default colormap ID.
		io.writeInt (_defaultColormap.getWhitePixel ());	// White pixel.
		io.writeInt (_defaultColormap.getBlackPixel ());	// Black pixel.
		io.writeInt (0);	// Current input masks.
		io.writeShort ((short) getWidth ());	// Width in pixels.
		io.writeShort ((short) getHeight ());	// Height in pixels.
		io.writeShort ((short) (getWidth ()
				/ _pixelsPerMillimeter));	// Width in millimeters.
		io.writeShort ((short) (getHeight ()
				/ _pixelsPerMillimeter));	// Height in millimeters.
		io.writeShort ((short) 1);	// Minimum installed maps.
		io.writeShort ((short) 1);	// Maximum installed maps.
		io.writeInt (vis.getId ());	// Root visual ID.
		io.writeByte (vis.getBackingStoreInfo ());
		io.writeByte ((byte) (vis.getSaveUnder () ? 1 : 0));
		io.writeByte ((byte) vis.getDepth ());	// Root depth.
		io.writeByte ((byte) 1);	// Number of allowed depths.

			// Write the only allowed depth.
		io.writeByte ((byte) vis.getDepth ());	// Depth.
		io.writeByte ((byte) 0);	// Unused.
		io.writeShort ((short) 1);	// Number of visuals with this depth.
		io.writePadBytes (4);	// Unused.
		vis.write (io);		// The visual at this depth.
	}

	/**
	 * Write the screen's installed colormaps.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @throws IOException
	 */
	public void
	writeInstalledColormaps (
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		int			n = _installedColormaps.size ();

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (n);	// Reply length.
			io.writeShort ((short) n);	// Number of colormaps.
			io.writePadBytes (22);	// Unused.

			for (Colormap cmap: _installedColormaps)
				io.writeInt (cmap.getId ());
		}
		io.flush ();
	}

	/**
	 * Process a screen-related request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param opcode	The request's opcode.
	 * @param arg	Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public void
	processRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		byte			opcode,
		int				arg,
		int				bytesRemaining
	) throws IOException {
		switch (opcode) {
			case RequestCode.SendEvent:
				if (bytesRemaining != 40) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processSendEventRequest (_xServer, io, sequenceNumber,
																arg == 1);
				}
				break;
			case RequestCode.GrabPointer:
				if (bytesRemaining != 20) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processGrabPointerRequest (_xServer, io, sequenceNumber,
																arg == 1);
				}
				break;
			case RequestCode.UngrabPointer:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			time = io.readInt ();	// Time.
					int			now = _xServer.getTimestamp ();

					if (time == 0)
						time = now;

					if (time >= _grabPointerTime && time <= now) {
						_grabPointerWindow = null;
						_grabCursor = null;
						_grabConfineWindow = null;
						updatePointer (2);
					}
				}
				break;
			case RequestCode.GrabButton:
				if (bytesRemaining != 20) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processGrabButtonRequest (_xServer, io, sequenceNumber,
																arg == 1);
				}
				break;
			case RequestCode.UngrabButton:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			wid = io.readInt ();	// Grab window.
					int			modifiers = io.readShort ();	// Modifiers.
					Resource	r = _xServer.getResource (wid);

					io.readSkip (2);	// Unused.

					if (r == null || r.getType () != Resource.WINDOW) {
						ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
																opcode, wid);
					} else {
						Window		w = (Window) r;

						// Not implemented.
					}
				}
				break;
			case RequestCode.ChangeActivePointerGrab:
				if (bytesRemaining != 12) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readSkip (bytesRemaining);	// Not implemented.
				}
				break;
			case RequestCode.GrabKeyboard:
				if (bytesRemaining != 20) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processGrabKeyboardRequest (_xServer, io, sequenceNumber,
																arg == 1);
				}
				break;
			case RequestCode.UngrabKeyboard:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readInt ();	// Time.

					_grabKeyboardWindow = null;
				}
				break;
			case RequestCode.GrabKey:
				if (bytesRemaining != 12) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processGrabKeyRequest (_xServer, io, sequenceNumber,
																arg == 1);
				}
				break;
			case RequestCode.UngrabKey:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			wid = io.readInt ();	// Grab window.
					int			modifiers = io.readShort ();	// Modifiers.
					Resource	r = _xServer.getResource (wid);

					io.readSkip (2);	// Unused.

					if (r == null || r.getType () != Resource.WINDOW) {
						ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
																opcode, wid);
					} else {
						Window		w = (Window) r;

						// Not implemented.
					}
				}
				break;
			case RequestCode.AllowEvents:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readInt ();	// Time.

						// Not implemented.
				}
				break;
		}
	}

	/**
	 * Process a SendEvent request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param propagate	Propagate flag.
	 * @throws IOException
	 */
	private void
	processSendEventRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		boolean			propagate
	) throws IOException {
		int				wid = io.readInt ();	// Destination window.
		int				mask = io.readInt ();	// Event mask.
		byte[]			event = new byte[32];
		Window			w;

		io.readBytes (event, 0, 32);	// Event.

		if (wid == 0) {		// Pointer window.
			w = _rootWindow.windowAtPoint (_motionX, _motionY);
		} else if (wid == 1) {	// Input focus.
				// Not finished - MKWAN
			w = _rootWindow.windowAtPoint (_motionX, _motionY);
		} else {
			Resource		r = _xServer.getResource (wid);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.SendEvent, wid);
				return;
			} else
				w = (Window) r;
		}

		ClientComms		client = null;

		if (mask == 0) {
			client = w._clientComms;
		} else if (!propagate) {
			if ((mask & w.getEventMask ()) != 0)
				client = w._clientComms;
		} else {
			for (;;) {
				if ((mask & w.getEventMask ()) != 0) {
					client = w._clientComms;
					break;
				}

				mask &= ~w.getDoNotPropagateMask ();
				if (mask == 0)
					break;

				w = w.getParent ();
				if (w == null)
					break;
			}
		}

		if (client == null)
			return;

		InputOutput		dio = client.getInputOutput ();

		synchronized (dio) {
			dio.writeByte ((byte) (event[0] | 128));

			if (event[0] == EventCode.KeymapNotify) {
				dio.writeBytes (event, 1, 31);
			} else {
				dio.writeByte (event[1]);
				dio.writeShort ((short) (client.getSequenceNumber() & 0xffff));
				dio.writeBytes (event, 4, 28);
			}
		}
		dio.flush ();
	}

	/**
	 * Process a GrabPointer request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param ownerEvents	Owner-events flag.
	 * @throws IOException
	 */
	private void
	processGrabPointerRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		boolean			ownerEvents
	) throws IOException {
		int				wid = io.readInt ();	// Grab window.
		int				mask = io.readShort ();	// Event mask.
		boolean			psync = (io.readByte () == 0);	// Pointer mode.
		boolean			ksync = (io.readByte () == 0);	// Keyboard mode.
		int				cwid = io.readInt ();	// Confine-to.
		int				cid = io.readInt ();	// Cursor.
		int				time = io.readInt ();	// Time.
		Resource		r = _xServer.getResource (wid);

		if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabPointer, wid);
			return;
		}

		Window			w = (Window) r;
		Cursor			c = null;
		Window			cw = null;

		if (cwid != 0) {
			r = _xServer.getResource (cwid);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabPointer, cwid);
				return;
			}
			cw = (Window) r;
		}

		if (cid != 0) {
			r = _xServer.getResource (cid);

			if (r != null && r.getType () == Resource.CURSOR) {
				ErrorCode.write (io, ErrorCode.Cursor, sequenceNumber,
										RequestCode.GrabPointer, cid);
				return;
			}
			c = (Cursor) r;
		}

		if (c == null)
			c = w.getCursor ();

		int			status = 0;	// Success.
		int			now = _xServer.getTimestamp ();

		if (time == 0)
			time = now;

		if (time < _grabPointerTime || time > now) {
			status = 2;	// Invalid time.
		} else if (_grabPointerWindow != null) {
			status = 1;	// Already grabbed.
		} else {
			_grabPointerWindow = w;
			_grabPointerTime = time;
			_grabCursor = c;
			_grabConfineWindow = cw;
			_grabEventMask = mask;
			_grabPointerOwnerEvents = ownerEvents;
			_grabPointerSynchronous = psync;
			_grabKeyboardSynchronous = ksync;
		}

		synchronized (io) {
			Util.writeReplyHeader (io, status, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writePadBytes (24);	// Unused.
		}
		io.flush ();

		if (status == 0)
			updatePointer (1);
	}

	/**
	 * Process a GrabButton request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param ownerEvents	Owner-events flag.
	 * @throws IOException
	 */
	private void
	processGrabButtonRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		boolean			ownerEvents
	) throws IOException {
		int				wid = io.readInt ();	// Grab window.
		int				mask = io.readShort ();	// Event mask.
		boolean			psync = (io.readByte () == 0);	// Pointer mode.
		boolean			ksync = (io.readByte () == 0);	// Keyboard mode.
		int				cwid = io.readInt ();	// Confine-to.
		int				cid = io.readInt ();	// Cursor.
		int				button = io.readByte ();	// Button.
		int				modifiers;
		Resource		r = _xServer.getResource (wid);

		io.readSkip (1);	// Unused.
		modifiers = io.readShort ();	// Modifiers.

		if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabPointer, wid);
			return;
		}

		Window			w = (Window) r;
		Cursor			c = null;
		Window			cw = null;

		if (cwid != 0) {
			r = _xServer.getResource (cwid);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabPointer, cwid);
				return;
			}
			cw = (Window) r;
		}

		if (cid != 0) {
			r = _xServer.getResource (cid);

			if (r != null && r.getType () == Resource.CURSOR) {
				ErrorCode.write (io, ErrorCode.Cursor, sequenceNumber,
										RequestCode.GrabPointer, cid);
				return;
			}
			c = (Cursor) r;
		}

		if (c == null)
			c = w.getCursor ();

			// Not implemented.
	}

	/**
	 * Process a GrabKeyboard request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param ownerEvents	Owner-events flag.
	 * @throws IOException
	 */
	private void
	processGrabKeyboardRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		boolean			ownerEvents
	) throws IOException {
		int				wid = io.readInt ();	// Grab window.
		int				time = io.readInt ();	// Time.
		boolean			psync = (io.readByte () == 0);	// Pointer mode.
		boolean			ksync = (io.readByte () == 0);	// Keyboard mode.
		Resource		r = _xServer.getResource (wid);

		io.readSkip (2);	// Unused.

		if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabKeyboard, wid);
			return;
		}

		Window		w = (Window) r;
		int			status = 0;	// Success.
		int			now = _xServer.getTimestamp ();

		if (time == 0)
			time = now;

		if (time < _grabKeyboardTime || time > now) {
			status = 2;	// Invalid time.
		} else if (_grabKeyboardWindow != null) {
			status = 1;	// Already grabbed.
		} else {
			_grabKeyboardWindow = w;
			_grabKeyboardTime = time;
			_grabKeyboardOwnerEvents = ownerEvents;
			_grabPointerSynchronous = psync;
			_grabKeyboardSynchronous = ksync;
		}

		synchronized (io) {
			Util.writeReplyHeader (io, status, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writePadBytes (24);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Process a GrabKey request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param ownerEvents	Owner-events flag.
	 * @throws IOException
	 */
	private void
	processGrabKeyRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		boolean			ownerEvents
	) throws IOException {
		int				wid = io.readInt ();	// Grab window.
		int				modifiers = io.readShort ();	// Modifiers.
		int				keycode = io.readByte ();	// Key.
		boolean			psync = (io.readByte () == 0);	// Pointer mode.
		boolean			ksync = (io.readByte () == 0);	// Keyboard mode.
		Resource		r = _xServer.getResource (wid);

		io.readSkip (3);	// Unused.

		if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.GrabPointer, wid);
			return;
		}

		Window			w = (Window) r;

			// Not implemented.
	}
}