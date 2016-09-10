package Applet;

import javax.vecmath.*;
import javax.media.opengl.*;

/** 
 * Spring force between one particle and a proxy point. 
 * 
 * @author Doug James, January 2007
 */
public class SpringForce1Particle implements Force
{
    Particle p1;
    Point2d  x2;
    ParticleSystem PS;

    SpringForce1Particle(Particle p1, Point2d x2, ParticleSystem PS)
    {
	if(p1==null || x2==null) throw new NullPointerException("p1="+p1+", x2="+x2);

	this.p1 = p1;
	this.x2 = x2;
	this.PS = PS;
    }

    public void updatePoint(Point2d x) {
	x2.set(x);
    }

    public void applyForce()
    {
	{
	    Vector2d v = new Vector2d();
	    v.sub(x2, p1.x);
	    double L = v.length();

	    v.normalize();

	    double dvDot = - v.dot(p1.v);

	    double k = PS.STIFFNESS_STRETCH * 0.5;
	    v.scale( k* ( L + 0.03*dvDot ) );
	    p1.f.add(v);

	    /// STRONGLY DAMP INTERACTION:
	    v.set(p1.v);
	    v.scale(- 5. * p1.m);
	    p1.f.add(v);
	}
    }

    public void display(GL gl)
    {
	/// DRAW A LINE:
	gl.glColor3f(0,1,0);
	gl.glBegin(GL.GL_LINES);
	gl.glVertex2d(p1.x.x, p1.x.y);
	gl.glVertex2d(x2.x,   x2.y);
	gl.glEnd();	
    }

    public ParticleSystem getParticleSystem() { return PS; }

    public boolean contains(Particle p)  { return (p==p1);  }
}
