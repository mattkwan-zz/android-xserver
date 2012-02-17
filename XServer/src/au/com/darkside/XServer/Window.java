/**
 * This class implements an X window.
 */
package au.com.darkside.XServer;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;


/**
 * @author Matthew Kwan
 * 
 * This class implements an X window.
 */
public class Window extends Resource {
	private final ScreenView		_screen;
	private Window					_parent;
	private Rect					_orect;
	private Rect					_irect;
	private Drawable				_drawable;
	private Colormap				_colormap;
	private Cursor					_cursor = null;
	private int[]					_attributes;
	private int						_borderWidth;
	private final boolean			_inputOnly;
	private boolean					_overrideRedirect;
	private final Vector<Window>	_children;
	private final Hashtable<Integer, Property>	_properties;
	private boolean					_isMapped = false;
	private boolean					_exposed = false;

	private static final int	AttrBackgroundPixmap = 0;
	private static final int	AttrBackgroundPixel = 1;
	private static final int	AttrBorderPixmap = 2;
	private static final int	AttrBorderPixel = 3;
	private static final int	AttrBitGravity = 4;
	private static final int	AttrWinGravity = 5;
	private static final int	AttrBackingStore = 6;
	private static final int	AttrBackingPlanes = 7;
	private static final int	AttrBackingPixel = 8;
	private static final int	AttrOverrideRedirect = 9;
	private static final int	AttrSaveUnder = 10;
	private static final int	AttrEventMask = 11;
	private static final int	AttrDoNotPropagateMask = 12;
	private static final int	AttrColormap = 13;
	private static final int	AttrCursor = 14;

	/**
	 * Constructor.
	 *
	 * @param id		The window's ID.
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param screen	The window's screen.
	 * @param parent	The window's parent.
	 * @param x	X position relative to parent.
	 * @param y	Y position relative to parent.
	 * @param width	Width of the window.
	 * @param height	Height of the window.
	 * @param borderWidth	Width of the window's border.
	 * @param inputOnly	Is this an InputOnly window?
	 */
	public Window (
		int				id,
		XServer			xServer,
		ClientComms		client,
		ScreenView		screen,
		Window			parent,
		int				x,
		int				y,
		int				width,
		int				height,
		int				borderWidth,
		boolean			inputOnly
	) {
		super (WINDOW, id, xServer, client);

		_screen = screen;
		_parent = parent;
		_borderWidth = borderWidth;
		_colormap = _screen.getDefaultColormap ();
		_inputOnly = inputOnly;

		if (_parent == null) {
			_orect = new Rect (0, 0, width, height);
			_irect = new Rect (0, 0, width, height);
		} else {
			final int	left = _parent._orect.left + _parent._borderWidth + x;
			final int	top = _parent._orect.top + _parent._borderWidth + y;
			final int	border = 2 * borderWidth;
	
			_orect = new Rect (left, top, left + width + border,
													top + height + border);
			if (_borderWidth == 0)
				_irect = new Rect (_orect);
			else
				_irect = new Rect (left + borderWidth, top + borderWidth,
					_orect.right - borderWidth, _orect.bottom - borderWidth);
		}

		_attributes = new int[] {
			0,	// background-pixmap = None
			0,	// background-pixel = zero
			0,	// border-pixmap = CopyFromParent
			0,	// border-pixel = zero
			0,	// bit-gravity = Forget
			1,	// win-gravity = NorthWest
			0,	// backing-store = NotUseful
			0xffffffff,	// backing-planes = all ones
			0,	// backing-pixel = zero
			0,	// override-redirect = False
			0,	// save-under = False
			0,	// event-mask = empty set
			0,	// do-not-propogate-mask = empty set
			0,	// colormap = CopyFromParent
			0	// cursor = None
		};

		if (_parent == null) {	// Make the root window grey and mapped.
			_attributes[AttrBackgroundPixel] = 0xff808080;
			_isMapped = true;
			_cursor = (Cursor) _xServer.getResource (2);	// X cursor.
		}

		_drawable = new Drawable (width, height, 32,
										_attributes[AttrBackgroundPixel]);
		_children = new Vector<Window> ();
		_properties = new Hashtable<Integer, Property> ();
	}

	/**
	 * Return the window's parent.
	 *
	 * @return	The window's parent.
	 */
	public Window
	getParent () {
		return _parent;
	}

	/**
	 * Return the window's screen.
	 *
	 * @return	The window's screen.
	 */
	public ScreenView
	getScreen () {
		return _screen;
	}

	/**
	 * Return the window's drawable.
	 *
	 * @return	The window's drawable.
	 */
	public Drawable
	getDrawable () {
		return _drawable;
	}

	/**
	 * Return the window's cursor.
	 *
	 * @return	The window's cursor.
	 */
	public Cursor
	getCursor () {
		if (_cursor == null)
			return _parent.getCursor ();
		else
			return _cursor;
	}

	/**
	 * Return the window's inner rectangle.
	 *
	 * @return	The window's inner rectangle.
	 */
	public Rect
	getIRect () {
		return _irect;
	}

	/**
	 * Return the window's outer rectangle.
	 *
	 * @return	The window's outer rectangle.
	 */
	public Rect
	getORect () {
		return _orect;
	}

	/**
	 * Return the window's event mask.
	 *
	 * @return	The window's event mask.
	 */
	public int
	getEventMask () {
		return _attributes[AttrEventMask];
	}

	/**
	 * Return the window's do-not-propagate mask.
	 *
	 * @return	The window's do-not-propagate mask.
	 */
	public int
	getDoNotPropagateMask () {
		return _attributes[AttrDoNotPropagateMask];
	}

	/**
	 * Draw the window and its mapped children.
	 * 
	 * @param canvas	The canvas to draw to.
	 * @param paint		A paint to draw with.
	 * @param bounds	The region to draw to.
	 */
	public void
	draw (
		Canvas		canvas,
		Paint		paint,
		Rect		bounds
	) {
		if (!_isMapped || _inputOnly)
			return;

		if (_borderWidth != 0) {
			if (bounds != null && !Rect.intersects (_orect, bounds))
				return;

			float		hbw = 0.5f * _borderWidth;

			paint.setColor (_attributes[AttrBorderPixel]);
			paint.setStrokeWidth (_borderWidth);
			paint.setStyle (Paint.Style.STROKE);
			canvas.drawRect (_orect.left + hbw, _orect.top + hbw,
							_orect.right - hbw, _orect.bottom - hbw, paint);
		}

		if (bounds != null && !Rect.intersects (_irect, bounds))
			return;

		canvas.drawBitmap (_drawable.getBitmap (), _irect.left, _irect.top,
																	null);

		for (Window w: _children)
			w.draw (canvas, paint, bounds);
	}

	/**
	 * Return the visible window that contains the specified point.
	 *
	 * @param x	X coordinate of the point.
	 * @param y	Y coordinate of the point.
	 * @return	The visible window containing the point.
	 */
	public Window
	windowAtPoint (
		int			x,
		int			y
	) {
		for (int i = _children.size () - 1; i >= 0; i--) {
			Window		w = _children.elementAt (i);

			if (w._isMapped && w._irect.contains (x, y))
				return w.windowAtPoint (x, y);
		}

		return this;
	}

	/**
	 * Process a CreateWindow.
	 * Create a window with the specified ID, with this window as parent.
	 *
	 * @param io	The input/output stream.
	 * @param client	The client issuing the request.
	 * @param sequenceNumber	The request sequence number.
	 * @param id	The ID of the window to create.
	 * @param depth		The window depth.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the window was created successfully.
	 * @throws IOException
	 */
	public boolean
	processCreateWindowRequest (
		InputOutput		io,
		ClientComms		client,
		int				sequenceNumber,
		int				id,
		int				depth,
		int				bytesRemaining
	) throws IOException {
		int				x = (short) io.readShort ();	// X position.
		int				y = (short) io.readShort ();	// Y position.
		int				width = io.readShort ();	// Window width.
		int				height = io.readShort ();	// Window height.
		int				borderWidth = io.readShort ();	// Border width.
		int				wclass = io.readShort ();	// Window class.
		Window			w;
		boolean			inputOnly;

		io.readInt ();	// Visual.

		if (wclass == 0)	// Copy from parent.
			inputOnly = _inputOnly;
		else if (wclass == 1)	// Input/output.
			inputOnly = false;
		else
			inputOnly = true;

		w = new Window (id, _xServer, client, _screen, this, x, y,
									width, height, borderWidth, inputOnly);
		bytesRemaining -= 16;
		if (!w.processWindowAttributes (io, sequenceNumber,
								RequestCode.CreateWindow, bytesRemaining))
			return false;

		_xServer.addResource (w);
		client.addResource (w);
		_children.add (w);
		w.invalidate ();

		if (isSelecting (EventCode.MaskSubstructureNotify))
			EventCode.sendCreateNotify (_clientComms, this, w, x, y, width,
									height, borderWidth, _overrideRedirect);

		return true;
	}

	/**
	 * Request a redraw of the window.
	 */
	public void
	invalidate () {
		_screen.postInvalidate (_orect.left, _orect.top, _orect.right,
															_orect.bottom);
	}

	/**
	 * Request a redraw of a region of the window.
	 * 
	 * @param x	X coordinate of the region.
	 * @param y	Y coordinate of the region.
	 * @param width	Width of the region.
	 * @param height	Height of the region.
	 */
	public void
	invalidate (
		int		x,
		int		y,
		int		width,
		int		height
	) {
		_screen.postInvalidate (_irect.left + x, _irect.top + y,
						_irect.left + x + width, _irect.top + y + height);
	}

	/**
	 * Delete this window from its parent.
	 * Used when a client disconnects.
	 */
	@Override
	public void
	delete () {
		super.delete ();

		if (_parent != null)
			_parent._children.remove (this);

		if (_isMapped) {
			_isMapped = false;
			invalidate ();
		}

		_screen.deleteWindow (this);
	}

	/**
	 * Process a list of window attributes.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The opcode being processed.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the window is successfully created.
	 * @throws IOException
	 */
	private boolean
	processWindowAttributes (
		InputOutput		io,
		int				sequenceNumber,
		byte			opcode,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber, opcode, 0);
			return false;
		}

		int			valueMask = io.readInt ();	// Value mask.
		int			n = Util.bitcount (valueMask);

		bytesRemaining -= 4;
		if (bytesRemaining != n * 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber, opcode, 0);
			return false;
		}

		for (int i = 0; i < 15; i++)
			if ((valueMask & (1 << i)) != 0)
				processValue (io, i);

		if (opcode == RequestCode.CreateWindow)	// Apply all values on create.
			valueMask = 0xffffffff;

		return applyValues (io, opcode, sequenceNumber, valueMask);
	}

	/**
	 * Process a single window attribute value.
	 *
	 * @param io	The input/output stream.
	 * @param maskBit	The mask bit of the attribute.
	 * @throws IOException
	 */
	private void
	processValue (
		InputOutput		io,
		int				maskBit
	) throws IOException {
		switch (maskBit) {
			case AttrBackgroundPixmap:
			case AttrBackgroundPixel:
			case AttrBorderPixmap:
			case AttrBorderPixel:
			case AttrBackingPlanes:
			case AttrBackingPixel:
			case AttrEventMask:
			case AttrDoNotPropagateMask:
			case AttrColormap:
			case AttrCursor:
				_attributes[maskBit] = io.readInt ();
				break;
			case AttrBitGravity:
			case AttrWinGravity:
			case AttrBackingStore:
			case AttrOverrideRedirect:
			case AttrSaveUnder:
				_attributes[maskBit] = io.readByte ();
				io.readSkip (3);
				break;
		}
	}

	/**
	 * Is the client selecting the specified event?
	 *
	 * @param mask	The event.
	 * @return	True if the client is selection the event.
	 */
	public boolean
	isSelecting (
		int			mask
	) {
		return (_attributes[AttrEventMask] & mask) != 0;
	}

	/**
	 * Apply the attribute values to the window.
	 *
	 * @param io	The input/output stream.
	 * @param opcode	The opcode being processed.
	 * @param sequenceNumber	The request sequence number.
	 * @param mask	Bit mask of the attributes that have changed.
	 * @return	True if the values are all valid.
	 * @throws IOException
	 */
	private boolean
	applyValues (
		InputOutput		io,
		byte			opcode,
		int				sequenceNumber,
		int				mask
	) throws IOException {
		boolean			ok = true;

		if ((mask & (1 << AttrBackgroundPixel)) != 0) {
			if ((_attributes[AttrBackgroundPixel] & 0xff000000) == 0)
				_attributes[AttrBackgroundPixel] |= 0xff000000;

			_drawable.getBitmap().eraseColor (
										_attributes[AttrBackgroundPixel]);
		}

		if ((mask & (1 << AttrBorderPixel)) != 0) {
			if ((_attributes[AttrBorderPixel] & 0xff000000) == 0)
				_attributes[AttrBorderPixel] |= 0xff000000;
		}

		if ((mask & (1 << AttrColormap)) != 0) {
			int			cid = _attributes[AttrColormap];

			if (cid != 0) {
				Resource	r = _xServer.getResource (cid);
	
				if (r != null && r.getType () == Resource.COLORMAP) {
					_colormap = (Colormap) r;
				} else {
					ErrorCode.write (io, ErrorCode.Colormap, sequenceNumber,
																opcode, cid);
					ok = false;
				}
			} else if (_parent != null) {
				_colormap = _parent._colormap;
			}
		}

		if ((mask & (1 << AttrOverrideRedirect)) != 0)
			_overrideRedirect = (_attributes[AttrOverrideRedirect] == 1);

		if ((mask & (1 << AttrCursor)) != 0) {
			int			cid = _attributes[AttrCursor];

			if (cid != 0) {
				Resource	r = _xServer.getResource (cid);
	
				if (r != null && r.getType () == Resource.CURSOR) {
					_cursor = (Cursor) r;
				} else {
					ErrorCode.write (io, ErrorCode.Cursor, sequenceNumber,
																opcode, cid);
					ok = false;
				}
			} else {
				_cursor = null;
			}
		}
	
		return ok;
	}

	/**
	 * Notify that the pointer has entered this window.
	 *
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param detail	0=Ancestor, 1=Virtual, 2=Inferior, 3=Nonlinear, 4=NonlinearVirtual.
	 * @param toWindow	Window containing pointer.
	 * @param mode	0=Normal, 1=Grab, 2=Ungrab
	 */
	private void
	enterNotify (
		int			x,
		int			y,
		int			detail,
		Window		toWindow,
		int			mode
	) {
		if (!isSelecting (EventCode.MaskEnterWindow) || !_isMapped)
			return;

		Window		child = (toWindow._parent == this) ? toWindow : null;
		boolean		focus = true;	// MKWAN

		try {
			EventCode.sendEnterNotify (_clientComms, _xServer.getTimestamp (),
						detail, _screen.getRootWindow (), this, child, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons (), mode, focus);

			if (isSelecting (EventCode.MaskKeymapState)) {
				Keyboard	kb = _xServer.getKeyboard ();

				EventCode.sendKeymapNotify (_clientComms, kb.getKeymap ());
			}
		} catch (IOException e) {
		}
	}

	/**
	 * Notify that the pointer has left this window.
	 *
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param detail	0=Ancestor, 1=Virtual, 2=Inferior, 3=Nonlinear, 4=NonlinearVirtual.
	 * @param fromWindow	Window previously containing pointer.
	 * @param mode	0=Normal, 1=Grab, 2=Ungrab
	 */
	private void
	leaveNotify (
		int			x,
		int			y,
		int			detail,
		Window		fromWindow,
		int			mode
	) {
		if (!isSelecting (EventCode.MaskLeaveWindow) || !_isMapped)
			return;

		Window		child = (fromWindow._parent == this) ? fromWindow : null;
		boolean		focus = false;	// MKWAN

		try {
			EventCode.sendLeaveNotify (_clientComms, _xServer.getTimestamp (),
						detail, _screen.getRootWindow (), this, child, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons (), mode, focus);
		} catch (IOException e) {
		}
	}

	/**
	 * Called when the pointer leaves this window and enters another.
	 *
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param ew	The window being entered.
	 * @param mode	0=Normal, 1=Grab, 2=Ungrab
	 */
	public void
	leaveEnterNotify (
		int			x,
		int			y,
		Window		ew,
		int			mode
	) {
		if (ew.isInferior (this)) {
			leaveNotify (x, y, 0, this, mode);

			for (Window w = _parent; w != ew; w = w._parent)
				w.leaveNotify (x, y, 1, this, 0);

			ew.enterNotify (x, y, 2, ew, mode);
		} else if (isInferior (ew)) {
			leaveNotify (x, y, 2, this, mode);

			Stack<Window>	stack = new Stack<Window>();

			for (Window w = ew._parent; w != this; w = w._parent)
				stack.push (w);

			while (!stack.empty ()) {
				Window		w = stack.pop ();

				w.enterNotify (x, y, 1, ew, mode);
			}

			ew.enterNotify (x, y, 0, ew, mode);
		} else {
			leaveNotify (x, y, 3, this, 0);

			Window			lca = null;
			Stack<Window>	stack = new Stack<Window>();

			for (Window w = _parent; w != ew; w = w._parent) {
				if (w.isInferior (ew)) {
					lca = w;
					break;
				} else {
					w.leaveNotify (x, y, 4, this, mode);
				}
			}

			for (Window w = ew._parent; w != lca; w = w._parent)
				stack.push (w);

			while (!stack.empty ()) {
				Window		w = stack.pop ();

				w.enterNotify (x, y, 4, ew, mode);
			}

			ew.enterNotify (x, y, 3, ew, mode);
		}
	}

	/**
	 * Called when a button is pressed or released in this window.
	 *
	 * @param pressed	Whether the button was pressed or released.
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param button	Button that was pressed/released.
	 * @param client	Only send notification to this client. Can be null.
	 *
	 * @return	True if an event is sent.
	 */
	public boolean
	buttonNotify (
		boolean		pressed,
		int			x,
		int			y,
		int			button,
		ClientComms	client
	) {
		Window		evw = this;
		Window		child = null;
		int			mask = pressed ? EventCode.MaskButtonPress
										: EventCode.MaskButtonRelease;

		for (;;) {
			if (evw._isMapped && evw.isSelecting (mask))
				break;

			if (evw._parent == null)
				return false;

			if ((evw._attributes[AttrDoNotPropagateMask] & mask) != 0)
				return false;

			child = evw;
			evw = evw._parent;
		}

		if (client != null && client != evw._clientComms)
			return false;

		try {
			if (pressed)
				EventCode.sendButtonPress (evw._clientComms,
						_xServer.getTimestamp (), button,
						_screen.getRootWindow (), evw, child, x, y,
						x - evw._irect.left, y - evw._irect.top,
						_screen.getButtons ());
			else
				EventCode.sendButtonRelease (evw._clientComms,
						_xServer.getTimestamp (), button,
						_screen.getRootWindow (), evw, child, x, y,
						x - evw._irect.left, y - evw._irect.top,
						_screen.getButtons ());
		} catch (IOException e) {
		}

		return true;
	}

	/**
	 * Called when a button is pressed or released while grabbed by this
	 * window.
	 *
	 * @param pressed	Whether the button was pressed or released.
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param button	Button that was pressed/released.
	 * @param eventMask	The events the window is interested in.
	 * @param ownerEvents	Owner-events flag.
	 */
	public void
	grabButtonNotify (
		boolean		pressed,
		int			x,
		int			y,
		int			button,
		int			eventMask,
		boolean		ownerEvents
	) {
		if (ownerEvents) {
			Window		w = _screen.getRootWindow().windowAtPoint (x, y);

			if (w.buttonNotify (pressed, x, y, button, _clientComms))
				return;
		}

		int			mask = pressed ? EventCode.MaskButtonPress
										: EventCode.MaskButtonRelease;

		if ((eventMask & mask) == 0)
			return;

		try {
			if (pressed)
				EventCode.sendButtonPress (_clientComms,
						_xServer.getTimestamp (), button,
						_screen.getRootWindow (), this, null, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons ());
			else
				EventCode.sendButtonRelease (_clientComms,
						_xServer.getTimestamp (), button,
						_screen.getRootWindow (), this, null, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons ());
		} catch (IOException e) {
		}
	}

	/**
	 * Called when a key is pressed or released in this window.
	 *
	 * @param pressed	Whether the key was pressed or released.
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param keycode	Keycode of the key.
	 * @param client	Only send notification to this client. Can be null.
	 *
	 * @return	True if an event is sent.
	 */
	public boolean
	keyNotify (
		boolean		pressed,
		int			x,
		int			y,
		int			keycode,
		ClientComms	client
	) {
		Window		evw = this;
		Window		child = null;
		int			mask = pressed ? EventCode.MaskKeyPress
												: EventCode.MaskKeyRelease;

		for (;;) {
			if (evw._isMapped && evw.isSelecting (mask))
				break;

			if (evw._parent == null)
				return false;

			if ((evw._attributes[AttrDoNotPropagateMask] & mask) != 0)
				return false;

			child = evw;
			evw = evw._parent;
		}

		if (client != null && client != evw._clientComms)
			return false;

		try {
			if (pressed)
				EventCode.sendKeyPress (evw._clientComms,
						_xServer.getTimestamp (), keycode,
						_screen.getRootWindow (), evw, child, x, y,
						x - evw._irect.left, y - evw._irect.top,
						_screen.getButtons ());
			else
				EventCode.sendKeyRelease (evw._clientComms,
						_xServer.getTimestamp (), keycode,
						_screen.getRootWindow (), evw, child, x, y,
						x - evw._irect.left, y - evw._irect.top,
						_screen.getButtons ());
		} catch (IOException e) {
		}

		return true;
	}

	/**
	 * Called when a key is pressed or released while grabbed by this window.
	 *
	 * @param pressed	Whether the key was pressed or released.
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param keycode	Keycode of the key.
	 * @param ownerEvents	Owner-events flag.
	 */
	public void
	grabKeyNotify (
		boolean		pressed,
		int			x,
		int			y,
		int			keycode,
		boolean		ownerEvents
	) {
		if (ownerEvents) {
			Window		w = _screen.getRootWindow().windowAtPoint (x, y);

			if (w.keyNotify (pressed, x, y, keycode, _clientComms))
				return;
		}

		try {
			if (pressed)
				EventCode.sendKeyPress (_clientComms,
						_xServer.getTimestamp (), keycode,
						_screen.getRootWindow (), this, null, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons ());
			else
				EventCode.sendKeyRelease (_clientComms,
						_xServer.getTimestamp (), keycode,
						_screen.getRootWindow (), this, null, x, y,
						x - _irect.left, y - _irect.top,
						_screen.getButtons ());
		} catch (IOException e) {
		}
	}

	/**
	 * Check if the window is interested in the motion event.
	 *
	 * @param buttonMask	Currently pressed pointer buttons.
	 *
	 * @return	True if the window is interested in the event.
	 */
	private boolean
	interestedInMotionEvent (
		int			buttonMask
	) {
		if (!_isMapped)
			return false;

		if (isSelecting (EventCode.MaskPointerMotion)) {
			return true;
		} else if (isSelecting (EventCode.MaskPointerMotionHint)) {
			return true;
		} else {
			if ((buttonMask & 0x700) == 0)
				return false;

			if (isSelecting (EventCode.MaskButtonMotion))
				return true;
			else if ((buttonMask & 0x100) != 0
								&& isSelecting (EventCode.MaskButton1Motion))
				return true;
			else if ((buttonMask & 0x200) != 0
								&& isSelecting (EventCode.MaskButton2Motion))
				return true;
			else if ((buttonMask & 0x400) != 0
								&& isSelecting (EventCode.MaskButton3Motion))
				return true;
		}

		return false;
	}

	/**
	 * Called when the pointer moves within this window.
	 *
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param buttonMask	Currently pressed pointer buttons.
	 * @param client	Only send notification to this client. Can be null.
	 *
	 * @return	True if an event is sent.
	 */
	public boolean
	motionNotify (
		int			x,
		int			y,
		int			buttonMask,
		ClientComms	client
	) {
		Window		evw = this;
		Window		child = null;

		for (;;) {
			if (evw.interestedInMotionEvent (buttonMask))
				break;

			if (evw._parent == null)
				return false;

			if ((evw._attributes[AttrDoNotPropagateMask]
										& EventCode.MaskPointerMotion) != 0)
				return false;

			child = evw;
			evw = evw._parent;
		}

		if (client != null && client != evw._clientComms)
			return false;

		int			detail = 0;	// Normal.

		if (evw.isSelecting (EventCode.MaskPointerMotionHint)
						&& !evw.isSelecting (EventCode.MaskPointerMotion))
			detail = 1;		// Hint.

		try {
			EventCode.sendMotionNotify (evw._clientComms,
						_xServer.getTimestamp (), detail,
						_screen.getRootWindow (), evw, child, x, y,
						x - evw._irect.left, y - evw._irect.top, buttonMask);
		} catch (IOException e) {
		}

		return true;
	}

	/**
	 * Called when the pointer moves while grabbed by this window.
	 *
	 * @param x	Pointer X coordinate.
	 * @param y	Pointer Y coordinate.
	 * @param buttonMask	Currently pressed pointer buttons.
	 * @param eventMask	The events the window is interested in.
	 * @param ownerEvents	Owner-events flag.
	 */
	public void
	grabMotionNotify (
		int			x,
		int			y,
		int			buttonMask,
		int			eventMask,
		boolean		ownerEvents
	) {
		if (ownerEvents) {
			Window		w = _screen.getRootWindow().windowAtPoint (x, y);

			if (w.motionNotify (x, y, buttonMask, _clientComms))
				return;
		}

		int			tmpMask = _attributes[AttrEventMask];

		_attributes[AttrEventMask] = eventMask;
		if (interestedInMotionEvent (buttonMask)) {
			int			detail = 0;	// Normal.

			if (isSelecting (EventCode.MaskPointerMotionHint)
							&& !isSelecting (EventCode.MaskPointerMotion))
				detail = 1;		// Hint.

			try {
				EventCode.sendMotionNotify (_clientComms,
								_xServer.getTimestamp (), detail,
								_screen.getRootWindow (), this, null, x, y,
								x - _irect.left, y - _irect.top, buttonMask);
			} catch (IOException e) {
			}
		}
		_attributes[AttrEventMask] = tmpMask;
	}

	/**
	 * Is the window an inferior of this window?
	 *
	 * @param w	The window being tested.
	 *
	 * @return	True if the window is a inferior of this window.
	 */
	private boolean
	isInferior (
		Window		w
	) {
		for (;;) {
			if (w._parent == this)
				return true;
			else if (w._parent == null)
				return false;
			else
				w = w._parent;
		}
	}

	/**
	 * Map/unmap the window.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param enable	If true, map the window. Otherwise unmap.
	 * @throws IOException
	 */
	private void
	map (
		InputOutput		io,
		int				sequenceNumber,
		boolean			enable
	) throws IOException {
		if (_isMapped == enable)
			return;

		if (enable && !_overrideRedirect && _parent != null
				&& _parent.isSelecting (EventCode.MaskSubstructureRedirect)) {
			EventCode.sendMapRequest(_parent._clientComms, _parent, this);
			return;
		}

		_isMapped = enable;

		if (enable) {
			if (isSelecting (EventCode.MaskStructureNotify))
				EventCode.sendMapNotify (_clientComms, this, this,
														_overrideRedirect);
			if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
				EventCode.sendMapNotify (_parent._clientComms, _parent,
													this, _overrideRedirect);
			if (!_exposed) {
				if (isSelecting (EventCode.MaskExposure))
					EventCode.sendExpose (_clientComms, this, 0, 0,
							_drawable.getWidth (), _drawable.getHeight (), 0);
				_exposed = true;
			}
		} else {
			if (isSelecting (EventCode.MaskStructureNotify))
				EventCode.sendUnmapNotify (_clientComms, this, this, false);
			if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
				EventCode.sendUnmapNotify (_parent._clientComms, _parent,
																this, false);
		}
	}

	/**
	 * Map/unmap the children of this window.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param enable	If true, map the window. Otherwise unmap.
	 * @throws IOException
	 */
	private void
	mapSubwindows (
		InputOutput		io,
		int				sequenceNumber,
		boolean			enable
	) throws IOException {
		for (Window w: _children) {
			w.map (io, sequenceNumber, enable);
			w.mapSubwindows (io, sequenceNumber, enable);
		}
	}

	/**
	 * Destroy the window and all its children.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param removeFromParent	If true, remove it from its parent.
	 * @throws IOException
	 */
	private void
	destroy (
		InputOutput		io,
		int				sequenceNumber,
		boolean			removeFromParent
	) throws IOException {
		if (_parent == null)	// No effect on root window.
			return;

		_xServer.freeResource (_id);
		if (_isMapped)
			map (io, sequenceNumber, false);

		for (Window w: _children)
			w.destroy (io, sequenceNumber, false);

		_children.clear ();

		if (removeFromParent)
			_parent._children.remove (this);

		if (isSelecting (EventCode.MaskStructureNotify))
			EventCode.sendDestroyNotify (_clientComms, this, this);
		if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
			EventCode.sendDestroyNotify (_parent._clientComms, _parent, this);
	}

	/**
	 * Change the window's parent.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param parent	New parent.
	 * @param x	New X position relative to new parent.
	 * @param y	New Y position relative to new parent.
	 * @throws IOException
	 */
	private void
	reparent (
		InputOutput		io,
		int				sequenceNumber,
		Window			parent,
		int				x,
		int				y
	) throws IOException {
		boolean		mapped = _isMapped;

		if (mapped)
			map (io, sequenceNumber, false);

		Rect		orig = new Rect (_orect);
		int			dx = parent._irect.left + x - _orect.left;
		int			dy = parent._irect.top + y - _orect.top;

		_orect.left += dx;
		_orect.top += dy;
		_orect.right += dx;
		_orect.bottom += dy;
		_irect.left += dx;
		_irect.top += dy;
		_irect.right += dx;
		_irect.bottom += dy;

		_parent._children.remove (this);
		parent._children.add (this);

		if (isSelecting (EventCode.MaskStructureNotify))
			EventCode.sendReparentNotify (_clientComms, this, this, parent,
													x, y, _overrideRedirect);

		if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
			EventCode.sendReparentNotify (_parent._clientComms, _parent, this,
											parent, x, y, _overrideRedirect);

		if (parent.isSelecting (EventCode.MaskSubstructureNotify))
			EventCode.sendReparentNotify (parent._clientComms, parent, this,
											parent, x, y, _overrideRedirect);

		_parent = parent;
		if (mapped && !_inputOnly) {
			map (io, sequenceNumber, true);
			_screen.postInvalidate (orig.left, orig.top, orig.right,
															orig.bottom);
		}
	}

	/**
	 * Circulate occluded windows.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param direction	0=RaiseLowest, 1=LowerHighest.
	 * @return	True if a window is restacked.
	 * @throws IOException
	 */
	private boolean
	circulate (
		InputOutput		io,
		int				sequenceNumber,
		int				direction
	) throws IOException {
		boolean			changed = false;

		if (direction == 0) {	// Raise lowest occluded.
			Window		lowest = null;

			for (Window w: _children) {
				if (occludes (null, w)) {
					lowest = w;
					break;
				}
			}

			if (lowest != null) {
				if (isSelecting (EventCode.MaskSubstructureRedirect)) {
					EventCode.sendCirculateRequest (_clientComms, this,
														lowest, direction);
				} else {
					_children.remove (lowest);
					_children.add (lowest);
					changed = true;
				}
			}
		} else {	// Lower highest occluding.
			Window		highest = null;

			for (int i = _children.size () - 1; i >= 0; i--) {
				Window		w = _children.elementAt (i);

				if (occludes (w, null)) {
					highest = w;
					break;
				}
			}

			if (highest != null) {
				if (isSelecting (EventCode.MaskSubstructureRedirect)) {
					EventCode.sendCirculateRequest (_clientComms, this,
														highest, direction);
				} else {
					_children.remove (highest);
					_children.add (0, highest);
					changed = true;
				}
			}
		}

		if (changed) {
			if (isSelecting (EventCode.MaskStructureNotify))
				EventCode.sendCirculateNotify (_clientComms, this, this,
																direction);

			if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
				EventCode.sendCirculateNotify (_parent._clientComms, _parent,
															this, direction);

			return true;
		}

		return false;
	}

	/**
	 * Does the first window occlude the second?
	 * If the first window is null, does any window occlude w2?
	 * If the second window is null, is any window occluded by w1?
	 *
	 * @param w1	First window.
	 * @param w2	Second window.
	 * @return	True if occlusion occurs.
	 */
	private boolean
	occludes (
		Window		w1,
		Window		w2
	) {
		if (w1 == null) {
			if (w2 == null || !w2._isMapped)
				return false;

				// Does anything occlude w2?
			Rect		r = w2._orect;
			boolean		above = false;

			for (Window w: _children) {
				if (above) {
					if (w._isMapped && Rect.intersects (w._orect, r))
						return true;
				} else {
					if (w == w2)
						above = true;
				}
			}
		} else {
			if (w2 == null) {	// Does w1 occlude anything?
				if (!w1._isMapped)
					return false;

				Rect		r = w1._orect;

				for (Window w: _children) {
					if (w == w1)
						return false;
					else if (w._isMapped && Rect.intersects (w._orect, r))
						return true;
				}
			} else {	// Does w1 occlude w2?
				if (!w1._isMapped || !w2._isMapped)
					return false;
				if (!Rect.intersects (w1._orect, w2._orect))
					return false;

				return _children.indexOf (w1) > _children.indexOf (w2);
			}
		}

		return false;
	}

	/**
	 * Move the window and its children.
	 *
	 * @param dx	X distance to move.
	 * @param dy	Y distance to move.
	 */
	private void
	move (
		int		dx,
		int		dy
	) {
		_irect.left += dx;
		_irect.right += dx;
		_irect.top += dy;
		_irect.bottom += dy;
		_orect.left += dx;
		_orect.right += dx;
		_orect.top += dy;
		_orect.bottom += dy;

		for (Window w: _children)
			w.move (dx, dy);
	}

	/**
	 * Process a ConfigureWindow request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The opcode being processed.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the window needs to be redrawn.
	 * @throws IOException
	 */
	private boolean
	processConfigureWindow (
		InputOutput		io,
		int				sequenceNumber,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.ConfigureWindow, 0);
			return false;
		}

		int			mask = io.readShort ();	// Value mask.
		int			n = Util.bitcount (mask);

		io.readSkip (2);	// Unused.
		bytesRemaining -= 4;
		if (bytesRemaining != 4 * n) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.ConfigureWindow, 0);
			return false;
		} else if (_parent == null) {	// No effect on root window.
			io.readSkip (bytesRemaining);
			return false;
		}

		int			oldWidth = _irect.right - _irect.left;
		int			oldHeight = _irect.bottom - _irect.top;
		int			oldX = _orect.left - _parent._irect.left;
		int			oldY = _orect.top - _parent._irect.top;
		int			width = oldWidth;
		int			height = oldHeight;
		int			x = oldX;
		int			y = oldY;
		int			borderWidth = _borderWidth;
		int			stackMode = 0;
		boolean		changed = false;
		Window		sibling = null;
		Rect		dirty = null;

		if ((mask & 0x01) != 0) {
			x = (short) io.readShort ();	// X.
			io.readSkip (2);	// Unused.
		}
		if ((mask & 0x02) != 0) {
			y = (short) io.readShort ();	// Y.
			io.readSkip (2);	// Unused.
		}
		if ((mask & 0x04) != 0) {
			width = (short) io.readShort ();	// Width.
			io.readSkip (2);	// Unused.
		}
		if ((mask & 0x08) != 0) {
			height = (short) io.readShort ();	// Height.
			io.readSkip (2);	// Unused.
		}
		if ((mask & 0x10) != 0) {
			borderWidth = (short) io.readShort ();	// Border width.
			io.readSkip (2);	// Unused.
		}
		if ((mask & 0x20) != 0) {
			int			id = io.readInt ();	// Sibling.
			Resource	r = _xServer.getResource (id);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.ConfigureWindow, id);
				io.readSkip (bytesRemaining);
				return false;
			} else {
				sibling = (Window) r;
			}
		}
		if ((mask & 0x40) != 0) {
			stackMode = io.readByte ();	// Stack mode.
			io.readSkip (3);	// Unused.
		}

		if (_parent.isSelecting (EventCode.MaskSubstructureRedirect)
												&& !_overrideRedirect) {
			EventCode.sendConfigureRequest (_parent._clientComms, stackMode,
					_parent, this, sibling, x, y, width, height, borderWidth,
					mask);
			return false;
		}

		if (isSelecting (EventCode.MaskResizeRedirect) && (width != oldWidth
											|| height != oldHeight)) {
			EventCode.sendResizeRequest (_clientComms, this, width, height);
			width = oldWidth;
			height = oldHeight;
		}

		if (x != oldX || y != oldY || width != oldWidth || height != oldHeight
											|| borderWidth != _borderWidth) {
			if (width != oldWidth || height != oldHeight) {
				_drawable = new Drawable (width, height, 32,
											_attributes[AttrBackgroundPixel]);
				_exposed = false;
			}

			dirty = new Rect (_orect);
			_borderWidth = borderWidth;
			_orect.left = _parent._irect.left + x;
			_orect.top = _parent._irect.top + y;
			_orect.right = _orect.left + width + 2 * borderWidth;
			_orect.bottom = _orect.top + height + 2 * borderWidth;
			_irect.left = _orect.left + borderWidth;
			_irect.top = _orect.top + borderWidth;
			_irect.right = _orect.right - borderWidth;
			_irect.bottom = _orect.bottom - borderWidth;
			changed = true;

			if (x != oldX || y != oldY)
				for (Window w: _children)
					w.move (x - oldX, y - oldY);
		}

		if ((mask & 0x60) != 0) {
			if ((sibling != null && sibling._parent != _parent)) {
				ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
											RequestCode.ConfigureWindow, 0);
				return false;
			}

			if (sibling == null) {
				switch (stackMode) {
					case 0:	// Above.
						_parent._children.remove (this);
						_parent._children.add (this);
						break;
					case 1:	// Below.
						_parent._children.remove (this);
						_parent._children.add (0, this);
					case 2:	// TopIf.
						if (_parent.occludes (null, this)) {
							_parent._children.remove (this);
							_parent._children.add (this);
						}
					case 3:	// BottomIf.
						if (_parent.occludes (this, null)) {
							_parent._children.remove (this);
							_parent._children.add (0, this);
						}
					case 4:	// Opposite.
						if (_parent.occludes (null, this)) {
							_parent._children.remove (this);
							_parent._children.add (this);
						} else if (_parent.occludes (this, null)) {
							_parent._children.remove (this);
							_parent._children.add (0, this);
						}
						break;
				}
			} else {
				int			pos;

				switch (stackMode) {
					case 0:	// Above.
						_parent._children.remove (this);
						pos = _parent._children.indexOf (sibling);
						_parent._children.add (pos + 1, this);
						break;
					case 1:	// Below.
						_parent._children.remove (this);
						pos = _parent._children.indexOf (sibling);
						_parent._children.add (pos, this);
					case 2:	// TopIf.
						if (_parent.occludes (sibling, this)) {
							_parent._children.remove (this);
							_parent._children.add (this);
						}
					case 3:	// BottomIf.
						if (_parent.occludes (this, sibling)) {
							_parent._children.remove (this);
							_parent._children.add (0, this);
						}
					case 4:	// Opposite.
						if (_parent.occludes (sibling, this)) {
							_parent._children.remove (this);
							_parent._children.add (this);
						} else if (_parent.occludes (this, sibling)) {
							_parent._children.remove (this);
							_parent._children.add (0, this);
						}
						break;
				}
			}
			changed = true;
		}

		if (changed) {
			if (isSelecting (EventCode.MaskStructureNotify))
				EventCode.sendConfigureNotify (_clientComms, this, this,
								null, x, y, width, height, _borderWidth,
								_overrideRedirect);
			if (_parent.isSelecting (EventCode.MaskSubstructureNotify))
				EventCode.sendConfigureNotify (_parent._clientComms, _parent,
								this, null, x, y, width, height, _borderWidth,
								_overrideRedirect);
		}

		if (!_exposed) {
			if (isSelecting (EventCode.MaskExposure))
				EventCode.sendExpose (_clientComms, this, 0, 0,
						_drawable.getWidth (), _drawable.getHeight (), 0);
			_exposed = true;
		}

		if (dirty != null && _isMapped && !_inputOnly)
			_screen.postInvalidate (dirty.left, dirty.top, dirty.right,
															dirty.bottom);

		return changed;
	}

	/**
	 * Process an X request relating to this window.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param arg		Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	@Override
	public void
	processRequest (
		InputOutput		io,
		int				sequenceNumber,
		byte			opcode,
		int				arg,
		int				bytesRemaining
	) throws IOException {
		boolean			redraw = false;
		boolean			updatePointer = false;

		switch (opcode) {
			case RequestCode.ChangeWindowAttributes:
				redraw = processWindowAttributes (io, sequenceNumber,
						RequestCode.ChangeWindowAttributes, bytesRemaining);
				updatePointer = true;
				break;
			case RequestCode.GetWindowAttributes:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			vid = _xServer.getRootVisual().getId ();
					int			mapState = _isMapped ? 0 : 2;

					synchronized (io) {
						Util.writeReplyHeader (io, 2, sequenceNumber);
						io.writeInt (3);	// Reply length.
						io.writeInt (vid);	// Visual.
						io.writeShort ((short)
											(_inputOnly ? 2 : 1));	// Class.
						io.writeByte ((byte) _attributes[AttrBitGravity]);
						io.writeByte ((byte) _attributes[AttrWinGravity]);
						io.writeInt (_attributes[AttrBackingPlanes]);
						io.writeInt (_attributes[AttrBackingPixel]);
						io.writeByte ((byte) _attributes[AttrSaveUnder]);
						io.writeByte ((byte) 1);	// Map is installed.
						io.writeByte ((byte) mapState);	// Map-state.
						io.writeByte ((byte) (_overrideRedirect ? 1 : 0));
						io.writeInt (_colormap.getId ());	// Colormap.
						io.writeInt (_attributes[AttrEventMask]);
						io.writeInt (_attributes[AttrEventMask]);
						io.writeShort ((short)
										_attributes[AttrDoNotPropagateMask]);
						io.writePadBytes (2);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.DestroyWindow:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					destroy (io, sequenceNumber, true);
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.DestroySubwindows:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					for (Window w: _children)
						w.destroy (io, sequenceNumber, false);
					_children.clear ();
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.ChangeSaveSet:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					// Do nothing.
				}
				break;
			case RequestCode.ReparentWindow:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			id = io.readInt ();	// Parent.
					int			x = (short) io.readShort ();	// X.
					int			y = (short) io.readShort ();	// Y.
					Resource	r = _xServer.getResource (id);

					if (r == null || r.getType () != Resource.WINDOW) {
						ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
																opcode, id);
					} else {
						reparent (io, sequenceNumber, (Window) r, x, y);
						redraw = true;
						updatePointer = true;
					}
				}
				break;
			case RequestCode.MapWindow:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					map (io, sequenceNumber, true);
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.MapSubwindows:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					mapSubwindows (io, sequenceNumber, true);
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.UnmapWindow:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					map (io, sequenceNumber, false);
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.UnmapSubwindows:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					mapSubwindows (io, sequenceNumber, false);
					redraw = true;
					updatePointer = true;
				}
				break;
			case RequestCode.ConfigureWindow:
				redraw = processConfigureWindow (io, sequenceNumber,
															bytesRemaining);
				updatePointer = true;
				break;
			case RequestCode.CirculateWindow:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					redraw = circulate (io, sequenceNumber, arg);
					updatePointer = true;
				}
				break;
			case RequestCode.GetGeometry:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			rid = _screen.getRootWindow().getId ();
					int			depth = _xServer.getRootVisual().getDepth ();
					int			x, y;
					int			width = _irect.right - _irect.left;
					int			height = _irect.bottom - _irect.top;

					if (_parent == null) {
						x = _orect.left;
						y = _orect.top;
					} else {
						x = _orect.left - _parent._irect.left;
						y = _orect.top - _parent._irect.top;
					}

					synchronized (io) {
						Util.writeReplyHeader (io, depth, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writeInt (rid);	// Root.
						io.writeShort ((short) x);	// X.
						io.writeShort ((short) y);	// Y.
						io.writeShort ((short) width);	// Width.
						io.writeShort ((short) height);	// Height.
						io.writeShort ((short) _borderWidth);	// Border wid.
						io.writePadBytes (10);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.QueryTree:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			rid = _screen.getRootWindow().getId ();
					int			pid =  (_parent == null) ? 0
														: _parent.getId ();

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (_children.size ());	// Reply length.
						io.writeInt (rid);	// Root.
						io.writeInt (pid);	// Parent.
													// Number of children.
						io.writeShort ((short) _children.size ());
						io.writePadBytes (14);	// Unused.

						for (Window w: _children)
							io.writeInt (w.getId ());
					}
					io.flush ();
				}
				break;
			case RequestCode.ChangeProperty:
			case RequestCode.GetProperty:
			case RequestCode.RotateProperties:
				Property.processRequest(_xServer, io, sequenceNumber, arg,
							opcode, bytesRemaining, this, _properties);
				break;
			case RequestCode.DeleteProperty:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			id = io.readInt ();	// Property.
					Atom		a = _xServer.getAtom (id);

					if (a == null) {
						ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
																opcode, id);
					} else if (_properties.containsKey (id)) {
						_properties.remove (id);
						if (isSelecting (EventCode.MaskPropertyChange))
							EventCode.sendPropertyNotify (_clientComms,
										this, a, _xServer.getTimestamp(), 1);
					}
				}
				break;
			case RequestCode.ListProperties:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			n = _properties.size ();

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (n);	// Reply length.
						io.writeShort ((short) n);	// Num atoms.
						io.writePadBytes (22);	// Unused.

						for (Property p: _properties.values ())
							io.writeInt (p.getId ());
					}
					io.flush ();
				}
				break;
			case RequestCode.QueryPointer:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			rid = _screen.getRootWindow().getId ();
					int			rx = _screen.getPointerX ();
					int			ry = _screen.getPointerY ();
					int			mask = _screen.getButtons ();
					int			wx = rx - _irect.left;
					int			wy = ry - _irect.top;
					Window		w = windowAtPoint (rx, ry);
					int			cid = 0;

					if (w._parent == this)
						cid = w.getId ();

					synchronized (io) {
						Util.writeReplyHeader (io, 1, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writeInt (rid);	// Root.
						io.writeInt (cid);	// Child.
						io.writeShort ((short) rx);	// Root X.
						io.writeShort ((short) ry);	// Root Y.
						io.writeShort ((short) wx);	// Win X.
						io.writeShort ((short) wy);	// Win Y.
						io.writeShort ((short) mask);	// Mask.
						io.writePadBytes (6);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.GetMotionEvents:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			numEvents = 0;	// Do nothing.

					io.readInt ();	// Start time.
					io.readInt ();	// Stop time.

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (numEvents * 2);	// Reply length.
						io.writeInt (numEvents);	// Number of events.
						io.writePadBytes (20);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.TranslateCoordinates:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			id = io.readInt ();	// Destination window.
					int			x = (short) io.readShort ();	// Source X.
					int			y = (short) io.readShort ();	// Source Y.
					Resource	r = _xServer.getResource (id);

					if (r == null || r.getType () != Resource.WINDOW) {
						ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
																opcode, id);
					} else {
						Window		w = (Window) r;
						int			dx = _irect.left + x - w._irect.left;
						int			dy = _irect.top + y - w._irect.top;
						int			child = 0;

						for (Window c: w._children)
							if (c._isMapped && c._irect.contains (x, y))
								child = c._id;

						synchronized (io) {
							Util.writeReplyHeader (io, 1, sequenceNumber);
							io.writeInt (0);	// Reply length.
							io.writeInt (child);	// Child.
							io.writeShort ((short) dx);	// Dest X.
							io.writeShort ((short) dy);	// Dest Y.
							io.writePadBytes (16);	// Unused.
						}
						io.flush ();
					}
				}
				break;
			case RequestCode.ClearArea:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			x = (short) io.readShort ();	// Source X.
					int			y = (short) io.readShort ();	// Source Y.
					int			width = io.readShort ();	// Width.
					int			height = io.readShort ();	// Height.

					if (width == 0)
						width = _drawable.getWidth () - x;
					if (height == 0)
						height = _drawable.getHeight () - y;
					_drawable.clearArea (x, y, width, height,
										_attributes[AttrBackgroundPixel]);
					invalidate (x, y, width, height);

					if (arg == 1)
						EventCode.sendExpose (_clientComms, this, x, y, width,
																height, 0);
				}
				break;
			case RequestCode.CopyArea:
			case RequestCode.CopyPlane:
			case RequestCode.PolyPoint:
			case RequestCode.PolyLine:
			case RequestCode.PolySegment:
			case RequestCode.PolyRectangle:
			case RequestCode.PolyArc:
			case RequestCode.FillPoly:
			case RequestCode.PolyFillRectangle:
			case RequestCode.PolyFillArc:
			case RequestCode.PutImage:
			case RequestCode.GetImage:
			case RequestCode.PolyText8:
			case RequestCode.PolyText16:
			case RequestCode.ImageText8:
			case RequestCode.ImageText16:
			case RequestCode.QueryBestSize:
				redraw = _drawable.processRequest (_xServer, io, _id,
								sequenceNumber, opcode, arg, bytesRemaining);
				break;
			case RequestCode.ListInstalledColormaps:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					_screen.writeInstalledColormaps (io, sequenceNumber);
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}

		if (redraw) {
			invalidate ();
			if (updatePointer)
				_screen.updatePointer (0);
		}
	}

	/**
	 * Write a response to a GetInputFocus request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @throws IOException
	 */
	public static void
	writeInputFocus (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		Window			w = xServer.getInputFocus ();
		int				id = (w == null) ? 0 : w.getId ();

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writeInt (id);	// Focus window.
			io.writePadBytes (20);	// Keys. Not implemented.
		}
		io.flush ();
	}

	/**
	 * Process a SetInputFocus request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The sequence number of the request.
	 * @param revertTo	Flag indicating where focus reverts to.
	 * @throws IOException
	 */
	public static void
	processSetInputFocus (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		int				revertTo
	) throws IOException {
		int				id = io.readInt ();	// Focus window.
		Window			w = null;

		io.readInt ();	// Time. Not implemented.

		if (id != 0) {
			Resource	r = xServer.getResource (id);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window,
							sequenceNumber, RequestCode.SetInputFocus, id);
				return;
			}
			w = (Window) r;
		}

		xServer.setInputFocus (w);
	}
}
