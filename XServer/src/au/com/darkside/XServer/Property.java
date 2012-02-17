/**
 * This class implements a selection.
 */
package au.com.darkside.XServer;

import java.io.IOException;
import java.util.Hashtable;


/**
 * @author Matthew KWan
 * 
 * This class implements a selection.
 */
public class Property {
	private final int	_id;
	private int			_type;
	private int			_format;
	private byte[]		_data = null;

	/**
	 * Constructor.
	 *
	 * @param id	The property's ID.
	 * @param type	The ID of the property's type atom.
	 * @param format	Data format = 8, 16, or 32.
	 */
	public Property (
		int			id,
		int			type,
		int			format
	) {
		_id = id;
		_type = type;
		_format = format;
	}

	/**
	 * Constructor.
	 *
	 * @param p	The property to copy.
	 */
	private Property (
		final Property	p
	) {
		_id = p._id;
		_type = p._type;
		_format = p._format;
		_data = p._data;
	}

	/**
	 * Return the property's atom ID.
	 *
	 * @return	The property's atom ID.
	 */
	public int
	getId () {
		return _id;
	}

	/**
	 * Process an X request relating to properties.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param arg	Optional first argument.
	 * @param opcode	The request's opcode.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @param w	The window containing the properties.
	 * @param properties	Hash table of the window's properties.
	 * @throws IOException
	 */
	public static void
	processRequest (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		int				arg,
		byte			opcode,
		int				bytesRemaining,
		Window			w,
		Hashtable<Integer, Property>	properties
	) throws IOException {
		switch (opcode) {
			case RequestCode.ChangeProperty:
				processChangePropertyRequest (xServer, io, sequenceNumber,
										arg, bytesRemaining, w, properties);
				break;
			case RequestCode.GetProperty:
				processGetPropertyRequest (xServer, io, sequenceNumber,
									arg == 1, bytesRemaining, w, properties);
				break;
			case RequestCode.RotateProperties:
				processRotatePropertiesRequest (xServer, io, sequenceNumber,
											bytesRemaining, w, properties);
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation, sequenceNumber,
																opcode, 0);
				break;
		}
	}

	/**
	 * Process a ChangeProperty request.
	 * Change the owner of the specified selection.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param mode	0=Replace 1=Prepend 2=Append.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @param w	The window containing the properties.
	 * @param properties	Hash table of the window's properties.
	 * @throws IOException
	 */
	public static void
	processChangePropertyRequest (
		XServer				xServer,
		InputOutput			io,
		int					sequenceNumber,
		int					mode,
		int					bytesRemaining,
		Window				w,
		Hashtable<Integer, Property>	properties
	) throws IOException {
		if (bytesRemaining < 16) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.ChangeProperty, 0);
			return;
		}

		int			pid = io.readInt ();	// Property atom.
		int			tid = io.readInt ();	// Type atom.
		int			format = io.readByte ();	// Format.

		io.readSkip (3);	// Unused.

		int			length = io.readInt ();	// Length of data.
		int			n, pad;

		if (format == 8)
			n = length;
		else if (format == 16)
			n = length * 2;
		else
			n = length * 4;

		pad = -n & 3;

		bytesRemaining -= 16;
		if (bytesRemaining != n + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.ChangeProperty, 0);
			return;
		}

		byte[]		data = new byte[n];

		io.readBytes (data, 0, n);
		io.readSkip (pad);	// Unused.

		Atom		property = xServer.getAtom (pid);

		if (property == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.ChangeProperty, pid);
			return;
		}

		if (!xServer.atomExists (tid)) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.ChangeProperty, tid);
			return;
		}

		Property	p;

		if (properties.containsKey (pid)) {
			p = properties.get (pid);
		} else {
			p = new Property (pid, tid, format);
			properties.put (pid, p);
		}

		if (mode == 0) {	// Replace.
			p._type = tid;
			p._format = format;
			p._data = data;
		} else {
			if (tid != p._type || format != p._format) {
				ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
											RequestCode.ChangeProperty, 0);
				return;
			}

			if (p._data == null) {
				p._data = data;
			} else {
				byte[]		d1, d2;

				if (mode == 1) {	// Prepend.
					d1 = data;
					d2 = p._data;
				} else {	// Append.
					d1 = p._data;
					d2 = data;
				}

				p._data = new byte[d1.length + d2.length];
				System.arraycopy (d1, 0, p._data, 0, d1.length);
				System.arraycopy (d2, 0, p._data, d1.length, d2.length);
			}
		}

		if (w.isSelecting (EventCode.MaskPropertyChange))
			EventCode.sendPropertyNotify (w.getClientComms (), w, property,
												xServer.getTimestamp (), 0);

	}

	/**
	 * Process a GetProperty request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param delete	Delete flag.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @param w	The window containing the properties.
	 * @param properties	Hash table of the window's properties.
	 * @throws IOException
	 */
	public static void
	processGetPropertyRequest (
		XServer				xServer,
		InputOutput			io,
		int					sequenceNumber,
		boolean				delete,
		int					bytesRemaining,
		Window				w,
		Hashtable<Integer, Property>	properties
	) throws IOException {
		if (bytesRemaining != 16) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.GetProperty, 0);
			return;
		}

		int			pid = io.readInt ();	// Property.
		int			tid = io.readInt ();	// Type.
		int			longOffset = io.readInt ();	// Long offset.
		int			longLength = io.readInt ();	// Long length.
		Atom		property = xServer.getAtom (pid);

		if (property == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.GetProperty, pid);
			return;
		} else if (tid != 0 && !xServer.atomExists (tid)) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.GetProperty, tid);
			return;
		}

		int			format = 0;
		int			bytesAfter = 0;
		byte[]		value = null;
		boolean		generateNotify = false;

		if (properties.containsKey (pid)) {
			Property	p = properties.get	(pid);

			if (tid != 0 && tid != p._type) {
				tid = p._type;
				format = p._format;
				bytesAfter = (p._data == null) ? 0 : p._data.length;
			} else {
				tid = p._type;
				format = p._format;

				int		n, i, t, l;

				n = (p._data == null) ? 0 : p._data.length;
				i = 4 * longOffset;
				t = n - i;

				l = 4 * longLength;
				if (t < l)
					l = t;
				bytesAfter = n - (i + l);

				if (l < 0) {
					ErrorCode.write (io, ErrorCode.Value, sequenceNumber,
												RequestCode.GetProperty, 0);
					return;
				}

				value = new byte[l];
				System.arraycopy (p._data, i, value, 0, l);

				if (delete && bytesAfter == 0) {
					properties.remove (pid);
					generateNotify = true;
				}
			}
		}

		int			valueLength = (value == null) ? 0 : value.length;
		int			pad = -valueLength & 3;

		synchronized (io) {
			Util.writeReplyHeader (io, format, sequenceNumber);
			io.writeInt ((valueLength + pad) / 4);	// Reply length.
			io.writeInt (tid);	// Type.
			io.writeInt (bytesAfter);	// Bytes after.
			io.writeInt (valueLength);	// Value length.
			io.writePadBytes (12);	// Unused.

			if (value != null) {
				io.writeBytes (value, 0, value.length);	// Value.
				io.writePadBytes (pad);	// Unused.
			}
		}
		io.flush ();

		if (generateNotify && w.isSelecting (EventCode.MaskPropertyChange))
			EventCode.sendPropertyNotify (w.getClientComms (), w, property,
												xServer.getTimestamp (), 1);
	}

	/**
	 * Process a RotateProperties request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @param w	The window containing the properties.
	 * @param properties	Hash table of the window's properties.
	 * @throws IOException
	 */
	public static void
	processRotatePropertiesRequest (
		XServer				xServer,
		InputOutput			io,
		int					sequenceNumber,
		int					bytesRemaining,
		Window				w,
		Hashtable<Integer, Property>	properties
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
										RequestCode.RotateProperties, 0);
			return;
		}

		int			n = io.readShort ();	// Num properties.
		int			delta = io.readShort ();	// Delta.

		bytesRemaining -= 4;
		if (bytesRemaining != n * 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
										RequestCode.RotateProperties, 0);
			return;
		}

		if (n == 0 || (delta % n) == 0)
			return;

		int[]		aids = new int[n];
		Property[]	props = new Property[n];
		Property[]	pcopy = new Property[n];

		for (int i = 0; i < n; i++)
			aids[i] = io.readInt ();

		for (int i = 0; i < n; i++) {
			if (!xServer.atomExists (aids[i])) {
				ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
									RequestCode.RotateProperties, aids[i]);
				return;
			} else if (!properties.containsKey (aids[i])) {
				ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
									RequestCode.RotateProperties, aids[i]);
				return;
			} else {
				props[i] = properties.get (aids[i]);
				pcopy[i] = new Property (props[i]);
			}
		}

		for (int i = 0; i < n; i++) {
			Property	p = props[i];
			Property	pc = pcopy[(i + delta) % n];

			p._type = pc._type;
			p._format = pc._format;
			p._data = pc._data;
		}

		if (w.isSelecting (EventCode.MaskPropertyChange)) {
			for (int i = 0; i < n; i++)
				EventCode.sendPropertyNotify (w.getClientComms (), w,
										xServer.getAtom (aids[i]),
										xServer.getTimestamp (), 0);
		}
	}
}