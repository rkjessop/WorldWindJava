/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 * 
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 * 
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 * 
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */

package gov.nasa.worldwind.render;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.drag.*;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.util.*;

import com.jogamp.opengl.GL2;
import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;

/**
 * Renders a string of text on the surface of the globe. The text will appear draped over terrain. Surface text is drawn
 * at a constant geographic size: it will appear larger when the view zooms in on the text and smaller when the view
 * zooms out.
 *
 * @author pabercrombie
 * @version $Id: SurfaceText.java 3092 2015-05-14 22:21:32Z tgaskins $
 */
// TODO: add support for heading
public class SurfaceText extends AbstractSurfaceObject implements GeographicText, Movable, Draggable
{
    /** Default text size. */
    public final static double DEFAULT_TEXT_SIZE_IN_METERS = 1000;
    /** Default font. */
    public static final Font DEFAULT_FONT = Font.decode("Arial-BOLD-24");
    /** Default text color. */
    public static final Color DEFAULT_COLOR = Color.WHITE;
    /** Default offset. The default offset centers the text on its geographic position both horizontally and vertically. */
    public static final Offset DEFAULT_OFFSET = new Offset(-0.5d, -0.5d, AVKey.FRACTION, AVKey.FRACTION);

    /** The text to draw. */
    protected CharSequence text;
    /** Location at which to draw the text. */
    protected Position location;
    /** The angle of text rotation from the true north (clockwise). */
    protected Angle heading = Angle.ZERO;
    /** The height of the text in meters. */
    protected double textSizeInMeters = DEFAULT_TEXT_SIZE_IN_METERS;
    /** Dragging Support */
    protected boolean dragEnabled = true;
    protected DraggableSupport draggableSupport = null;

    /** Font to use to draw the text. Defaults to {@link #DEFAULT_FONT}. */
    protected Font font = DEFAULT_FONT;
    /** Color to use to draw the text. Defaults to {@link #DEFAULT_COLOR}. */
    protected Color color = DEFAULT_COLOR;
    /** Background color for the text. By default color will be generated to contrast with the text color. */
    protected Color bgColor;
    /** Text priority. Can be used to implement text culling. */
    protected double priority;
    /** Offset that specifies where to place the text in relation to it's geographic position. */
    protected Offset offset = DEFAULT_OFFSET;

    // Computed each time text is rendered
    /** Bounds of the text in pixels. */
    protected Rectangle2D textBounds;
    /** Geographic size of a pixel. */
    protected double pixelSizeInMeters;
    /** Scaling factor applied to the text to maintain a constant geographic size. */
    protected double scale;

    /**
     * The lower-left location of the text box after applying offset.
     */
    protected LatLon drawLocation;

    /**
     * Indicates whether this text spans the dateline.
     */
    protected boolean spansAntimeridian = false;

    /**
     * Create a new surface text object.
     *
     * @param text     Text to draw.
     * @param position Geographic location at which to draw the text.
     */
    public SurfaceText(String text, Position position)
    {
        this.setText(text);
        this.setPosition(position);
    }

    /**
     * Create a new surface text object.
     *
     * @param text     Text to draw.
     * @param position Geographic location at which to draw the text.
     * @param font     Font to use when drawing text.
     * @param color    Color to use when drawing text.
     */
    public SurfaceText(String text, Position position, Font font, Color color)
    {
        this.setText(text);
        this.setPosition(position);
        this.setFont(font);
        this.setColor(color);
    }

    /** {@inheritDoc} */
    public CharSequence getText()
    {
        return this.text;
    }

    /** {@inheritDoc} */
    public void setText(CharSequence text)
    {
        if (text == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.text = text;
        this.textBounds = null; // Need to recompute bounds
        this.onShapeChanged();
    }

    /** {@inheritDoc} */
    public Position getPosition()
    {
        return this.location;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The offset determines how the text is placed relative to this position. The default offset centers the text on
     * the position both horizontally and vertically.
     *
     * @see #setOffset(Offset)
     */
    public void setPosition(Position position)
    {
        if (position == null)
        {
            String message = Logging.getMessage("nullValue.LatLonIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.location = position;
        this.onShapeChanged();
    }

    /** {@inheritDoc} */
    public Angle getHeading()
    {
        return this.heading;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The angle of text rotation from the true north (clockwise)
     */
    public void setHeading(Angle heading)
    {
        if (heading == null)
        {
            String message = Logging.getMessage("nullValue.HeadingIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.heading = heading;
        this.onShapeChanged();
    }

    /** {@inheritDoc} */
    public Font getFont()
    {
        return this.font;
    }

    /** {@inheritDoc} */
    public void setFont(Font font)
    {
        if (font == null)
        {
            String message = Logging.getMessage("nullValue.FontIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // Only set the font if it is different than the active font
        if (!font.equals(this.font))
        {
            this.font = font;
            this.textBounds = null; // Need to recompute bounds
            this.onShapeChanged();
        }
    }

    /** {@inheritDoc} */
    public Color getColor()
    {
        return this.color;
    }

    /** {@inheritDoc} */
    public void setColor(Color color)
    {
        if (color == null)
        {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (!color.equals(this.color))
        {
            this.color = color;
            this.onShapeChanged();
        }
    }

    /** {@inheritDoc} */
    public Color getBackgroundColor()
    {
        return this.bgColor;
    }

    /** {@inheritDoc} */
    public void setBackgroundColor(Color background)
    {
        if (background == null)
        {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (bgColor == null || !bgColor.equals(background))
        {
            this.bgColor = background;
            this.onShapeChanged();
        }
    }

    /** {@inheritDoc} */
    public void setPriority(double priority)
    {
        this.priority = priority;
    }

    /** {@inheritDoc} */
    public double getPriority()
    {
        return this.priority;
    }

    /**
     * Returns the text offset. The offset determines how to position the text relative to its geographic position.
     *
     * @return the text offset.
     *
     * @see #setOffset(Offset)
     */
    public Offset getOffset()
    {
        return this.offset;
    }

    /**
     * Specifies a location relative to the label position at which to align the label. The label text begins at the
     * point indicated by the offset. An offset of (0, 0) aligns the left baseline of the text with the position. An
     * offset of (-0.5, -0.5) fraction aligns the center of the text with the position.
     * <p>
     * A pixel based offset is interpreted based on the geographic size of the text. For example, if the text rendered
     * "normally" in two dimensions would be 20 pixels tall, and the geographic text is 100 meters tall, then each pixel
     * of text corresponds to 5 meters. So an offset of 2 pixels would correspond to a geographic offset of 10 meters.
     *
     * @param offset Offset that controls where to position the label relative to its geographic location.
     */
    public void setOffset(Offset offset)
    {
        if (offset == null)
        {
            String message = Logging.getMessage("nullValue.OffsetIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (!offset.equals(this.offset))
        {
            this.offset = offset;
            this.onShapeChanged();
        }
    }

    public double getTextSize()
    {
        return this.textSizeInMeters;
    }

    public void setTextSize(double meters)
    {
        this.textSizeInMeters = meters;
    }

    /** {@inheritDoc} */
    @Override
    public void preRender(DrawContext dc)
    {
        if (this.textBounds == null)
        {
            this.updateTextBounds(dc);
        }

        super.preRender(dc);
    }

    /** {@inheritDoc} */
    public Position getReferencePosition()
    {
        return new Position(this.location, 0);
    }

    /** {@inheritDoc} */
    public void move(Position position)
    {
        Position refPos = this.getReferencePosition();
        if (refPos == null)
            return;

        this.moveTo(refPos.add(position));
    }

    /** {@inheritDoc} */
    public void moveTo(Position position)
    {
        this.setPosition(position);
    }

    @Override
    public boolean isDragEnabled()
    {
        return this.dragEnabled;
    }

    @Override
    public void setDragEnabled(boolean enabled)
    {
        this.dragEnabled = enabled;
    }

    @Override
    public void drag(DragContext dragContext)
    {
        if (!this.dragEnabled)
            return;

        if (this.draggableSupport == null)
            this.draggableSupport = new DraggableSupport(this, WorldWind.CLAMP_TO_GROUND);

        this.doDrag(dragContext);
    }

    protected void doDrag(DragContext dragContext)
    {
        this.draggableSupport.dragGlobeSizeConstant(dragContext);
    }

    /** {@inheritDoc} */
    public java.util.List<Sector> getSectors(DrawContext dc)
    {
        if (dc == null)
        {
            String message = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        return Arrays.asList(this.computeSector(dc));
    }

    /** {@inheritDoc} */
    protected void drawGeographic(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        OGLStackHandler ogsh = new OGLStackHandler();
        ogsh.pushAttrib(gl,
            GL2.GL_CURRENT_BIT       // For current color (used by JOGL TextRenderer).
                | GL2.GL_TRANSFORM_BIT); // For matrix mode.
        ogsh.pushModelview(gl);
        try
        {
            this.computeGeometry(dc, sdc);

            if (this.isSmall())
                return;

            this.applyDrawTransform(dc, sdc);
            this.drawText(dc);
        }
        finally
        {
            ogsh.pop(gl);
        }
    }

    /**
     * Draw the text.
     *
     * @param dc Current draw context.
     */
    protected void drawText(DrawContext dc)
    {
        TextRenderer tr = this.getTextRenderer(dc);
        try
        {
            tr.begin3DRendering();

            Color bgColor = this.determineBackgroundColor(this.color);
            CharSequence text = this.getText();

            tr.setColor(bgColor);
            tr.draw(text, 1, -1);
            tr.setColor(this.getColor());
            tr.draw(text, 0, 0);
        }
        finally
        {
            tr.end3DRendering();
        }
    }

    /**
     * Compute the text size and position.
     *
     * @param dc  Current draw context.
     * @param sdc Current surface tile draw context.
     */
    protected void computeGeometry(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        // Determine the geographic size of a pixel in the tile
        this.pixelSizeInMeters = this.computePixelSize(dc, sdc);

        // Determine how big the text would be without scaling
        double fullHeightInMeters = this.pixelSizeInMeters * this.textBounds.getHeight();

        // Calculate a scale to make the text the size we want (a constant geographic size)
        this.scale = this.textSizeInMeters / fullHeightInMeters;
    }

    /**
     * Apply a transform to the GL state to draw the text at the proper location and scale.
     *
     * @param dc  Current draw context.
     * @param sdc Current surface tile draw context.
     */
    protected void applyDrawTransform(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        Vec4 point = new Vec4(this.location.getLongitude().degrees, this.location.getLatitude().degrees, 1);
        // If the text box spans the anti-meridian and we're drawing tiles to the right of the anti-meridian, then we
        // need to map the translation into coordinates relative to that side of the anti-meridian.
        if (this.spansAntimeridian &&
            Math.signum(sdc.getSector().getMinLongitude().degrees) != Math.signum(this.drawLocation.longitude.degrees)) {
            point = new Vec4(this.location.getLongitude().degrees - 360, this.location.getLatitude().degrees, 1);
        }
        point = point.transformBy4(sdc.getModelviewMatrix());

        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

        // Translate to location point
        gl.glTranslated(point.x(), point.y(), point.z());

        // Apply the scaling factor to draw the text at the correct geographic size
        gl.glScaled(this.scale, this.scale, 1d);
        
        double widthInPixels = this.textBounds.getWidth();
        double heightInPixels = this.textBounds.getHeight();
        
        Point2D textDimensions = getRotatedTextDimensions();
        double rotatedPixelHeight = textDimensions.getY();
        double rotatedPixelWidth = textDimensions.getX();
        
        Point2D textOffset = getOffset().computeOffset(rotatedPixelWidth, rotatedPixelHeight, null, null);
        
        // Move to offset position.
        gl.glTranslated(rotatedPixelWidth / 2.0 + textOffset.getX(), rotatedPixelHeight / 2.0 + textOffset.getY(), 0);

        // Apply rotation angle from text center.
        gl.glRotated(-this.heading.degrees, 0, 0, 1);

        // Move to text center.
        gl.glTranslated(-widthInPixels / 2.0, -heightInPixels / 2.0, 0);
    }

    /**
     * Determine if the text is too small to draw.
     *
     * @return {@code true} if the height of the text is less than one pixel.
     */
    protected boolean isSmall()
    {
        return this.scale * this.textSizeInMeters < this.pixelSizeInMeters;
    }

    /**
     * Compute the size of a pixel in the surface tile.
     *
     * @param dc  Current draw context.
     * @param sdc Current surface tile draw context.
     *
     * @return The size of a tile pixel in meters.
     */
    protected double computePixelSize(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        return dc.getGlobe().getRadius() * sdc.getSector().getDeltaLatRadians() / sdc.getViewport().height;
    }

    /**
     * Determine the text background color. This method returns the user specified background color, or a computed
     * default color if the user has not set a background color.
     *
     * @param color text color.
     *
     * @return the user specified background color, or a default color that contrasts with the text color.
     */
    protected Color determineBackgroundColor(Color color)
    {
        // If the app specified a background color, use that.
        Color bgColor = this.getBackgroundColor();
        if (bgColor != null)
            return bgColor;

        // Otherwise compute a color that contrasts with the text color.
        return this.computeBackgroundColor(color);
    }

    /**
     * Compute a background color that contrasts with the text color.
     *
     * @param color text color.
     *
     * @return a color that contrasts with the text color.
     */
    protected Color computeBackgroundColor(Color color)
    {
        // Otherwise compute a color that contrasts with the text color.
        float[] colorArray = new float[4];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), colorArray);

        if (colorArray[2] > 0.5)
            return new Color(0, 0, 0, 0.7f);
        else
            return new Color(1, 1, 1, 0.7f);
    }
    
    private Point2D getRotatedTextDimensions()
    {
        double widthInPixels = this.textBounds.getWidth();
        double heightInPixels = this.textBounds.getHeight();
        
        Angle rotation = Angle.normalizedLongitude(this.heading);
        double ct = Math.cos(rotation.radians);
        double st = Math.sin(rotation.radians);

        double hct = heightInPixels * ct;
        double wct = widthInPixels * ct;
        double hst = heightInPixels * st;
        double wst = widthInPixels * st;
        
        if (rotation.degrees > 0)
        {
            if (rotation.degrees < 90)
            {
                // 0 < theta < 90
                heightInPixels = hct + wst;
                widthInPixels = wct + hst;
            }
            else
            {
                // 90 <= theta <= 180
                heightInPixels = wst - hct;
                widthInPixels = hst - wct;
            }
        }
        else
        {
            if (rotation.degrees > -90 )
            {
                // -90 < theta <= 0
                heightInPixels = hct - wst;
                widthInPixels = wct - hst;
            }
            else
            {
                // -180 <= theta <= -90
                heightInPixels = -(hct + wst);
                widthInPixels = -(wct + hst);
            }
        }
        
        return new Point2D.Double(widthInPixels, heightInPixels);
    }

    /**
     * Compute the sector covered by this surface text.
     *
     * @param dc Current draw context.
     *
     * @return The sector covered by the surface text.
     */
    protected Sector[] computeSector(DrawContext dc)
    {
        // Compute text extent depending on distance from eye
        Globe globe = dc.getGlobe();

        Point2D textDimensions = getRotatedTextDimensions();
        double heightInPixels = textDimensions.getY();
        double widthInPixels = textDimensions.getX();
        
        double heightFactor = heightInPixels / this.textBounds.getHeight();
        double heightInMeters = heightFactor * this.textSizeInMeters;
        double widthInMeters = heightInMeters * (widthInPixels / heightInPixels);
        
        double radius = globe.getRadius();
        double heightInRadians = heightInMeters / radius;
        double widthInRadians = widthInMeters / radius;

        // Compute the offset from the reference position.
        // Convert pixels to meters based on the geographic size of the text.
        Point2D textOffset = getOffset().computeOffset(widthInPixels, heightInPixels, null, null);

        double metersPerPixel = heightInMeters / heightInPixels;

        double dxRadians = (textOffset.getX() * metersPerPixel) / radius;
        double dyRadians = (textOffset.getY() * metersPerPixel) / radius;
        
        double minLat = this.location.latitude.addRadians(dyRadians).degrees;
        double maxLat = this.location.latitude.addRadians(dyRadians + heightInRadians).degrees;
        double minLon = this.location.longitude.addRadians(dxRadians).degrees;
        double maxLon = this.location.longitude.addRadians(dxRadians + widthInRadians).degrees;
        
        this.drawLocation = LatLon.fromDegrees(minLat, minLon);

        if (maxLon > 180) {
            // Split the bounding box into two sectors, one to each side of the anti-meridian.
            Sector[] sectors = new Sector[2];
            sectors[0] = Sector.fromDegrees(minLat, maxLat, minLon, 180);
            sectors[1] = Sector.fromDegrees(minLat, maxLat, -180, maxLon - 360);
            this.spansAntimeridian = true;
            return sectors;
        } else {
            this.spansAntimeridian = false;
            return new Sector[] {Sector.fromDegrees(minLat, maxLat, minLon, maxLon)};
        }
    }

    /**
     * Get the text renderer to use to draw text.
     *
     * @param dc Current draw context.
     *
     * @return The text renderer that will be used to draw the surface text.
     */
    protected TextRenderer getTextRenderer(DrawContext dc)
    {
        return OGLTextRenderer.getOrCreateTextRenderer(dc.getTextRendererCache(), this.getFont(), true, false, false);
    }

    /**
     * Determine the text bounds.
     *
     * @param dc Current draw context.
     */
    protected void updateTextBounds(DrawContext dc)
    {
        this.textBounds = this.getTextRenderer(dc).getBounds(this.text);
    }
}
