/**
 * This class handles an X pointer.
 */
package au.com.darkside.XServer;

import java.io.IOException;

import android.graphics.Rect;


/**
 * @author Matthew Kwan
 *
 * This class handles an X pointer.
 */
public class Pointer {
	private byte[]		_buttonMap = {1, 2, 3};

	/**
	 * Constructor.
	 */
	Pointer () {
	}

	/**
	 * Process a WarpPointer request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @throws IOException
	 */
	public void
	processWarpPointer (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		int			swin = io.readInt ();	// Source window.
		int			dwin = io.readInt ();	// Destination window.
		int			sx = io.readShort ();	// Source X.
		int			sy = io.readShort ();	// Source Y.
		int			width = io.readShort ();	// Source width.
		int			height = io.readShort ();	// Source height.
		int			dx = io.readShort ();	// Destination X.
		int			dy = io.readShort ();	// Destination Y.
		ScreenView	screen = xServer.getScreen ();
		boolean		ok = true;
		int			x, y;

		if (dwin == 0) {
			x = screen.getPointerX () + dx;
			y = screen.getPointerY () + dy;
		} else {
			Resource	r = xServer.getResource (dwin);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.WarpPointer, dwin);
				ok = false;
			}

			Rect	rect = ((Window) r).getIRect ();

			x = rect.left + dx;
			y = rect.top + dy;
		}

		if (swin != 0) {
			Resource	r = xServer.getResource (swin);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
											RequestCode.WarpPointer, swin);
				ok = false;
			} else {
				Window		w = (Window) r;
				Rect		rect = w.getIRect ();

				sx += rect.left;
				sy += rect.top;

				if (width == 0)
					width = rect.right - sx;
				if (height == 0)
					height = rect.bottom - sy;

				if (x < sx || x >= sx + width || y < sy || y >= sy + height)
					ok = false;
			}
		}
		
		if (ok)
			screen.updatePointerPosition (x, y, 0);
	}

	/**
	 * Process an X request relating to the pointers.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param arg		Optional first argument.
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
			case RequestCode.WarpPointer:
				if (bytesRemaining != 20) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length,
												sequenceNumber, opcode, 0);
				} else {
					processWarpPointer (xServer, io, sequenceNumber);
				}
				break;
			case RequestCode.ChangePointerControl:
				if (bytesRemaining != 8)
					ErrorCode.write (io, ErrorCode.Length,
												sequenceNumber, opcode, 0);
				io.readSkip (bytesRemaining);
				break;	// Do nothing.
			case RequestCode.GetPointerControl:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length,
												sequenceNumber, opcode, 0);
				} else {
					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writeShort ((short) 1);	// Acceleration numerator.
						io.writeShort ((short) 1);	// Acceleration denom.
						io.writeShort ((short) 1);	// Threshold.
						io.writePadBytes (18);	// Unused.
					}
					io.flush ();
				}
				break;				
			case RequestCode.SetPointerMapping:
				if (bytesRemaining != arg + (-arg & 3)) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else if (arg != _buttonMap.length) {
					ErrorCode.write (io, ErrorCode.Value, sequenceNumber,
																opcode, 0);
				} else {
					io.readBytes (_buttonMap, 0, arg);
					io.readSkip (arg);	// Map. Not implemented.
					io.readSkip (-arg & 3);	// Unused.

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writePadBytes (24);	// Unused.
					}
					io.flush ();

					xServer.sendMappingNotify (2, 0, 0);
				}
				break;
			case RequestCode.GetPointerMapping:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			n = _buttonMap.length;
					int			pad = -n & 3;

					synchronized (io) {
						Util.writeReplyHeader (io, n, sequenceNumber);
						io.writeInt ((n + pad) / 4);	// Reply length.
						io.writePadBytes (24);	// Unused.

						io.writeBytes (_buttonMap, 0, n);	// Map.
						io.writePadBytes (pad);	// Unused.
					}
					io.flush ();
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}
	}
}
