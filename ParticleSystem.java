package Applet;

import java.util.*;

import javax.vecmath.*;
import javax.media.opengl.*;

import Applet.ParticleSystemBuilder.BuilderGUI;


/**
 * Maintains dynamic lists of Particle and Force objects, and provides
 * access to their state for numerical integration of dynamics.
 * <pre>
 * Symplectic-Euler integrator is hard-coded, with embedded collision
 * processing code.
 * </pre>
 * 
 * @author Doug James, January 2007 (revised Feb 2009)
 */
public class ParticleSystem //implements Serializable
{
	/** Spring stretching stiffness. */
	public static double STIFFNESS_STRETCH = 10000.0; 

	/** numerical damping */
	public static double ks    = STIFFNESS_STRETCH/2; 

	public static double kd    = STIFFNESS_STRETCH/2; 

	/** Current simulation time. */
	double time = 0;

	/** List of Particle objects. */
	ArrayList<Particle>   P = new ArrayList<Particle>();

	/** List of Force objects. */
	ArrayList<Force>      F = new ArrayList<Force>();

	/** List of Constraint objects. */
	ArrayList<Link> C = new ArrayList<Link>();

	/** Rigid Set used to create constrained links with. Not sure why it has to be a HashSet. */
	HashSet<Force> rigidSet = new HashSet<Force>();

	/** Array form of the particles in the system. */
	Particle[] particleArray;

	public BuilderGUI gui;


	ConstraintProcessor processor = null;


	/** Basic constructor. */
	public ParticleSystem(BuilderGUI gui) { 
		this.gui = gui;
	}

	/** Adds a force object (until removed) */
	public synchronized void addForce(Force f) {
		F.add(f);
	}

	/** Useful for removing temporary forces, such as user-interaction
	 * spring forces. */
	public synchronized void removeForce(Force f) {
		F.remove(f);
	}

	/** Creates particle and adds it to the particle system. 
	 * @param p0 Undeformed/material position. 
	 * @return Reference to new Particle.
	 */
	public synchronized Particle createParticle(Point2d p0) 
	{
		Particle newP = new Particle(p0);
		P.add(newP);
		return newP;
	}


	/** Removes particle and any attached forces from the ParticleSystem.
	 * @param p Particle
	 */
	public synchronized void removeParticle(Particle p) 
	{
		P.remove(p);

		ArrayList<Force> removalList = new ArrayList<Force>();
		for(Force f : F) {/// REMOVE f IF p IS USED IN FORCE
			if(f.contains(p))  removalList.add(f);
		}

		for( Force fRemoved : removalList){
			if( rigidSet.contains(fRemoved) )
				rigidSet.remove(fRemoved);
		}

		F.removeAll(removalList);

	}

	/** 
	 * Helper-function that computes nearest particle to the specified
	 * (deformed) position.
	 * @return Nearest particle, or null if no particles. 
	 */
	public synchronized Particle getNearestParticle(Point2d x)
	{
		Particle minP      = null;
		double   minDistSq = Double.MAX_VALUE;
		for(Particle particle : P) {
			double distSq = x.distanceSquared(particle.x);
			if(distSq < minDistSq) {
				minDistSq = distSq;
				minP = particle;
			}
		}
		return minP;
	}

	/**
	 * Helper-function that chooses the nearest link or null if no link.
	 * @return Nearest link, or null if no link selected.
	 */
	public synchronized SpringForce2Particle getNearestLink(Point2d x){

		SpringForce2Particle linkCandidate = null;

		for(Force f : F){
			if (f instanceof SpringForce2Particle){

				SpringForce2Particle link = (SpringForce2Particle) f;

				Vector2d linkRay = new Vector2d();
				Vector2d ptRay	 = new Vector2d();

				linkRay.sub(link.p1.x0, link.p2.x0);
				ptRay.sub(link.p1.x0, x);

				linkRay.normalize();
				double xT = Math.cos(Math.PI/2.)*linkRay.x - Math.sin(Math.PI/2.)*linkRay.y;
				double yT = Math.sin(Math.PI/2.)*linkRay.x + Math.cos(Math.PI/2.)*linkRay.y;
				Vector2d linkRayT = new Vector2d(xT, yT);
				linkRayT.normalize();

				if ((linkRay.dot(ptRay) >= 0. && linkRay.dot(ptRay) <= 1.) && Math.abs(linkRayT.dot(ptRay)) <= .01 ){
					linkCandidate = link;
				}
			}

		}

		return linkCandidate;
	}

	/** 
	 * Helper-function that computes nearest particle to the specified
	 * (deformed) position.
	 * @return Nearest particle, or null if no particles. 
	 * @param pinned If true, returns pinned particles, and if false, returns unpinned
	 */
	public synchronized Particle getNearestPinnedParticle(Point2d x, boolean pinned)
	{
		Particle minP      = null;
		double   minDistSq = Double.MAX_VALUE;
		for(Particle particle : P) {
			if(particle.isPinned() == pinned) {
				double distSq = x.distanceSquared(particle.x);
				if(distSq < minDistSq) {
					minDistSq = distSq;
					minP = particle;
				}
			}
		}
		return minP;
	}

	/** Moves all particles to undeformed/materials positions, and
	 * sets all velocities to zero. Synchronized to avoid problems
	 * with simultaneous calls to advanceTime(). */
	public synchronized void reset()
	{
		for(Particle p : P)  {
			p.x.set(p.x0);
			p.v.set(0,0);
			p.f.set(0,0);
			p.setHighlight(false);
			processor = null;
			C.clear();
		}

		/// WORKAROUND FOR DANGLING MOUSE-SPRING FORCES AFTER PS-INTERNAL RESETS:
		ArrayList<Force> removeF = new ArrayList<Force>();
		for(Force f : F) {
			if(f instanceof SpringForce1Particle) removeF.add(f);
		}
		F.removeAll(removeF);

		time = 0;
	}

	/**
	 * MAIN FUNCTION TO IMPLEMENT YOUR ROBUST CONSTRAINT PROCESSING ALGORITHM.
	 */
	public synchronized void advanceTime(double dt)
	{
		long t0 = -System.nanoTime();

		{/// GATHER BASIC FORCES (NO NEED TO MODIFY):

			/// CLEAR FORCE ACCUMULATORS:
			for(Particle p : P)  p.f.set(0,0);

			/// APPLY FORCES:
			for(Force force : F) 
				force.applyForce();

			// GRAVITY:
			for(Particle p : P)   p.f.y -= p.m * Constants.GRAVITY;

			// ADD SOME MASS-PROPORTIONAL DAMPING (DEFAULT IS ZERO)
			for(Particle p : P) {
				Utils.acc(p.f,  -Constants.DAMPING_MASS * p.m, p.v);
			}
		}

		//TODO: make sure to nullify the collision processor when particles or constraints are modified
		if(processor == null ) {
			processor = new ConstraintProcessor(this);

			//create all the links
			for ( Force spf : rigidSet ){
				assert spf instanceof SpringForce2Particle;

				SpringForce2Particle sp = (SpringForce2Particle)spf;

				//satisfy the precondition for creating Constraint
				if(sp.p1.getKey() < sp.p2.getKey()){
					Link link = new Link(sp.p1, sp.p2);
					C.add(link);

				}else{
					Link link = new Link(sp.p2, sp.p1);
					C.add(link);

				}

			}

		}

		//process the constraints if there are any.
		if (C.size() > 0){ processor.processConstraints(); }



		///////////////////////////////////////////////
		/// SYMPLECTIC-EULER TIME-STEP w/ COLLISIONS:
		///////////////////////////////////////////////
		///////////////////////////////////////////////
		/// 1. UPDATE PREDICTOR VELOCITY WITH FORCES
		///////////////////////////////////////////////
		for(Particle p : P) {

			/// APPLY PIN CONSTRAINTS (set p=p0, and zero out v):
			if(p.isPinned()) {
				p.v.set(0,0);
			}
			else {
				p.v.scaleAdd(dt/p.m, p.f, p.v); // v += dt * f/m;
			}

			/// CLEAR FORCE ACCUMULATOR
			p.f.set(0,0);
		}


		//////////////////////////////////////////////////////////
		/// 3. ADVANCE POSITIONS USING COLLISION-FEASIBLE VELOCITY
		//////////////////////////////////////////////////////////
		for(Particle p : P) {
			p.x.scaleAdd(dt, p.v, p.x); //p.x += dt * p.v;
		}

		time += dt;

		t0 += System.nanoTime();
	}

	/**
	 * Displays Particle and Force objects.
	 */
	public synchronized void display(GL gl) 
	{
		for(Force force : F) {
			force.display(gl);
		}

		for(Particle particle : P) {
			particle.display(gl);
		}
	}

	/** 
	 * Prints the current state of the Particle System for debugging purposes. 
	 * */
	public String toString(){
		StringBuilder sys = new StringBuilder();
		StringBuilder particles = new StringBuilder();
		StringBuilder links = new StringBuilder();

		enumParticles();

		//write particle attributes
		particles.append("<Particles>\n");
		particles.append(P.size() + "\n");
		for (Particle p : P){
			particles.append(p.x0.x + ", " + p.x0.y + ", " + p.isPinned() + "\n");
		}
		particles.append("<end Particles>\n");

		//write link attributes
		links.append("<Links>\n");

		for ( Force f : F ){
			if( f instanceof SpringForce2Particle ){
				SpringForce2Particle sp = (SpringForce2Particle)f;
				
				links.append(sp.p1.getKey() + ", " + sp.p2.getKey() + ", " + rigidSet.contains(sp) + "\n");
			}
		}
		links.append("<end Links>");

		sys.append(particles);
		sys.append(links);

		return sys.toString();
	}

	/** Convert the current list of particles to an array and set the keys. */
	public synchronized void enumParticles(){

		particleArray = (Particle[])P.toArray(new Particle[0]);

		for(int k=0; k<particleArray.length; k++) {	
			particleArray[k].setKey(k);
		}
	}


}
