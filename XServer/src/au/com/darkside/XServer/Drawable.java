/**
 * This class implements an X drawable.
 */
package au.com.darkside.XServer;

import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;


/**
 * @author Matthew Kwan
 * 
 * This class implements an X drawable.
 */
public class Drawable {
	private final Bitmap		_bitmap;
	private final Canvas		_canvas;
	private final int			_depth;

	/**
	 * Constructor.
	 *
	 * @param width		The drawable width.
	 * @param height	The drawable height.
	 * @param depth		The drawable depth.
	 * @param color		Fill the bitmap with this color.
	 */
	public Drawable (
		int			width,
		int			height,
		int			depth,
		int			color
	) {
		_bitmap = Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);
		_canvas = new Canvas (_bitmap);
		_depth = depth;
		_bitmap.eraseColor (color);
	}

	/**
	 * Return the drawable's width.
	 *
	 * @return	The drawable's width.
	 */
	public int
	getWidth () {
		return _bitmap.getWidth ();
	}

	/**
	 * Return the drawable's height.
	 *
	 * @return	The drawable's height.
	 */
	public int
	getHeight () {
		return _bitmap.getHeight ();
	}

	/**
	 * Return the drawable's depth.
	 *
	 * @return	The drawable's depth.
	 */
	public int
	getDepth () {
		return _depth;
	}

	/**
	 * Return the drawable's bitmap.
	 *
	 * @return	The drawable's bitmap.
	 */
	public Bitmap
	getBitmap () {
		return _bitmap;
	}

	/**
	 * Process an X request relating to this drawable.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param id	The ID of the pixmap or window using this drawable.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param arg		Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the drawable has been changed.
	 * @throws IOException
	 */
	public boolean
	processRequest (
		XServer			xServer,
		InputOutput		io,
		int				id,
		int				sequenceNumber,
		byte			opcode,
		int				arg,
		int				bytesRemaining
	) throws IOException {
		boolean			changed = false;

		switch (opcode) {
			case RequestCode.CopyArea:
				if (bytesRemaining != 20) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			did = io.readInt ();	// Dest drawable.
					int			gcid = io.readInt ();	// GC.
					short		sx = (short) io.readShort ();	// Src X.
					short		sy = (short) io.readShort ();	// Src Y.
					short		dx = (short) io.readShort ();	// Dst X.
					short		dy = (short) io.readShort ();	// Dst Y.
					int			width = io.readShort ();	// Width.
					int			height = io.readShort ();	// Height.
					Resource	r1 = xServer.getResource (did);
					Resource	r2 = xServer.getResource (gcid);

					if (r1 == null || !r1.isDrawable ()) {
						ErrorCode.write (io, ErrorCode.Drawable,
												sequenceNumber, opcode, did);
					} else if (r2 == null
									|| r2.getType () != Resource.GCONTEXT) {
						ErrorCode.write (io, ErrorCode.GContext,
												sequenceNumber, opcode, gcid);
					} else if (width > 0 && height > 0){
						copyArea (io, sequenceNumber, sx, sy, width, height,
												r1, dx, dy, (GContext) r2);
					}
				}
				break;
			case RequestCode.CopyPlane:
				if (bytesRemaining != 24) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, id);
				} else {
					int			did = io.readInt ();	// Dest drawable.
					int			gcid = io.readInt ();	// GC.
					short		sx = (short) io.readShort ();	// Src X.
					short		sy = (short) io.readShort ();	// Src Y.
					short		dx = (short) io.readShort ();	// Dst X.
					short		dy = (short) io.readShort ();	// Dst Y.
					int			width = io.readShort ();	// Width.
					int			height = io.readShort ();	// Height.
					int			bitPlane = io.readInt ();	// Bit plane.
					Resource	r1 = xServer.getResource (did);
					Resource	r2 = xServer.getResource (gcid);

					if (r1 == null || !r1.isDrawable ()) {
						ErrorCode.write (io, ErrorCode.Drawable,
												sequenceNumber, opcode, did);
					} else if (r2 == null
									|| r2.getType () != Resource.GCONTEXT) {
						ErrorCode.write (io, ErrorCode.GContext,
												sequenceNumber, opcode, gcid);
					} else {
						if (_depth != 32)
							copyPlane (io, sequenceNumber, sx, sy, width,
										height, bitPlane, r1, dx, dy,
										(GContext) r2);
						else
							copyArea (io, sequenceNumber, sx, sy, width,
										height, r1, dx, dy, (GContext) r2);
					}
				}
				break;
			case RequestCode.GetImage:
				if (bytesRemaining != 12) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					processGetImageRequest (io, sequenceNumber, arg);
				}
				break;
			case RequestCode.QueryBestSize:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			width = io.readShort ();	// Width.
					int			height = io.readShort ();	// Height.

					synchronized (io) {
						Util.writeReplyHeader (io, 0, sequenceNumber);
						io.writeInt (0);	// Reply length.
						io.writeShort ((short) width);	// Width.
						io.writeShort ((short) height);	// Height.
						io.writePadBytes (20);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.PolyPoint:
			case RequestCode.PolyLine:
			case RequestCode.PolySegment:
			case RequestCode.PolyRectangle:
			case RequestCode.PolyArc:
			case RequestCode.FillPoly:
			case RequestCode.PolyFillRectangle:
			case RequestCode.PolyFillArc:
			case RequestCode.PutImage:
			case RequestCode.PolyText8:
			case RequestCode.PolyText16:
			case RequestCode.ImageText8:
			case RequestCode.ImageText16:
				if (bytesRemaining < 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			gcid = io.readInt ();	// GContext.
					Resource	r = xServer.getResource (gcid);

					bytesRemaining -= 4;
					if (r == null || r.getType () != Resource.GCONTEXT) {
						io.readSkip (bytesRemaining);
						ErrorCode.write (io, ErrorCode.GContext,
												sequenceNumber, opcode, 0);

					} else {
						changed = processGCRequest (xServer, io, id,
								(GContext) r, sequenceNumber, opcode, arg,
								bytesRemaining);
					}
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}

		return changed;
	}

	/**
	 * Process a GetImage request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param format	1=XYPixmap, 2=ZPixmap.
	 * @throws IOException
	 */
	private void
	processGetImageRequest (
		InputOutput	io,
		int			sequenceNumber,
		int			format
	) throws IOException {
		short		x = (short) io.readShort ();	// X.
		short		y = (short) io.readShort ();	// Y.
		int			width = io.readShort ();	// Width.
		int			height = io.readShort ();	// Height.
		int			planeMask = io.readInt ();	// Plane mask.
		int			wh = width * height;
		int			n, pad;
		int[]		pixels = new int[wh];
		byte[]		bytes = null;

		_bitmap.getPixels (pixels, 0, width, x, y, width, height);

		if (format == 2)	// ZPixmap.
			n = wh * 3;
		else {	// XYPixmap.
			int			planes = Util.bitcount (planeMask);
			int			rightPad = -width & 7;
			int			xmax = width + rightPad;
			int			offset = 0;

			n = planes * height * (width + rightPad) / 8;
			bytes = new byte[n];

			for (int plane = 0; plane < 32; plane++) {
				if ((plane & planeMask) == 0)
					continue;

				byte		b = 0;

				for (int yi = 0; yi < height; yi++) {
					for (int xi = 0; xi < xmax; xi++) {
						b <<= 1;
						if (xi < width
								&& (pixels[yi * width + xi] & planeMask) != 0)
							b |= 1;

						if ((xi & 7) == 7) {
							bytes[offset++] = b;
							b = 0;
						}
					}
				}
			}
		}

		pad = -n & 3;

		synchronized (io) {
			Util.writeReplyHeader (io, 32, sequenceNumber);
			io.writeInt ((n + pad) / 4);	// Reply length.
			io.writeInt (0);	// Visual ID.
			io.writePadBytes (20);	// Unused.

			if (format == 2) {
				for (int i = 0; i < wh; i++) {
					n = pixels[i] & planeMask;
					io.writeByte ((byte) (n & 0xff));
					io.writeByte ((byte) ((n >> 8) & 0xff));
					io.writeByte ((byte) ((n >> 16) & 0xff));
				}
			} else {
				io.writeBytes (bytes, 0, n);
			}

			io.writePadBytes (pad);	// Unused.
		}
		io.flush ();

	}
	/**
	 * Clear a rectangular region of the drawable.
	 *
	 * @param x	X coordinate of the rectangle.
	 * @param y	Y coordinate of the rectangle.
	 * @param width	Width of the rectangle.
	 * @param height	Height of the rectangle.
	 * @param background	The color to draw in the cleared area.
	 */
	public void
	clearArea (
		int			x,
		int			y,
		int			width,
		int			height,
		int			background
	) {
		Rect		r = new Rect (x, y, x + width, y + height);
		Paint		paint = new Paint ();

		paint.setColor (background);
		paint.setStyle (Paint.Style.FILL);
		_canvas.drawRect (r, paint);
	}

	/**
	 * Copy a rectangle from this drawable to another.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param sx	X coordinate of this rectangle.
	 * @param sy	Y coordinate of this rectangle.
	 * @param width	Width of the rectangle.
	 * @param height	Height of the rectangle.
	 * @param dr	The pixmap or window to draw the rectangle in.
	 * @param dx	The destination X coordinate.
	 * @param dy	The destination Y coordinate.
	 * @param gc	The GContext.
	 * @throws IOException
	 */
	private void
	copyArea (
		InputOutput	io,
		int			sequenceNumber,
		int			sx,
		int			sy,
		int			width,
		int			height,
		Resource	dr,
		int			dx,
		int			dy,
		GContext	gc
	) throws IOException {
		Drawable	dst;

		if (dr.getType () == Resource.PIXMAP)
			dst = ((Pixmap) dr).getDrawable ();
		else
			dst = ((Window) dr).getDrawable ();

		Bitmap		bm = Bitmap.createBitmap (_bitmap, sx, sy, width, height);

		dst._canvas.drawBitmap (bm, dx, dy, gc.getPaint ());

		if (dr.getType () == Resource.WINDOW)
			((Window) dr).invalidate (dx, dy, width, height);

		if (gc.getGraphicsExposure ())
			EventCode.sendNoExposure (dr.getClientComms (), dr,
													RequestCode.CopyArea);
	}

	/**
	 * Copy a rectangle from a plane of this drawable to another rectangle.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param sx	X coordinate of this rectangle.
	 * @param sy	Y coordinate of this rectangle.
	 * @param width	Width of the rectangle.
	 * @param height	Height of the rectangle.
	 * @param bitPlane	The bit plane being copied.
	 * @param dr	The pixmap or window to draw the rectangle in.
	 * @param dx	The destination X coordinate.
	 * @param dy	The destination Y coordinate.
	 * @param gc	The GContext.
	 * @throws IOException
	 */
	private void
	copyPlane (
		InputOutput	io,
		int			sequenceNumber,
		int			sx,
		int			sy,
		int			width,
		int			height,
		int			bitPlane,
		Resource	dr,
		int			dx,
		int			dy,
		GContext	gc
	) throws IOException {
		Drawable	dst;

		if (dr.getType () == Resource.PIXMAP)
			dst = ((Pixmap) dr).getDrawable ();
		else
			dst = ((Window) dr).getDrawable ();

		int			fg = (_depth == 1) ? 0xffffffff : gc.getForegroundColor ();
		int			bg = (_depth == 1) ? 0 : gc.getBackgroundColor ();
		int[]		pixels = new int [width * height];

		_bitmap.getPixels (pixels, 0, width, sx, sy, width, height);
		for (int i = 0; i < pixels.length; i++)
			pixels[i] = ((pixels[i] & bitPlane) != 0) ? fg : bg;

		dst._canvas.drawBitmap (pixels, 0, width, dx, dy, width, height,
												true, gc.getPaint ());

		if (dr.getType () == Resource.WINDOW)
			((Window) dr).invalidate (dx, dy, width, height);

		if (gc.getGraphicsExposure ())
			EventCode.sendNoExposure (dr.getClientComms(), dr,
													RequestCode.CopyPlane);
	}

	/**
	 * Draw text at the specified location, on top of a bounding rectangle
	 * drawn in the background color.
	 *
	 * @param s	The string to write.
	 * @param x	X coordinate.
	 * @param y	Y coordinate.
	 * @param gc	Graphics context for drawing the text.
	 */
	private void
	drawImageText (
		String		s,
		int			x,
		int			y,
		GContext	gc
	) {
		Paint		paint = gc.getPaint ();
		Font		font = gc.getFont ();
		Rect		rect = new Rect ();

		font.getTextBounds (s, x, y, rect);
		paint.setColor (gc.getBackgroundColor ());
		paint.setStyle (Paint.Style.FILL);
		_canvas.drawRect (rect, paint);
		
		paint.setColor (gc.getForegroundColor ());
		_canvas.drawText (s, x, y, paint);
	}

	/**
	 * Process an X request relating to this drawable using the
	 * GContext provided.
	 *
	 * @param xServer	The X server.
	 * @param io	The input/output stream.
	 * @param id	The ID of the pixmap or window using this drawable.
	 * @param gc	The GContext to use for drawing.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param arg		Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the drawable is modified.
	 * @throws IOException
	 */
	public boolean
	processGCRequest (
		XServer			xServer,
		InputOutput		io,
		int				id,
		GContext		gc,
		int				sequenceNumber,
		byte			opcode,
		int				arg,
		int				bytesRemaining
	) throws IOException {
		Paint			paint = gc.getPaint ();
		boolean			changed = false;
		int				originalColor = paint.getColor ();

		_canvas.save ();
		gc.applyClipRectangles (_canvas);

		if (_depth == 1)
			paint.setColor (0xffffffff);

		switch (opcode) {
			case RequestCode.PolyPoint:
				if ((bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					float[]		points = new float[bytesRemaining / 2];
					int			i = 0;

					while (bytesRemaining > 0) {
						float		p = (short) io.readShort ();

						bytesRemaining -= 2;
						if (arg == 0 || i < 2)	// Relative to origin.
							points[i] = p;
						else
							points[i] = points[i - 2] + p;	// Rel to previous.
						i++;
					}

					_canvas.drawPoints (points, paint);
					changed = true;
				}
				break;
			case RequestCode.PolyLine:
				if ((bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					Path		path = new Path ();
					int			i = 0;

					while (bytesRemaining > 0) {
						float		x = (short) io.readShort ();
						float		y = (short) io.readShort ();

						bytesRemaining -= 4;
						if (i == 0)
							path.moveTo (x, y);
						else if (arg == 0)	// Relative to origin.
							path.lineTo (x, y);
						else	// Relative to previous.
							path.rLineTo (x, y);
						i++;
					}
					paint.setStyle (Paint.Style.STROKE);
					_canvas.drawPath (path, paint);
					changed = true;
				}
				break;
			case RequestCode.PolySegment:
				if ((bytesRemaining & 7) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					float[]		points = new float[bytesRemaining / 2];
					int			i = 0;

					while (bytesRemaining > 0) {
						points[i++] = (short) io.readShort ();
						bytesRemaining -= 2;
					}

					_canvas.drawLines (points, paint);
					changed = true;
				}
				break;
			case RequestCode.PolyRectangle:
			case RequestCode.PolyFillRectangle:
				if ((bytesRemaining & 7) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					if (opcode == RequestCode.PolyRectangle)
						paint.setStyle (Paint.Style.STROKE);
					else
						paint.setStyle (Paint.Style.FILL);

					while (bytesRemaining > 0) {
						float		x = (short) io.readShort ();
						float		y = (short) io.readShort ();
						float		width = io.readShort ();
						float		height = io.readShort ();

						bytesRemaining -= 8;
						_canvas.drawRect (x, y, x + width, y + height, paint);
						changed = true;
					}
				}
				break;
			case RequestCode.FillPoly:
				if (bytesRemaining < 4 || (bytesRemaining & 3) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					io.readByte ();		// Shape.

					int			mode = io.readByte ();	// Coordinate mode.
					Path		path = new Path ();
					int			i = 0;

					io.readSkip (2);	// Unused.
					bytesRemaining -= 4;

					while (bytesRemaining > 0) {
						float		x = (short) io.readShort ();
						float		y = (short) io.readShort ();

						bytesRemaining -= 4;
						if (i == 0)
							path.moveTo (x, y);
						else if (mode == 0)	// Relative to origin.
							path.lineTo (x, y);
						else	// Relative to previous.
							path.rLineTo (x, y);
						i++;
					}

					path.close ();
					path.setFillType (gc.getFillType ());
					paint.setStyle (Paint.Style.FILL);
					_canvas.drawPath (path, paint);
					changed = true;
				}
				break;
			case RequestCode.PolyArc:
			case RequestCode.PolyFillArc:
				if ((bytesRemaining % 12) != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					boolean		useCenter = false;

					if (opcode == RequestCode.PolyArc) {
						paint.setStyle (Paint.Style.STROKE);
					} else {
						paint.setStyle (Paint.Style.FILL);
						if (gc.getArcMode () == 1)		// Pie slice.
							useCenter = true;
					}

					while (bytesRemaining > 0) {
						float		x = (short) io.readShort ();
						float		y = (short) io.readShort ();
						float		width = io.readShort ();
						float		height = io.readShort ();
						float		angle1 = (short) io.readShort ();
						float		angle2 = (short) io.readShort ();
						RectF		r = new RectF (x, y, x + width, y + height);

						bytesRemaining -= 12;
						_canvas.drawArc (r, angle1 / -64.0f, angle2 / -64.0f,
														useCenter, paint);
						changed = true;
					}
				}
				break;
			case RequestCode.PutImage:
				changed = processPutImage (io, sequenceNumber, gc, arg,
															bytesRemaining);
				break;
			case RequestCode.PolyText8:
			case RequestCode.PolyText16:
				changed = processPolyText (io, sequenceNumber, gc, opcode,
															bytesRemaining);
				break;
			case RequestCode.ImageText8:
				if (bytesRemaining != 4 + arg + (-arg & 3)) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			x = (short) io.readShort ();
					int			y = (short) io.readShort ();
					int			pad = -arg & 3;
					byte[]		bytes = new byte[arg];

					io.readBytes (bytes, 0, arg);
					io.readSkip (pad);
					drawImageText (new String (bytes), x, y, gc);
					changed = true;
				}
				break;
			case RequestCode.ImageText16:
				if (bytesRemaining != 4 + 2 * arg + (-(2 * arg) & 3)) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			x = (short) io.readShort ();
					int			y = (short) io.readShort ();
					int			pad = (-2 * arg) & 3;
					char[]		chars = new char[arg];

					for (int i = 0; i < arg; i++) {
						int			b1 = io.readByte ();
						int			b2 = io.readByte ();

						chars[i] = (char) ((b1 << 8) | b2);
					}

					io.readSkip (pad);
					drawImageText (new String (chars), x, y, gc);
					changed = true;
				}
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}

		if (_depth == 1)
			paint.setColor (originalColor);

		_canvas.restore ();		// Undo any clip rectangles.

		return changed;
	}

	/**
	 * Process a PutImage request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param gc	The GContext to use for drawing.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the drawable is modified.
	 * @throws IOException
	 */
	private boolean
	processPutImage (
		InputOutput		io,
		int				sequenceNumber,
		GContext		gc,
		int				format,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 12) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
													RequestCode.PutImage, 0);
			return false;
		}

		int			width = io.readShort ();
		int			height = io.readShort ();
		float		dstX = (short) io.readShort ();
		float		dstY = (short) io.readShort ();
		int			leftPad = io.readByte ();
		int			depth = io.readByte ();
		int			n, pad, rightPad;

		io.readSkip(2);		// Unused.
		bytesRemaining -= 12;

		if (format == 2) {
			rightPad = 0;
			n = 3 * width * height;
		} else {
			rightPad = -(width + leftPad) & 7;
			n = (width + leftPad + rightPad) * height * depth / 8;
		}
		pad = -n & 3;

		if (bytesRemaining != n + pad) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
													RequestCode.PutImage, 0);
			return false;
		}

		boolean		badMatch = false;

		if (format == 0) {	// Bitmap.
			if (depth != 1)
				badMatch = true;
		} else if (format == 1) {	// XYPixmap.
			if (depth != 1 && depth != 32)
				badMatch = true;
		} else if (format == 2) {	// ZPixmap.
			if (depth != 32 || leftPad != 0)
				badMatch = true;
		} else {	// Invalid format.
			badMatch = true;
		}

		if (badMatch) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Match, sequenceNumber,
													RequestCode.PutImage, 0);
			return false;
		}

		int[]		colors = new int[width * height];

		if (depth == 1) {	// Bitmap.
			int			fg = gc.getForegroundColor ();
			int			bg = gc.getBackgroundColor ();
			int			offset = 0;
			int			count = 0;
			int			x = 0;
			int			y = 0;
			int			mask = 128;
			int			val = 0;

			for (;;) {
				if ((count++ & 7) == 0) {
					val = io.readByte ();
					mask = 128;
				}

				if (x >= leftPad && x < leftPad + width)
					colors[offset++] = ((val & mask) == 0) ? bg : fg;

				mask >>= 1;
				if (++x == leftPad + width + rightPad) {
					x = 0;
					if (++y == height)
						break;
				}
			}
		} else if (format == 1) {	// 32-bit XYPixmap.
			int			planeBit = 1 << (depth - 1);

			for (int i = 0; i < depth; i++) {
				int			offset = 0;
				int			count = 0;
				int			x = 0;
				int			y = 0;
				int			mask = 128;
				int			val = 0;
	
				for (;;) {
					if ((count++ & 7) == 0) {
						val = io.readByte ();
						mask = 128;
					}

					if (x >= leftPad && x < leftPad + width)
						colors[offset++] |= ((val & mask) == 0) ? 0 : planeBit;
	
					mask >>= 1;
					if (++x == leftPad + width + rightPad) {
						x = 0;
						if (++y == height)
							break;
					}
				}
			}

			planeBit >>= 1;
		} else {	// 32-bit ZPixmap.
			for (int i = 0; i < colors.length; i++) {
				int			b = io.readByte ();
				int			g = io.readByte ();
				int			r = io.readByte ();

				colors[i] = 0xff000000 | (r << 16) | (g << 8) | b;
			}
		}

		io.readSkip (pad);
		_canvas.drawBitmap (Bitmap.createBitmap (colors, width, height,
						Bitmap.Config.ARGB_8888), dstX, dstY, gc.getPaint ());

		return true;
	}

	/**
	 * Process a PolyText8 or PolyText16 request.
	 *
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param gc	The GContext to use for drawing.
	 * @param opcode	The request's opcode.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @return	True if the drawable is modified.
	 * @throws IOException
	 */
	private boolean
	processPolyText (
		InputOutput		io,
		int				sequenceNumber,
		GContext		gc,
		byte			opcode,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining < 4) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
													opcode, 0);
			return false;
		}

		float		x = (short) io.readShort ();
		float		y = (short) io.readShort ();

		bytesRemaining -= 4;
		while (bytesRemaining > 1) {
			int			length = io.readByte ();
			int			minBytes;

			bytesRemaining--;
			if (length == 255)		// Font change indicator.
				minBytes = 4;
			else if (opcode == RequestCode.PolyText8)
				minBytes = 1 + length;
			else
				minBytes = 1 + length * 2;

			if (bytesRemaining < minBytes) {
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				return false;
			}

			if (length == 255) {	// Font change indicator.
				int			fid = 0;

				for (int i = 0; i < 4; i++)
					fid = (fid << 8) | io.readByte ();

				bytesRemaining -= 4;
				if (!gc.setFont (fid))
					ErrorCode.write (io, ErrorCode.Font, sequenceNumber,
																opcode, fid);
			} else {	// It's a string.
				int			delta = io.readByte ();
				String		s;

				bytesRemaining--;
				if (opcode == RequestCode.PolyText8) {
					byte[]		bytes = new byte[length];

					io.readBytes (bytes, 0, length);
					bytesRemaining -= length;
					s = new String (bytes);
				} else {
					char[]		chars = new char[length];

					for (int i = 0; i < length; i++) {
						int			b1 = io.readByte ();
						int			b2 = io.readByte ();

						chars[i] = (char) ((b1 << 8) | b2);
					}

					bytesRemaining -= length * 2;
					s = new String (chars);
				}

				Paint		paint = gc.getPaint ();

				x += delta;
				_canvas.drawText (s, x, y, paint);
				x += paint.measureText (s);
			}
		}
		io.readSkip (bytesRemaining);

		return true;
	}
}
