/**
 * This class implements an X font.
 */
package au.com.darkside.XServer;

import java.io.IOException;
import java.util.Vector;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;


/**
 * @author Matthew Kwan
 *
 * This class implements an X font.
 */
public class Font extends Resource {
	private static int	_dpi = 250;

	private Paint		_paint;
	private float		_minWidth;
	private float		_maxWidth;
	private char		_maxChar = 255;
	private Atom		_nameAtom = null;

	private static final String[]	_allFonts = {
		"-android-default-medium-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-default-bold-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-default-medium-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-default-bold-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-default-medium-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-default-bold-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-default-medium-i-normal--0-0-0-0-p-0-iso10646-1",
		"-android-default-bold-i-normal--0-0-0-0-p-0-iso10646-1",
		"-android-monospace-medium-r-normal--0-0-0-0-c-0-iso8859-1",
		"-android-monospace-bold-r-normal--0-0-0-0-c-0-iso8859-1",
		"-android-monospace-medium-i-normal--0-0-0-0-c-0-iso8859-1",
		"-android-monospace-bold-i-normal--0-0-0-0-c-0-iso8859-1",
		"-android-monospace-medium-r-normal--0-0-0-0-c-0-iso10646-1",
		"-android-monospace-bold-r-normal--0-0-0-0-c-0-iso10646-1",
		"-android-monospace-medium-i-normal--0-0-0-0-c-0-iso10646-1",
		"-android-monospace-bold-i-normal--0-0-0-0-c-0-iso10646-1",
		"-android-serif-medium-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-serif-bold-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-serif-medium-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-serif-bold-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-serif-medium-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-serif-bold-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-serif-medium-i-normal--0-0-0-0-p-0-iso10646-1",
		"-android-serif-bold-i-normal--0-0-0-0-p-0-iso10646-1",
		"-android-sans serif-medium-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-sans serif-bold-r-normal--0-0-0-0-p-0-iso8859-1",
		"-android-sans serif-medium-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-sans serif-bold-i-normal--0-0-0-0-p-0-iso8859-1",
		"-android-sans serif-medium-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-sans serif-bold-r-normal--0-0-0-0-p-0-iso10646-1",
		"-android-sans serif-medium-i-normal--0-0-0-0-p-0-iso10646-1",
		"-android-sans serif-bold-i-normal--0-0-0-0-p-0-iso10646-1",
		"fixed",
		"cursor"
	};

	private static String[][]	_allFontFields = null;

	/**
	 * Set the dots-per-inch resolution at which fonts will be displayed.
	 *
	 * @param dpi	The dots-per-inch resolution.
	 */
	public static void
	setDpi (
		int			dpi
	) {
		_dpi = dpi;
	}

	/**
	 * Constructor.
	 *
	 * @param id		The server font ID.
	 * @param xserver	The X server.
	 * @param client	The client issuing the request.
	 * @param name		The name of the font. May be null.
	 */
	public Font (
		int			id,
		XServer		xServer,
		ClientComms	client,
		String		name
	) {
		super (FONT, id, xServer, client);

		_paint = new Paint ();
		if (name == null || name.equalsIgnoreCase ("cursor")) {
			_paint.setTypeface (Typeface.DEFAULT);
		} else if (name.equalsIgnoreCase ("fixed")) {
			_paint.setTypeface (Typeface.MONOSPACE);
		} else {
			String[]	fields = name.split ("-");
			Typeface	base = Typeface.DEFAULT;
			int			style = Typeface.NORMAL;

			if (fields.length == 15) {
				if (fields[3].equalsIgnoreCase ("bold"))
					style |= Typeface.BOLD;
				if (fields[4].equalsIgnoreCase ("i"))
					style |= Typeface.ITALIC;

				try {
					int		n = Integer.valueOf (fields[7]);

					if (n > 0)
						_paint.setTextSize (n);
				} catch (java.lang.NumberFormatException e) {
				}

				if (!fields[11].equalsIgnoreCase ("p"))
					base = Typeface.MONOSPACE;
				else if (fields[2].equalsIgnoreCase ("default"))
					base = Typeface.DEFAULT;
				else if (fields[2].equalsIgnoreCase ("serif"))
					base = Typeface.SERIF;
				else if (fields[2].equalsIgnoreCase ("sans serif"))
					base = Typeface.SANS_SERIF;
				else
					base = Typeface.create (fields[2], style);

				if (fields[13].equalsIgnoreCase ("iso10646"))
					_maxChar = 65534;
			}

			_paint.setTypeface (Typeface.create (base, style));
		}

			// Calculate the minimum and maximum widths.
		byte[]		bytes = new byte[126 - 32 + 1];
		float[]		widths = new float[bytes.length];

		for (int i = 0; i < bytes.length; i++)
			bytes[i] = (byte) (i + 32);

		_paint.getTextWidths (new String (bytes), widths);

		_minWidth = widths[0];
		_maxWidth = widths[0];
		for (int i = 1; i < widths.length; i++) {
			if (widths[i] < _minWidth)
				_minWidth = widths[i];
			if (widths[i] > _maxWidth)
				_maxWidth = widths[i];
		}
	}

	/**
	 * Return the font's typeface.
	 * @return	The font's typeface.
	 */
	public Typeface
	getTypeface () {
		return _paint.getTypeface ();
	}

	/**
	 * Return the font's size.
	 * @return	The font's size.
	 */
	public int
	getSize () {
		return (int) _paint.getTextSize ();
	}

	/**
	 * Process an X request relating to this font.
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
			case RequestCode.CloseFont:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					_xServer.freeResource (_id);
					if (_clientComms != null)
						_clientComms.freeResource (this);
				}
				break;
			case RequestCode.QueryFont:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processQueryFontRequest (io, sequenceNumber);
				}
				break;
			case RequestCode.QueryTextExtents:
				if (bytesRemaining < 4 || (bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			pad = (arg == 0) ? 0 : 2;
					int			length = (bytesRemaining - pad) / 2;
					char[]		chars = new char[length];

					for (int i = 0; i < length; i++) {
						int			b1 = io.readByte ();
						int			b2 = io.readByte ();

						chars[i] = (char) ((b1 << 8) | b2);
					}

					io.readSkip (pad);
					processQueryTextExtentsRequest (io, sequenceNumber,
														new String (chars));
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}
	}

	/**
	 * Process an OpenFont request.
	 *
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param id	The ID of the font to create.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processOpenFontRequest (
		XServer			xServer,
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		int				id,
		int				bytesRemaining
	) throws IOException {
		int				length = io.readShort ();	// Length of name.
		int				pad = -length & 3;

		io.readSkip (2);	// Unused.
		bytesRemaining -= 4;
		if (bytesRemaining != length + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
												RequestCode.OpenFont, 0);
			return;
		}

		byte[]		nameBytes = new byte[length];

		io.readBytes (nameBytes, 0, length);
		io.readSkip (pad);

		String		name = new String (nameBytes);
		Font		f = new Font (id, xServer, client, name);

		xServer.addResource (f);
		client.addResource (f);

			// Create an atom containing the font name.
		Atom		a = xServer.findAtom (name);

		if (a == null) {
			a = new Atom (xServer.nextFreeAtomId (), name);
			xServer.addAtom (a);
		}

		f._nameAtom = a;
	}

	/**
	 * Process a QueryFont request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @throws IOException
	 */
	private void
	processQueryFontRequest (
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		int				numFontProperties = (_nameAtom == null) ? 0 : 1;
		int				numCharInfos = _maxChar - 31;
		char[]			chars = new char[numCharInfos];

		for (char c = 32; c <= _maxChar; c++)
			chars[c - 32] = c;

		String			s = new String (chars);
		Rect			bounds = new Rect ();
		float[]			widths = new float[numCharInfos];
		Paint.FontMetricsInt	metrics = _paint.getFontMetricsInt ();

		_paint.getTextWidths (s, widths);

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
				// Reply length.
			io.writeInt (7 + numFontProperties * 2 + numCharInfos * 3);

				// Min bounds.
			io.writeShort ((short) 0);	// Left side bearing.
			io.writeShort ((short) 0);	// Right side bearing.
			io.writeShort ((short) _minWidth);	// Character width.
			io.writeShort ((short) 0);	// Ascent.
			io.writeShort ((short) 0);	// Descent.
			io.writeShort ((short) 0);	// Attributes.
			io.writePadBytes (4);	// Unused.

				// Max bounds.
			io.writeShort ((short) 0);	// Left side bearing.
			io.writeShort ((short) _maxWidth);	// Right side bearing.
			io.writeShort ((short) _maxWidth);	// Character width.
			io.writeShort ((short) -metrics.top);	// Ascent.
			io.writeShort ((short) metrics.bottom);	// Descent.
			io.writeShort ((short) 0);	// Attributes.
			io.writePadBytes (4);	// Unused.

			io.writeShort ((short) 32);	// Min char or byte2.
			io.writeShort ((short) _maxChar);	// Max char or byte2.
			io.writeShort ((short) 32);	// Default char.
			io.writeShort ((short) numFontProperties);
			io.writeByte ((byte) 0);	// Draw direction = left-to-right.
			io.writeByte ((byte) 0);	// Min byte 1.
			io.writeByte ((byte) 0);	// Max byte 1.
			io.writeByte ((byte) 0);	// All chars exist = false.
			io.writeShort ((short) -metrics.ascent);	// Font ascent.
			io.writeShort ((short) metrics.descent);	// Font descent.
			io.writeInt (numCharInfos);

				// If name atom is specified, write the FONT property.
			if (_nameAtom != null) {
				Atom		a = _xServer.findAtom ("FONT");

				io.writeInt (a.getId ());	// Name.
				io.writeInt (_nameAtom.getId ());	// Value.
			}

			for (int i = 0; i < numCharInfos; i++) {
				_paint.getTextBounds (s, i, i + 1, bounds);
				io.writeShort ((short) bounds.left);	// Left side bearing.
				io.writeShort ((short) bounds.right);	// Right side bearing.
				io.writeShort ((short) (widths[i]));	// Character width.
				io.writeShort ((short) -bounds.top);	// Ascent.
				io.writeShort ((short) bounds.bottom);	// Descent.
				io.writeShort ((short) 0);	// Attributes.
			}
		}
		io.flush ();
	}

	/**
	 * Process a QueryTextExtents request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param s		The string whose extents are being queried.
	 * @throws IOException
	 */
	private void
	processQueryTextExtentsRequest (
		InputOutput		io,
		int				sequenceNumber,
		String			s
	) throws IOException {
		Paint.FontMetricsInt	metrics = _paint.getFontMetricsInt ();
		int				width = (int) _paint.measureText (s);
		Rect			bounds = new Rect ();

		_paint.getTextBounds (s, 0, s.length (), bounds);

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt (0);	// Reply length.
			io.writeShort ((short) -metrics.ascent);	// Font ascent.
			io.writeShort ((short) metrics.descent);	// Font descent.
			io.writeShort ((short) -bounds.top);	// Overall ascent.
			io.writeShort ((short) bounds.bottom);	// Overall descent.
			io.writeInt (width);	// Overall width.
			io.writeInt (bounds.left);	// Overall left.
			io.writeInt (bounds.right);	// Overall right.
			io.writePadBytes (4);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Process a GetFontPath request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @throws IOException
	 */
	public static void
	processGetFontPath (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber
	) throws IOException {
		String[]	fontPaths = xServer.getFontPath ();
		int			numPaths = 0;
		int			length = 0;

		if (fontPaths != null)
			numPaths = fontPaths.length;

		for (int i = 0; i < numPaths; i++)
			length += fontPaths[i].length () + 1;
		
		int			pad = -length & 3;

		synchronized (io) {
			Util.writeReplyHeader (io, 0, sequenceNumber);
			io.writeInt ((length + pad) / 4);	// Reply length.
			io.writeShort ((short) numPaths);	// Number of STRs in path.
			io.writePadBytes (22);	// Unused.

			for (int i = 0; i < numPaths; i++) {
				byte[]		ba = fontPaths[i].getBytes ();

				io.writeByte ((byte) ba.length);
				io.writeBytes (ba, 0, ba.length);
			}
			io.writePadBytes (pad);	// Unused.
		}
		io.flush ();
	}

	/**
	 * Process a SetFontPath request.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processSetFontPath (
		XServer			xServer,
		InputOutput		io,
		int				sequenceNumber,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
												RequestCode.SetFontPath, 0);
			return;
		}

		int			numPaths = io.readShort ();	// Number of STRs in path.
		String[]	fontPaths = (numPaths > 0) ? new String[numPaths] : null;
		boolean		lengthError = false;

		io.readSkip (2);	// Unused.
		bytesRemaining -= 4;

		for (int i = 0; i < numPaths; i++) {
			if (bytesRemaining < 1) {
				lengthError = true;
				break;
			}

			int			length = io.readByte ();
			byte[]		ba = new byte[length];

			bytesRemaining--;
			if (bytesRemaining < length) {
				lengthError = true;
				break;
			}

			io.readBytes (ba, 0, length);
			bytesRemaining -= length + 1;
			fontPaths[i] = new String (ba);
		}

		if (bytesRemaining >= 4)
			lengthError = true;

		io.readSkip (bytesRemaining);
		if (lengthError)
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
												RequestCode.SetFontPath, 0);
		else
			xServer.setFontPath (fontPaths);
	}

	/**
	 * Does the font name match the pattern?
	 *
	 * @param idx	The index of the font being matched.
	 * @param pattern	The pattern being matched.
	 * @param pfields	The pattern, broken into its components.
	 * @return	The name of the matching font, or null if it doesn't match.
	 */
	private static String
	fontMatchesPattern (
		int			idx,
		String		pattern,
		String[]	pfields
	) {
		String		font = _allFonts[idx];

		if (pattern.equals ("*"))
			return font;

		String[]	fields;

		if (_allFontFields == null)
			_allFontFields = new String[_allFonts.length][];

		if (_allFontFields[idx] == null)
			fields = _allFontFields[idx] = font.split ("-");
		else
			fields = _allFontFields[idx];

		if (fields.length < pfields.length)
			return null;

		if (fields.length == 1)
			return pattern.equalsIgnoreCase (font) ? font : null;

		int			offset = 0;
		boolean		rescale = false;

		if (pfields[0].equals ("*"))
			offset = fields.length - pfields.length;

		for (int i = 0; i < pfields.length; i++) {
			if (pfields[i].equals ("*"))
				continue;

			int			foff = offset + i;

			if (foff == 0 || foff == 9 || foff == 10)
				continue;	// First field not used. And ignore resolution.
			else if (fields[foff].equalsIgnoreCase (pfields[i]))
				continue;
			// else if (fields[foff].matches (pfields[i]))
				// continue;	// Pattern matching.
			else if (foff >= 7 && foff <= 8)	// Pixel and point size.
				rescale = true;
			else
				return null;
		}

		if (rescale) {
			int			pixels = 0;
			int			points = 0;

			if (offset <= 7) {
				try {
					pixels = Integer.parseInt (pfields[7 - offset]);
				} catch (Exception e) {
				}
			}

			if (offset <= 8) {
				try {
					points = Integer.parseInt (pfields[8 - offset]);
				} catch (Exception e) {
				}
			}

			if (pixels == 0 && points == 0)
				return font;
			else if (pixels == 0 && points != 0)
				pixels = (int) Math.round (points * _dpi / 722.7);
			else if (pixels != 0 && points == 0)
				points = (int) Math.round (pixels * 722.7 / _dpi);

			return "-" + fields[1] + "-" + fields[2]
					+ "-" + fields[3] + "-" + fields[4]
					+ "-" + fields[5] + "-" + fields[6]
					+ "-" + pixels + "-" + points + "-" + _dpi + "-" + _dpi
					+ "-" + fields[11] + "-" + fields[12]
					+ "-" + fields[13] + "-" + fields[14];
		}

		return font;
	}

	/**
	 * Process a ListFonts or ListFontsWithInfo request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processListFonts (
		InputOutput		io,
		int				sequenceNumber,
		byte			opcode,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber, opcode, 0);
			return;
		}

		int			maxNames = io.readShort ();	// Max names.
		int			length = io.readShort ();	// Length of pattern.
		int			pad = -length & 3;

		bytesRemaining -= 4;
		if (bytesRemaining != length + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber, opcode, 0);
			return;
		}

		byte[]		bytes = new byte[length];

		io.readBytes (bytes, 0, length);	// Pattern.
		io.readSkip (pad);	// Unused.

		String			pattern = new String (bytes);
		String[]		pfields = pattern.split ("-");
		Vector<String>	fonts = new Vector<String>();

		for (int i = 0; i < _allFonts.length; i++) {
			String		f = fontMatchesPattern (i, pattern, pfields);

			if (f != null) {
				fonts.add (f);
				if (fonts.size () >= maxNames)
					break;
			}
		}

		if (opcode == RequestCode.ListFonts) {
			length = 0;
			for (String s: fonts)
				length += s.length () + 1;
			
			pad = -length & 3;

			synchronized (io) {
				Util.writeReplyHeader (io, 0, sequenceNumber);
				io.writeInt ((length + pad) / 4);	// Reply length.
				io.writeShort ((short) fonts.size ());	// Number of names.
				io.writePadBytes (22);	// Unused.

				for (String s: fonts) {
					byte[]		ba = s.getBytes ();

					io.writeByte ((byte) ba.length);
					io.writeBytes (ba, 0, ba.length);
				}

				io.writePadBytes (pad);	// Unused.
			}
			io.flush ();
		} else {
			int		remaining = fonts.size ();

			for (String s: fonts)
				writeFontWithInfo (io, sequenceNumber, s, remaining--);

				// Last in series indicator.
			synchronized (io) {
				Util.writeReplyHeader (io, 0, sequenceNumber);
				io.writeInt (7);	// Reply length.
				io.writePadBytes (52);	// Unused.
			}
			io.flush ();
		}
	}

	/**
	 * Write information about a named font.
	 * This is one of multiple replies to a ListFontsWithInfo request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param name	The name of the font.
	 * @param fontsRemaining	Number of replies before request is complete.
	 * @throws IOException
	 */
	private static void
	writeFontWithInfo (
		InputOutput		io,
		int				sequenceNumber,
		String			name,
		int				fontsRemaining
	) throws IOException {
		Font			font = new Font (0, null, null, name);
		int				numFontProperties = 0;
		int				nameLength = name.length ();
		int				pad = -nameLength & 3;
		Paint.FontMetricsInt	metrics = font._paint.getFontMetricsInt ();

		synchronized (io) {
			Util.writeReplyHeader (io, nameLength, sequenceNumber);
				// Reply length.
			io.writeInt (7 + numFontProperties * 2 + (nameLength + pad) / 4);

				// Min bounds.
			io.writeShort ((short) 0);	// Left side bearing.
			io.writeShort ((short) 0);	// Right side bearing.
			io.writeShort ((short) font._minWidth);	// Character width.
			io.writeShort ((short) 0);	// Ascent.
			io.writeShort ((short) 0);	// Descent.
			io.writeShort ((short) 0);	// Attributes.
			io.writePadBytes (4);	// Unused.

				// Max bounds.
			io.writeShort ((short) 0);	// Left side bearing.
			io.writeShort ((short) font._maxWidth);	// Right side bearing.
			io.writeShort ((short) font._maxWidth);	// Character width.
			io.writeShort ((short) -metrics.top);	// Ascent.
			io.writeShort ((short) metrics.bottom);	// Descent.
			io.writeShort ((short) 0);	// Attributes.
			io.writePadBytes (4);	// Unused.

			io.writeShort ((short) 32);	// Min char or byte2.
			io.writeShort ((short) font._maxChar);	// Max char or byte2.
			io.writeShort ((short) 32);	// Default char.
			io.writeShort ((short) numFontProperties);
			io.writeByte ((byte) 0);	// Draw direction = left-to-right.
			io.writeByte ((byte) 0);	// Min byte 1.
			io.writeByte ((byte) 0);	// Max byte 1.
			io.writeByte ((byte) 0);	// All chars exist = false.
			io.writeShort ((short) -metrics.ascent);	// Font ascent.
			io.writeShort ((short) metrics.descent);	// Font descent.
			io.writeInt (fontsRemaining);	// Replies hint.
					// No font properties.
			io.writeBytes (name.getBytes (), 0, nameLength);	// Name.
			io.writePadBytes (pad);	// Unused.
		}
		io.flush ();
	}
}