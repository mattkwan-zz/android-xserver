/**
 * An X TrueColor colormap.
 */
package au.com.darkside.XServer;

import java.io.IOException;

import android.graphics.Color;

/**
 * @author Matthew Kwan
 * 
 * An X TrueColor colormap.
 */
public class Colormap extends Resource {
	private boolean			_installed = false;
	private final ScreenView	_screen;

	/**
	 * Constructor.
	 *
	 * @param id	The colormap ID.
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 */
	public Colormap (
		int			id,
		XServer		xServer,
		ClientComms	client,
		ScreenView		screen
	) {
		super (COLORMAP, id, xServer, client);

		_screen = screen;
	}

	/**
	 * Return the value of a black pixel.
	 *
	 * @return	The value of a black pixel.
	 */
	public int
	getBlackPixel () {
		return Color.BLACK;
	}

	/**
	 * Return the value of a white pixel.
	 *
	 * @return	The value of a white pixel.
	 */
	public int
	getWhitePixel () {
		return Color.WHITE;
	}

	/**
	 * Return the ARGB color corresponding to the (TrueColor) pixel.
	 * Add the alpha channel if necessary.
	 *
	 * @param pixel	The TrueColor pixel.
	 * @return	The ARGB color corresponding to the pixel.
	 */
	public int
	getPixelColor (
		int		pixel
	) {
		if ((pixel & 0xff000000) == 0)
			return pixel | 0xff000000;
		else
			return pixel;
	}

	/**
	 * Get the colormap's screen.
	 *
	 * @return	The colormap's screen.
	 */
	public ScreenView
	getScreen () {
		return _screen;
	}

	/**
	 * Has the colormap been installed?
	 *
	 * @return	True if the colormap has been installed.
	 */
	public boolean
	getInstalled () {
		return _installed;
	}

	/**
	 * Set whether the colormap has been installed.
	 *
	 * @param installed	Whether the colormap has been installed.
	 */
	public void
	setInstalled (
		boolean		installed
	) {
		_installed = installed;
		if (_installed)
			_screen.addInstalledColormap (this);
		else
			_screen.removeInstalledColormap (this);
	}

	/**
	 * Construct a color from 16-bit RGB components.
	 *
	 * @param r	The red component.
	 * @param g	The green component.
	 * @param b	The blue component.
	 * @return
	 */
	public static int
	fromParts16 (
		int		r,
		int		g,
		int		b
	) {
		return 0xff000000 | ((r & 0xff00) << 8) | (g & 0xff00)
											| ((b & 0xff00) >> 8);
	}

	/**
	 * Process an X request relating to this colormap.
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
		switch (opcode) {
			case RequestCode.FreeColormap:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else if (_id != _screen.getDefaultColormap().getId ()) {
					_xServer.freeResource (_id);
					if (_clientComms != null)
						_clientComms.freeResource (this);
				}
				break;
			case RequestCode.InstallColormap:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					setInstalled (true);
				}
				break;
			case RequestCode.UninstallColormap:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					setInstalled (false);
				}
				break;
			case RequestCode.AllocColor:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			r = io.readShort ();	// Red.
					int			g = io.readShort ();	// Green.
					int			b = io.readShort ();	// Blue.
					int			color = fromParts16 (r, g,b);

					io.readSkip (2);	// Unused.
					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writeShort ((short) r);	// Red.
						io.writeShort ((short) g);	// Green.
						io.writeShort ((short) b);	// Blue.
						io.writePadBytes (2);	// Unused.
						io.writeInt (color);	// Pixel.
						io.writePadBytes (12);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.AllocNamedColor:
				if (bytesRemaining < 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			n = io.readShort ();	// Length of name.
					int			pad = -n & 3;

					io.readSkip (2);	// Unused.
					bytesRemaining -= 4;
					if (bytesRemaining != n + pad) {
						io.readSkip (bytesRemaining);
						ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
					} else {	// Not implemented.
						io.readSkip (bytesRemaining);
						ErrorCode.write (io, ErrorCode.Name, sequenceNumber,
																opcode, 0);
					}
				}
				break;
			case RequestCode.AllocColorCells:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readShort ();	// Number of colors.
					io.readShort ();	// Number of planes.

						// Not implemented.
					ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				}
				break;
			case RequestCode.AllocColorPlanes:
				if (bytesRemaining != 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readShort ();	// Colors.
					io.readShort ();	// Reds.
					io.readShort ();	// Greens.
					io.readShort ();	// Blues.

						// Not implemented.
					ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				}
				break;
			case RequestCode.FreeColors:
				if (bytesRemaining < 4 || (bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {	// Nothing to do.
					io.readInt ();	// Plane mask.
					bytesRemaining -= 4;
					io.readSkip (bytesRemaining);	// Pixels.
				}
				break;
			case RequestCode.StoreColors:
				if ((bytesRemaining % 12) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {	// Cannot modify a TrueColor colormap.
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Access, sequenceNumber,
																opcode, 0);
				}
				break;
			case RequestCode.StoreNamedColor:
				if (bytesRemaining < 8) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {	// Cannot modify a TrueColor colormap.
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Access, sequenceNumber,
																opcode, 0);
				}
				break;
			case RequestCode.QueryColors:
				if ((bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			n = bytesRemaining / 4;
					int[]		pixels = new int[n];

					for (int i = 0; i < n; i++)
						pixels[i] = io.readInt ();

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (n * 2);	// Reply length.
						io.writeShort ((short) n);	// Number of RGBs.
						io.writePadBytes (22);	// Unused.

						for (int i = 0; i < n; i++) {
							int		color = pixels[i];
							int		r = (color & 0xff0000) >> 16;
							int		g = (color & 0xff00) >> 8;
							int		b = color & 0xff;

							io.writeShort ((short) (r | (r << 8)));	// Red.
							io.writeShort ((short) (g | (g << 8)));	// Green.
							io.writeShort ((short) (b | (b << 8)));	// Blue.
							io.writePadBytes (2);	// Unused.
						}
					}
					io.flush ();
				}
				break;
			case RequestCode.LookupColor:
				if (bytesRemaining < 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			n = io.readShort ();	// Length of name.
					int			pad = -n & 3;

					io.readSkip (2);	// Unused.
					bytesRemaining -= 4;
					if (bytesRemaining != n + pad) {
						io.readSkip (bytesRemaining);
						ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
					} else {	// Not implemented.
						io.readSkip (bytesRemaining);
						ErrorCode.write (io, ErrorCode.Name, sequenceNumber,
																opcode, 0);
					}
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation, sequenceNumber,
																opcode, 0);
				break;
		}
	}

	/**
	 * Process a CreateColormap request.
	 *
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param id	The ID of the colormap to create.
	 * @param alloc		Allocate colours; 0=None, 1=All.
	 * @throws IOException
	 */
	public static void
	processCreateColormapRequest (
		XServer			xServer,
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		int				id,
		int				alloc
	) throws IOException {
		int			wid = io.readInt ();	// Window.
		int			vid = io.readInt ();	// Visual.
		Resource	r = xServer.getResource (wid);

		if (alloc != 0)	{	// Only TrueColor supported.
			ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
									RequestCode.CreateColormap, id);
		} else if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
									RequestCode.CreateColormap, wid);
		} else if (vid != xServer.getRootVisual().getId ()) {
			ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
									RequestCode.CreateColormap, wid);
		} else {
			ScreenView		s = ((Window) r).getScreen ();
			Colormap	cmap = new Colormap (id, xServer, client, s);

			xServer.addResource (cmap);
			client.addResource (cmap);
		}
	}

	/**
	 * Process a CopyColormapAndFree request.
	 *
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param id	The ID of the colormap to create.
	 * @throws IOException
	 */
	public void
	processCopyColormapAndFree (
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		int				id
	) throws IOException {
		Colormap	cmap = new Colormap (id, _xServer, client, _screen);

			// Nothing to copy, nothing to free.
		_xServer.addResource (cmap);
		client.addResource (cmap);
	}
}