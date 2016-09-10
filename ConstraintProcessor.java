package Applet;

import java.util.*;

import javax.swing.JOptionPane;
import javax.vecmath.SingularMatrixException;

import Jama.Matrix;

public class ConstraintProcessor {

	ParticleSystem PS;
	private Particle[] R;
	private Matrix Q = null; // 2N x 1 external forces
	private Matrix q_dot=null;

	private Matrix b = null;
	private Matrix A = null;
	private Matrix fc = null;
	private Matrix J = null;
	private Matrix M_inv = null;
	private Matrix cFunc= null;



	ConstraintProcessor(ParticleSystem PS){
		if(PS==null) throw new NullPointerException();

		this.PS = PS;
		this.Q = new Matrix(2*PS.P.size(), 1);
		this.q_dot = new Matrix(2*PS.P.size(), 1);
		
		PS.enumParticles();
		this.R = PS.particleArray.clone();
//		R = (Particle[])PS.P.toArray(new Particle[0]);

		this.M_inv = new Matrix(2*R.length, 2*R.length);

		//index the rigid particles with keys
		for(int k=0; k<R.length; k++) {	
			R[k].setKey(k);
			int index = R[k].getKey();

			if(!R[k].isPinned()){
				M_inv.set(2*index, 2*index , 1);
				M_inv.set(2*index+1, 2*index+1 , 1);
			}
		}
		//M_inv.print(2, 2);
	}

	//For now treat every particle a rigid particle
	public void processConstraints(){
		setJacobian();
		setup();
		solve(A, b);

	}

	/** set up all necessary matrixes and vectors for solving.*/
	private void setup(){

		for (int i=0; i<R.length; i++){

			int index = R[i].getKey();
			Q.set(2*index,   0, R[i].f.x);
			Q.set(2*index+1, 0, R[i].f.y);

			q_dot.set(2*index,   0, R[i].v.x);
			q_dot.set(2*index+1, 0, R[i].v.y);
		}
		//Q.print(2, 2);

		//set up b
		b = new Matrix(PS.C.size(), 1);
		Matrix C_dot = J.times(q_dot);
		Matrix b_temp = J.times(M_inv).times(Q).times(-1.);
		b = b_temp.minus(cFunc.times(PS.ks)).minus(C_dot.times(PS.kd));

		//b.print(2,2);

		//set up A
		A = new Matrix(PS.C.size(), PS.C.size());
		Matrix JT = J.transpose();
		A = J.times(M_inv).times(JT);
		//A.print(2,2);


	}

	private void setJacobian(){
		J = new Matrix(PS.C.size(), 2*PS.P.size());
		cFunc = new Matrix(PS.C.size(), 1);

		int i=0;
		for (Link c: PS.C){
			c.evalConstraintDeriv();
			int key1 = c.p1.getKey();
			int key2 = c.p2.getKey();

			J.set(i,2*key1,   c.dx1);
			J.set(i,2*key1+1, c.dy1);
			J.set(i,2*key2,   c.dx2);
			J.set(i,2*key2+1, c.dy2);

			cFunc.set(i, 0, c.evalConstraintFunction());
			i++;
		}
		//	cFunc.print(2, 2);
		//J.print(2,2);
	}

	/** solve the linear system.  Use Jama's solve method for now. 
	 * Modify this to use CG later. */
	private void solve(Matrix A, Matrix b){

		Matrix lambda = null;
		try {
			lambda = A.solve(b);
		}
		catch (RuntimeException r) {
			JOptionPane.showMessageDialog(PS.gui.guiFrame, "MATRIX IS SINGULAR!!!. Restart program. ",
					"Computational Error", JOptionPane.ERROR_MESSAGE);
		}
		
		if (lambda != null)
			fc = J.transpose().times(lambda);
		else
			System.err.println("Something funny happened. ");
		
		//fc.print(2,2);
		for (int i=0; i<R.length; i++){
			R[i].f.x = Q.get(2*i,0)    + fc.get(2*i,0);
			R[i].f.y = Q.get(2*i+1, 0) + fc.get(2*i+1,0);

		}
	}


	public ParticleSystem getParticleSystem() { return PS; }

}
