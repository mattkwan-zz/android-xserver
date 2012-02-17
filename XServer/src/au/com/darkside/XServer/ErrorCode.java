/**
 * All the X error codes.
 */
package au.com.darkside.XServer;

import java.io.IOException;

/**
 * @author Matthew Kwan
 *
 * All the X error codes.
 */
public class ErrorCode {
	public static final byte	None = 0;
	public static final byte	Request = 1;
	public static final byte	Value = 2;
	public static final byte	Window = 3;
	public static final byte	Pixmap = 4;
	public static final byte	Atom = 5;
	public static final byte	Cursor = 6;
	public static final byte	Font = 7;
	public static final byte	Match = 8;
	public static final byte	Drawable = 9;
	public static final byte	Access = 10;
	public static final byte	Alloc = 11;
	public static final byte	Colormap = 12;
	public static final byte	GContext = 13;
	public static final byte	IDChoice = 14;
	public static final byte	Name = 15;
	public static final byte	Length = 16;
	public static final byte	Implementation = 17;

	/**
	 * Write an X error.
	 *
	 * @param io	The input/output stream.
	 * @param error	The error code.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The opcode of the error request.
	 * @param resourceId	The (optional) resource ID that cause the error.
	 * @throws IOException
	 */
	public static void
	write (
		InputOutput		io,
		byte			error,
		int				sequenceNumber,
		byte			opcode,
		int				resourceId
	) throws IOException {
		synchronized (io) {
			io.writeByte ((byte) 0);	// Indicates an error.
			io.writeByte (error);		// Error code.
			io.writeShort ((short) (sequenceNumber & 0xffff));	// Seq number.
			io.writeInt (resourceId);	// Bad resource ID.
			io.writeShort ((short) 0);	// Minor opcode.
			io.writeByte (opcode);		// Major opcode.
			io.writePadBytes (21);		// Unused.
		}
		io.flush ();
	}
}
