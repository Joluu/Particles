package Applet;

import javax.vecmath.Vector2d;

public class Link {
	Particle p1;
	Particle p2;
	double r, dx1, dy1, dx2, dy2;
	
	/**Precondition: p1 must be the particle with the lower key.
	 * Creates a link constraint on 2 particles connected by a spring.*/
	Link(Particle p1, Particle p2){
		this.p1 = p1;
		this.p2 = p2;
		
		r = p1.x0.distance(p2.x0);
		//System.out.println("p1 = "+p1.x.x + " " + p1.x.y + " p2 = "+ p2.x.x +" "+ p2.x.y );
		
		dx1=0; dy1=0;
	}
	
	public double evalConstraintFunction(){
		
		Vector2d linkLength = new Vector2d();
		linkLength.sub(p2.x, p1.x);
		return linkLength.length() - r;
		
	}
	
	public void evalConstraintDeriv(){
		Vector2d linkLength = new Vector2d();
		linkLength.sub(p2.x, p1.x);
		double temp = linkLength.lengthSquared();
		temp = Math.pow(temp, -.5);
		
		//compute dC/dX1
		dx1 = -(p2.x.x - p1.x.x)*temp;
		dy1 = -(p2.x.y - p1.x.y)*temp;
		
		//compute dC/dX2
		dx2 = (p2.x.x - p1.x.x)*temp;
		dy2 = (p2.x.y - p1.x.y)*temp;
	}
	
	public boolean contains(Particle p)  { 
		return ((p==p1) || (p==p2));
	}

}
