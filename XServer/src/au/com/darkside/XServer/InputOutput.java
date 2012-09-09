/**
 * This class handles buffered bi-directional communications.
 */
package au.com.darkside.XServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * @author Matthew Kwan
 *
 * This class handles buffered bi-directional communications.
 */
public class InputOutput {
	private final BufferedInputStream	_inStream;
	private final BufferedOutputStream	_outStream;
	private boolean						_msb = true;

	/**
	 * Constructor.
	 *
	 * @param socket	Communicate via this socket.
	 * @throws IOException
	 */
	public InputOutput (
		Socket		socket
	) throws IOException {
		_inStream = new BufferedInputStream (socket.getInputStream (), 1024);
		_outStream = new BufferedOutputStream (socket.getOutputStream (),
																		1024);
	}

	/**
	 * Set whether the most significant byte comes first.
	 *
	 * @param msb
	 */
	public void
	setMSB (
		boolean			msb
	) {
		_msb = msb;
	}

	/**
	 * Read an 8-bit integer from the input stream.
	 *
	 * @return	An 8-bit integer in the range 0 to 255.
	 * @throws IOException
	 */
	public int
	readByte () throws IOException {
		int		n = _inStream.read ();

		if (n < 0)
			throw new IOException ();
		else
			return n;
	}

	/**
	 * Read bytes from the input stream.
	 *
	 * @param ba		The array to store the bytes to.
	 * @param offset	The start position in the array to store the bytes.
	 * @param length	The maximum number of bytes to store.
	 * @throws IOException
	 */
	public void
	readBytes (
		byte[]		ba,
		int			offset,
		int			length
	) throws IOException {
		while  (length > 0) {
			int			n = _inStream.read (ba, offset, length);

			if (n < 0) {
				throw new IOException ();
			} else {
				length -= n;
				offset += n;
			}
		}
	}

	/**
	 * Read bits from the input stream as an array of booleans.
	 *
	 * @param bits	The array to store the bits to.
	 * @param offset	The start position in the array to store the bits.
	 * @param length	The maximum number of bits to store.
	 * @throws IOException
	 */
	public void
	readBits (
		boolean[]	bits,
		int			offset,
		int			length
	) throws IOException {
		for (int i = 0; i < length; i += 8) {
			int		x = readByte ();
			int		n = length - i;

			if (n > 8)
				n = 8;

			for (int j = 0; j < n; j++)
				bits[offset + i + j] = ((x & (1 << j)) != 0);
		}
	}

	/**
	 * Read a 16-bit integer from the output stream.
	 *
	 * @return	A 16-bit integer in the range 0 to 65535.
	 * @throws IOException
	 */
	public int
	readShort () throws IOException {
		if (_msb) {
			int		n = readByte ();

			return (n << 8) | readByte ();
		} else {
			int		n = readByte ();

			return n | (readByte () << 8);
		}
	}

	/**
	 * Read a 32-bit integer from the input stream.
	 *
	 * @return	A 32-bit signed integer.
	 * @throws IOException
	 */
	public int
	readInt () throws IOException {
		int		n = readByte ();

		if (_msb) {
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
		} else {
			n |= readByte () << 8;
			n |= readByte () << 16;
			n |= readByte () << 24;
		}

		return n;
	}

	/**
	 * Read a 64-bit integer from the input stream.
	 *
	 * @return	A 64-bit signed integer.
	 * @throws IOException
	 */
	public long
	readLong () throws IOException {
		long	n = readByte ();
		
		if (_msb) {
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
			n = (n << 8) | readByte ();
		} else {
			n |= readByte () << 8;
			n |= readByte () << 16;
			n |= readByte () << 24;
			n |= readByte () << 32;
			n |= readByte () << 40;
			n |= readByte () << 48;
			n |= readByte () << 56;
		}

		return n;
	}

	/**
	 * Skip bytes from the input stream.
	 *
	 * @param n		The number of bytes to skip.
	 * @throws IOException
	 */
	public void
	readSkip (
		int		n
	) throws IOException {
		for (int i = 0; i < n; i++)
			readByte ();
	}

	/**
	 * Write an 8-bit integer to the output stream.
	 *
	 * @param n		The byte to write.
	 * @throws IOException
	 */
	public void
	writeByte (
		byte		n
	) throws IOException {
		_outStream.write (n);
	}

	/**
	 * Write bytes to the output stream.
	 *
	 * @param ba		The array to be written.
	 * @param offset	The start position in the array to write from.
	 * @param length	The number of bytes to write.
	 * @throws IOException
	 */
	public void
	writeBytes (
		byte[]		ba,
		int			offset,
		int			length
	) throws IOException {
		_outStream.write (ba, offset, length);
	}

	/**
	 * Write a 16-bit integer to the output stream.
	 *
	 * @param n		The short to write.
	 * @throws IOException
	 */
	public void
	writeShort (
		short		n
	) throws IOException {
		if (_msb) {
			_outStream.write ((byte) ((n >> 8) & 0xff));
			_outStream.write ((byte) (n & 0xff));
		} else {
			_outStream.write ((byte) (n & 0xff));
			_outStream.write ((byte) ((n >> 8) & 0xff));
		}
	}

	/**
	 * Write a 32-bit integer to the output stream.
	 *
	 * @param n		The integer to write.
	 * @throws IOException
	 */
	public void
	writeInt (
		int		n
	) throws IOException {
		if (_msb) {
			_outStream.write ((byte) ((n >> 24) & 0xff));
			_outStream.write ((byte) ((n >> 16) & 0xff));
			_outStream.write ((byte) ((n >> 8) & 0xff));
			_outStream.write ((byte) (n & 0xff));
		} else {
			_outStream.write ((byte) ((n) & 0xff));
			_outStream.write ((byte) ((n >> 8) & 0xff));
			_outStream.write ((byte) ((n >> 16) & 0xff));
			_outStream.write ((byte) ((n >> 24) & 0xff));
		}
	}

	/**
	 * Write a 64-bit integer to the output stream.
	 *
	 * @param n		The integer to write.
	 * @throws IOException
	 */
	public void
	writeLong (
		long	n
	) throws IOException{
		if (_msb) {
			_outStream.write ((byte) ((n >> 56) & 0xff));
			_outStream.write ((byte) ((n >> 48) & 0xff));
			_outStream.write ((byte) ((n >> 40) & 0xff));
			_outStream.write ((byte) ((n >> 32) & 0xff));
			_outStream.write ((byte) ((n >> 24) & 0xff));
			_outStream.write ((byte) ((n >> 16) & 0xff));
			_outStream.write ((byte) ((n >> 8) & 0xff));
			_outStream.write ((byte) (n & 0xff));
		} else {
			_outStream.write ((byte) ((n) & 0xff));
			_outStream.write ((byte) ((n >> 8) & 0xff));
			_outStream.write ((byte) ((n >> 16) & 0xff));
			_outStream.write ((byte) ((n >> 24) & 0xff));
			_outStream.write ((byte) ((n >> 32) & 0xff));
			_outStream.write ((byte) ((n >> 40) & 0xff));
			_outStream.write ((byte) ((n >> 48) & 0xff));
			_outStream.write ((byte) ((n >> 56) & 0xff));
		}
	}

	/**
	 * Write padding byte 0 to the output stream multiple times.
	 *
	 * @param n		The number of times to write the byte.
	 * @throws IOException
	 */
	public void
	writePadBytes (
		int		n
	) throws IOException {
		for (int i = 0; i < n; i++)
			writeByte ((byte) 0);
	}

	/**
	 * Flush all unwritten output bytes.
	 *
	 * @throws IOException
	 */
	public synchronized void
	flush () throws IOException {
		_outStream.flush ();
	}

	/**
	 * Close the input and output streams.
	 *
	 * @throws IOException
	 */
	public void
	close () throws IOException {
		_inStream.close ();
		_outStream.close ();
	}
}