package Applet;

import javax.media.opengl.GL;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import javax.vecmath.Point2d;

/** 
 * Simple object for setting up orthographic projection in OpenGL, and
 * mapping mouse clicks into the unit computational cell.
 * <pre>
 * THERE IS NO NEED TO EDIT THIS CLASS
 * </pre>
 * @author Doug James, January 2007
 */
public class OrthoMap
{
    private double eps = 0.03;//epsilon boundary gap
    private int width, height;
    private double r;
    private double L   = 0;	  
    private double R;//   = r*1;
    private double B   = 0;	  
    private double T   = 1;

    OrthoMap(int viewportWidth, int viewportHeight)
    {
	width  = viewportWidth;
	height = viewportHeight;
	r = (double)width/(double)height;
	//L   = B = 0;	  
	R   = r*1;
	//T   = 1;
	//System.out.println("r="+r);
    }

    public void apply_glOrtho(GL gl) 
    {
	gl.glOrtho(L-eps, R+eps, B-eps, T+eps, -1, +1);
    }

    /** Get 2d coordinates in unit computation cell of e using
     * knowledge of ortho projection. */
    public Point2d getPoint2d(MouseEvent e)
    {
	Dimension size = e.getComponent().getSize();

	double    x    = (double)e.getX()/(double)size.width;      /// on [0,1] (unless outside mouse click)
	x *= (r + 2*eps);
	x -= eps;

	double    y    = 1. - (double)e.getY()/(double)size.height;/// on [0,1] (unless outside mouse click)
	y *= (1 + 2*eps);
	y -= eps;

	Point2d   p    = new Point2d(x,y);
	// 	    p.clampMax(1);
	// 	    p.clampMin(0);
	//System.out.println(p);
	return p;
    }


}
