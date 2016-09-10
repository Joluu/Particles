package Applet;

import javax.vecmath.*;
import javax.media.opengl.*;



/** 
 * Simple particle implementation.
 * 
 * @author Doug James, January 2007
 */
public class Particle
{
    /** Radius of particle's circle graphic. */
    private static final double PARTICLE_RADIUS = .01;//0.004;/// (NOT USED)

    /** Display list index. */
    private static int PARTICLE_DISPLAY_LIST = -1;

    /** Highlighted appearance if true, otherwise white. */
    private boolean highlight = false;

    /** If true, then particle is pinned in space. */
    private boolean pin = false;
    
    /** If true, then particle is constrained to a link. */
    private boolean link = false;

    /** Default mass. */
    double   m = Constants.PARTICLE_MASS;

    /** Deformed Position. */
    Point2d  x = new Point2d();

    /** Undeformed/material Position. */
    Point2d  x0 = new Point2d();

    /** Velocity. */
    Vector2d v = new Vector2d();

    /** Force accumulator. */
    Vector2d f = new Vector2d();
    
    /** Index of particle for ConstraintProcessor id. */
    private int key = -1;

    /** 
     * Constructs particle with the specified material/undeformed
     * coordinate, p0.
     */
    Particle(Point2d x0) 
    {
	this.x0.set(x0);
	x.set(x0);
    }

    /** Draws circular particle using a display list. */
    public void display(GL gl)
    {
	if(PARTICLE_DISPLAY_LIST < 0) {// MAKE DISPLAY LIST:
	    int displayListIndex = gl.glGenLists(1);
	    gl.glNewList(displayListIndex, GL.GL_COMPILE);
	    drawParticle(gl, new Point2d());///particle at origin
	    gl.glEndList();
	    //System.out.println("MADE LIST "+displayListIndex+" : "+gl.glIsList(displayListIndex));
	    PARTICLE_DISPLAY_LIST = displayListIndex;
	}

	/// COLOR: DEFAULT BLACK; GREEN IF HIGHLIGHTED; ADD RED IF PINNED
	//Color3f c = new Color3f(1,1,1);//default: white
	float cx = 0f;
	float cy = 0f;
	float cz = 0f;
	if(pin){ 
	    cx = 1f;//add red
	    cy *= 0.2f;
	    cz = 0;
	}
	if(highlight) {
	    cy = 1.0f;
	    cz = 0;
	}

	gl.glColor3f(cx, cy, cz);

	/// DRAW ORIGIN-CIRCLE TRANSLATED TO "p":
	gl.glPushMatrix();
	gl.glTranslated(x.x, x.y, 0);
	gl.glCallList(PARTICLE_DISPLAY_LIST);
	gl.glPopMatrix();
    }

    /** Specifies whether particle should be drawn highlighted. */
    public void setHighlight(boolean highlight) { 
	this.highlight = highlight;   
    }
    /** True if particle should be drawn highlighted. */
    public boolean getHighlight() { 
	return highlight; 
    }

    /** Specifies whether or not this particle is fixed in space via a
     * pin constraint. (Should probably be elsewhere in a generic
     * constraint list). */
    public void setPin(boolean fix) { pin = fix; }

    /** Returns true if currently pinned. */
    public boolean isPinned() { return pin; }

    /** Specifies whether or not this particle is constrained. */
    public void setConstraint(boolean constraint) { link = constraint; }

    /** Returns true if currently pinned. */
    public boolean isConstrained() { return link; }
    
    /** 
     * Draws a canonical circular particle.
     */
    private static void drawParticle(GL gl, Point2d p)
    {
	if(false) {/// GL_POINT (doesn't really merit a display list ;P) 
	    gl.glBegin(GL.GL_POINTS);
	    gl.glVertex2d(p.x,p.y);
	    gl.glEnd();
	}
	else {/// TRIANGULATED CIRCLE:
	    double radius = PARTICLE_RADIUS;

	    double vectorY1 = p.y;
	    double vectorX1 = p.x;
 
	    gl.glBegin(GL.GL_TRIANGLES);
	    int N = 45;
	    for(int i=0; i<=N; i++)
		{
		    double angle   = ((double)i) * 2. * Math.PI / (double)N;
		    double vectorX = p.x + radius*Math.sin(angle);
		    double vectorY = p.y + radius*Math.cos(angle);
		    gl.glVertex2d(p.x,p.y);
		    gl.glVertex2d(vectorX1,vectorY1);
		    gl.glVertex2d(vectorX,vectorY);
		    vectorY1 = vectorY;
		    vectorX1 = vectorX;	
		}
	    gl.glEnd();
	}
    }
    
    /** Integer key used to identify particle in ConstraintProcessor */
    void setKey(int key) {
	this.key = key;
    }
    
    /** Integer key used to identify body in ConstraintProcessor
     * (default=-1 if not set). */
    public int getKey() { return key; }


}
