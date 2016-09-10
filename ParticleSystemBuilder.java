package Applet;

import java.text.NumberFormat;
import java.util.*;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import javax.swing.*;
import javax.vecmath.*;

import javax.media.opengl.*;

import com.sun.opengl.util.*;

/**
 * CS 5643: Assignment #2 "Robust Collision Processing" 
 * (a.k.a. "The Spaghetti Factory")
 * <pre>
 * main() entry point class that initializes ParticleSystem, OpenGL
 * rendering, and GUI that manages GUI/mouse events. Spacebar toggles simulation advance.
 * </pre>
 * 
 * Unresolved Bugs: After loading a file, GL canvas must be clicked twice before simulation
 * will work.  
 * 
 * @author Doug James, January 2007 (revised Feb 2009)
 */
public class ParticleSystemBuilder implements GLEventListener
{
	private static int N_STEPS_PER_FRAME = 30;
	private static boolean startLinks = false;
	private FrameExporter frameExporter;
	private OrthoMap orthoMap;

	/** Default graphics time step size. */
	public static final double DT = 0.01/N_STEPS_PER_FRAME;

	Color3f bgColor = new Color3f(1,1,1);

	/** Main window frame. */
	JFrame frame = null;

	private int width, height;

	/** The single ParticleSystem reference. */
	ParticleSystem PS;

	/** Object that handles all GUI and user interactions of building
	 * Task objects, and simulation. */
	public BuilderGUI     gui;

	GL gl;

	static boolean autoRigid = true;

	/** Main constructor. Call start() to begin simulation. */
	ParticleSystemBuilder() 
	{
		gui = new BuilderGUI();
		PS  = new ParticleSystem(gui);
	}

	/**
	 * Builds and shows windows/GUI, and starts simulator.
	 * TODO: copy these steps into the Applet canvas instead of JFrame!!!!!!!!!!
	 */
	public void start()
	{
		if(frame != null) return;

		frame = new JFrame("TAM 7970 Proposed KMODDL Simulator");
		GLCanvas canvas = new GLCanvas();
		canvas.addGLEventListener(this);
		frame.add(canvas);

		final Animator animator = new Animator(canvas);
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				// Run this on another thread than the AWT event queue to
				// make sure the call to Animator.stop() completes before
				// exiting
				new Thread(new Runnable() {
					public void run() {
						animator.stop();
						System.exit(0);
					}
				}).start();
			}
		});

		frame.pack();
		frame.setSize(600,600);
		frame.setLocation(240, 0);
		frame.setVisible(true);
		animator.start();
	}


	/** Maps mouse event into computational cell using OrthoMap. */
	public Point2d getPoint2d(MouseEvent e) {
		return orthoMap.getPoint2d(e);
	}

	/** GLEventListener implementation for ParticleSystemBuilder: 
	 * Initializes JOGL renderer. */
	public void init(GLAutoDrawable drawable) 
	{
		// DEBUG PIPELINE (can use to provide GL error feedback... disable for speed)
		//drawable.setGL(new DebugGL(drawable.getGL()));

		GL gl = drawable.getGL();
		System.err.println("INIT GL IS: " + gl.getClass().getName());

		gl.setSwapInterval(1);

		/// SETUP ANTI-ALIASED POINTS AND LINES:
		gl.glLineWidth(2);  /// YOU MAY WANT TO ADJUST THIS WIDTH
		gl.glPointSize(3f); /// YOU MAY WANT TO ADJUST THIS SIZE
		gl.glEnable(gl.GL_POINT_SMOOTH);
		gl.glHint  (gl.GL_POINT_SMOOTH_HINT, gl.GL_NICEST);
		gl.glEnable(gl.GL_LINE_SMOOTH);
		gl.glHint  (gl.GL_LINE_SMOOTH_HINT, gl.GL_NICEST);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

		drawable.addMouseListener(gui);
		drawable.addMouseMotionListener(gui);

		drawable.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) {
				gui.dispatchKey(e.getKeyChar(), e);
			}
		});
	}

	/** GLEventListener implementation */
	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {}

	/** Handles window reshaping. */
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) 
	{
		System.out.println("width="+width+", height="+height);
		height = Math.max(height, 1); // avoid height=0;

		this.width  = width;
		this.height = height;

		GL gl = drawable.getGL();
		gl.glViewport(0,0,width,height);	

		/// SETUP ORTHOGRAPHIC PROJECTION AND MAPPING INTO UNIT CELL:
		gl.glMatrixMode(GL.GL_PROJECTION);	
		gl.glLoadIdentity();			
		orthoMap = new OrthoMap(width, height);//Hide grungy details in OrthoMap
		orthoMap.apply_glOrtho(gl);

		/// GET READY TO DRAW:
		gl.glMatrixMode(GL.GL_MODELVIEW);
		gl.glLoadIdentity();
	}


	/** 
	 * Main event loop: OpenGL display + simulation
	 * advance. GLEventListener implementation.
	 */
	public void display(GLAutoDrawable drawable) 
	{
		GL gl = drawable.getGL();
		//gl.glClearColor(0,0,0,0);
		gl.glClearColor(bgColor.x, bgColor.y, bgColor.z, 0);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		/// DRAW COMPUTATIONAL CELL BOUNDARY:
		{
			gl.glBegin(GL.GL_LINE_STRIP);
			gl.glColor3f(0.5f, 0.5f, 0.5f);
			gl.glVertex2d(0,0);	gl.glVertex2d(1,0);	gl.glVertex2d(1,1);	gl.glVertex2d(0,1);	gl.glVertex2d(0,0);
			gl.glEnd();
		}

		/// SIMULATE/DISPLAY HERE (Handled by BuilderGUI):
		gui.simulateAndDisplayScene(gl);
		if(frameExporter != null) {
			frameExporter.writeFrame();
		}

	}


	/** Interaction central: Handles windowing/mouse events, and building state. */
	class BuilderGUI implements MouseListener, MouseMotionListener//, KeyListener
	{
		final JMenuBar	menuBar 	= new JMenuBar();
		final JMenu		menu		= new JMenu("File");
		final JMenuItem openItem 	= new JMenuItem("Open");
		final JMenuItem saveItem	= new JMenuItem("Save");
		final JMenuItem saveAsItem  = new JMenuItem("Save As...");
		final JFileChooser chooser  = new JFileChooser();
		final JCheckBox checkBox 	= new JCheckBox("Auto Rigid", true);
		final JLabel 	jLabel		= new JLabel("Spring Stiffness", JLabel.CENTER);
		private NumberFormat format;
		final JFormattedTextField stiffness	= new JFormattedTextField(format);


		boolean simulate = false;

		/** Current build task (or null) */
		Task task;
		JFrame  guiFrame;
		MenuActionPerformed menuAction = new MenuActionPerformed();
		TaskSelector taskSelector = new TaskSelector();

		JToggleButton[] buttons;

		BuilderGUI() 
		{
			guiFrame = new JFrame("Tasks");
			guiFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

			//create and add the radio buttons
			ButtonGroup     buttonGroup  = new ButtonGroup();
			JToggleButton[] buttons      = {new JToggleButton("Reset",           false),
					new JRadioButton ("Create Particle", true), 
					new JRadioButton ("Delete Particle", false), 
					new JRadioButton ("Delete Link"),
					new JRadioButton ("Move Particle",   false), 
					new JRadioButton ("Create Link",   false),
					new JRadioButton ("Pin Constraint",  false), 
					new JRadioButton ("Rigid Constraint", false)};
			this.buttons = buttons;

			buttons[buttons.length - 1].setToolTipText("Add or remove rigid constraints on a link.");

			// Set up and add the menu bar
			openItem.addActionListener(menuAction);
			openItem.setToolTipText("Load a Particle System from file.");
			saveItem.addActionListener(menuAction);
			saveItem.setToolTipText("Save the state of the current Particle System.");
			saveAsItem.addActionListener(menuAction);
			saveAsItem.setToolTipText("Save the state of the current Particle System to a new file.");
			checkBox.addItemListener(new ItemListener(){

				public void itemStateChanged(ItemEvent e) {
					autoRigid = !autoRigid;
				}

			});
			checkBox.setToolTipText("Unselect to create rigid constraints manually.");

			menuBar.add(menu);
			menu.add(openItem);
			menu.add(saveItem);
			menu.add(saveAsItem);

			guiFrame.setJMenuBar(menuBar);
			guiFrame.add(menuBar);

			for(int i=0; i<buttons.length; i++) {
				buttonGroup.add(buttons[i]);
				guiFrame.add(buttons[i]);
				buttons[i].addActionListener(taskSelector);
			}
			guiFrame.add(checkBox);
			guiFrame.add(jLabel);
			stiffness.setValue(ParticleSystem.STIFFNESS_STRETCH);
			stiffness.addPropertyChangeListener(new PropertyChangeListener(){

				public void propertyChange(PropertyChangeEvent evt) {
					if(evt.getSource() == stiffness){
						ParticleSystem.STIFFNESS_STRETCH = ((Number) stiffness.getValue()).doubleValue();
//						System.out.println("Spring Stiffness = " + PS.STIFFNESS_STRETCH);
					}
				}

			});
			stiffness.setToolTipText("Must hit reset to activate a new stiffness value.");
			guiFrame.add(stiffness);
			guiFrame.setLayout(new GridLayout(buttons.length + 5, 0));
			guiFrame.setSize(200,200);
			guiFrame.pack();
			guiFrame.setResizable(false);
			guiFrame.setVisible(true);

			task = new CreateParticleTask();
		}

		/** Simulate then display particle system and any builder
		 * adornments. */
		void simulateAndDisplayScene(GL gl)
		{
			/// TODO: OVERRIDE THIS INTEGRATOR (Doesn't use Force objects properly)
			if(simulate) {

				/// MULTIPLE STEPS OF SIZE DT (different from Assignment#1)
				int    nSteps = N_STEPS_PER_FRAME;
				double dt     = DT; 
				for(int k=0; k<nSteps; k++) {

					PS.advanceTime(dt);/// 
				}
			}

			// Draw particles, springs, etc.
			PS.display(gl);

			// Display Task, e.g., currently drawn spring.
			if(task != null) task.display(gl);
		}

		/** ActionListener implementation to manage Menu selections. */
		class MenuActionPerformed implements ActionListener{

			/** 
			 * Resets ParticleSystem to undeformed/material state,
			 * disables the simulation, and removes the active Task.
			 */
			void resetToRest() {
				if(task != null)  task.reset();
				task = null;

				simulate = false;
				PS.reset();//synchronized
				bgColor.set(1,1,1);
			}

			public void actionPerformed(ActionEvent e) {
				String cmd = e.getActionCommand();
				resetToRest();

				if (cmd == null) {
					return;
				}

				if (cmd.equals(Constants.OPEN))
					OpenAction();
				else if (cmd.equals(Constants.SAVE))
					SaveAction();
				else if (cmd.equals(Constants.SAVE_AS))
					SaveAsAction();
				else
					JOptionPane.showMessageDialog(guiFrame, "Invalid Command!!!","", JOptionPane.ERROR_MESSAGE);
			}

			/** Handles Open from MenuActionPerformed. */
			private void OpenAction(){

				File inputFile = null;
				chooser.setDialogTitle("Select Input File");

				do{
					if(inputFile != null){
						JOptionPane.showMessageDialog(guiFrame, "File must be in .txt format. ",
								"File name error", JOptionPane.ERROR_MESSAGE);
					}
					int returnVal = chooser.showOpenDialog(guiFrame);
					if (returnVal != JFileChooser.APPROVE_OPTION) return;
					inputFile = chooser.getSelectedFile();

				} while (!inputFile.toString().endsWith(".txt"));

				Scanner fileScanner = null;
				try {
					fileScanner = new Scanner(inputFile);
				} catch (FileNotFoundException e1) {
					JOptionPane.showMessageDialog(guiFrame, "Cannot find file!",
							"File read error", JOptionPane.ERROR_MESSAGE);
					e1.printStackTrace();
					return;
				}

				if(LoadParticleSystem(fileScanner)){
					JOptionPane.showMessageDialog(guiFrame, "File " + inputFile.getName() + " successfully loaded.",
							"Message", JOptionPane.INFORMATION_MESSAGE);
				}

				ActionEvent newE = new ActionEvent(buttons[0], 1, "Reset");
				gui.taskSelector.actionPerformed(newE);
			}

			/** Handles Save from MenuActionPerformed. */
			private void SaveAction(){

				PrintStream out = null;
				File file = chooser.getSelectedFile();
				if (file == null || !file.exists()) {

					ActionEvent newE = new ActionEvent(saveItem, 1, Constants.SAVE_AS);
					gui.menuAction.actionPerformed(newE);
					return;

				}
				try {
					out = new PrintStream(file);
				} catch (FileNotFoundException exc) {
					JOptionPane.showMessageDialog(guiFrame, "Cannot write file " + file.getName(),
							"File write error", JOptionPane.ERROR_MESSAGE);
					if (out != null) out.close();
					return;
				}
				//write stuff to the file
				if (WriteParticleSystem(out))
					JOptionPane.showMessageDialog(guiFrame, "File " + file.getName() + " successfully written",
							"Message", JOptionPane.INFORMATION_MESSAGE);
			}

			/** Handles Save As from MenuActionPerformed. */
			private void SaveAsAction(){

				PrintStream out = null;
				chooser.setDialogTitle("Select Save File");
				int ok = chooser.showSaveDialog(guiFrame);
				if (ok != JFileChooser.APPROVE_OPTION) return;
				File file = chooser.getSelectedFile();

				if (file.getName().lastIndexOf(".") < 0) {
					file = new File(file.getAbsolutePath() + ".txt");
				}

				if (file.exists()) {
					String msg = "File " + file.getName() + " exists; overwrite?";
					ok = JOptionPane.showConfirmDialog(guiFrame, msg, "", JOptionPane.YES_NO_OPTION);
					if (ok != JOptionPane.YES_OPTION) return;
				}

				try {
					out = new PrintStream(file);
				} catch (FileNotFoundException exc) {
					JOptionPane.showMessageDialog(guiFrame, "Cannot write file " + file.getName(),
							"File write error", JOptionPane.ERROR_MESSAGE);
					if (out != null) out.close();
					return;
				}

				if (WriteParticleSystem(out))
					JOptionPane.showMessageDialog(guiFrame, "File " + file.getName() + " successfully written",
							"Message", JOptionPane.INFORMATION_MESSAGE);
				else
					JOptionPane.showMessageDialog(guiFrame, "CORRUPT FILE!!!",
							"File read error", JOptionPane.ERROR_MESSAGE);

			}

			/** Loads the read in Particle System.  Resets the current PS, replaces
			 *  the current array lists with loaded ones. */
			private boolean LoadParticleSystem(Scanner fileScanner){

				//clean the system
				PS.rigidSet.removeAll(PS.rigidSet);
				PS.F.removeAll(PS.F);
				PS.C.removeAll(PS.C);
				PS.P.removeAll(PS.P);

				if ( !fileScanner.hasNext() || !( fileScanner.nextLine().startsWith("<Particles>") ) ){
					JOptionPane.showMessageDialog(guiFrame, "CORRUPT FILE!!!",
							"File read error", JOptionPane.ERROR_MESSAGE);
					return false;
				}

				//get number of particles
				int num = fileScanner.nextInt();

				fileScanner.useDelimiter( System.getProperty("line.separator") );
				while( fileScanner.hasNext() ){
					parseLine(fileScanner.next());
				}
				startLinks = false;
				fileScanner.close();

				if ( PS.P.size() != num ) return false;

				return true;

			}

			/** Parses a line of string for attributes in the particle system file. */
			private void parseLine( String line ){
				
				if( line.equals("<end Particles>") ||  line.equals("<end Links>") )
					return;

				if( line.equals("<Links>") ){
					startLinks = true;
					return;
				}

				Scanner lineScanner = new Scanner(line);
				lineScanner.useDelimiter("\\s*,\\s*");

				if (!startLinks){
					
					double x = lineScanner.nextDouble();
					double y = lineScanner.nextDouble();

					Particle p = new Particle(new Point2d(x,y));

					String isPinned = lineScanner.next();
					if( isPinned.contains("true") ) 
						p.setPin(true);

					PS.P.add(p);
				}else{
					PS.enumParticles();
					int p1 = lineScanner.nextInt();
					int p2 = lineScanner.nextInt();
					
					SpringForce2Particle sp = new SpringForce2Particle(PS.particleArray[p1], PS.particleArray[p2], PS);
					PS.F.add(sp);
					
					String isRigid = lineScanner.next();
					if( isRigid.contains("true") ){
						PS.rigidSet.add(sp);
						sp.setLinkHighlight(true);
					}
				}

			}


			/** Writes the current Particle System state to a file.  Returns true when 
			 * writing is complete.
			 * */
			private boolean WriteParticleSystem(PrintStream outFile){
				outFile.println(PS);
				outFile.close();
				return true;
			}
		}

		/**
		 * ActionListener implementation to manage Task selection
		 * using (radio) buttons.
		 */
		class TaskSelector implements ActionListener
		{
			/** 
			 * Resets ParticleSystem to undeformed/material state,
			 * disables the simulation, and removes the active Task.
			 */
			void resetToRest() {
				if(task != null)  task.reset();
				task = null;

				simulate = false;
				PS.reset();//synchronized
				bgColor.set(1,1,1);
			}

			/** Creates new Task objects to handle specified button action.  */
			public void actionPerformed(ActionEvent e)
			{
				String cmd = e.getActionCommand();

				resetToRest();
				if(cmd.equals("Reset")) {
				}
				else if(cmd.equals("Create Particle")){
					task = new CreateParticleTask();
				}
				else if(cmd.equals("Delete Particle")){
					task = new DeleteParticleTask();
				}
				else if(cmd.equals("Delete Link")){
					task = new DeleteSpringTask();
				}
				else if(cmd.equals("Move Particle")){
					task = new MoveParticleTask();
				}
				else if(cmd.equals("Create Link")){
					task = new CreateSpringTask();
				}
				else if(cmd.equals("Pin Constraint")){
					task = new PinConstraintTask();
				}

				else if(cmd.equals("Rigid Constraint")){
					task = new RigidConstraintTask();
				}
				else {
					System.out.println("UNHANDLED ActionEvent: "+e);
				}
			}
		}

		// Methods required for the implementation of MouseListener
		public void mouseEntered (MouseEvent e) { if(task!=null) task.mouseEntered(e);  }
		public void mouseExited  (MouseEvent e) { if(task!=null) task.mouseExited(e);   }
		public void mousePressed (MouseEvent e) { if(task!=null) task.mousePressed(e);  }
		public void mouseReleased(MouseEvent e) { if(task!=null) task.mouseReleased(e); }
		public void mouseClicked (MouseEvent e) { if(task!=null) task.mouseClicked(e);  }

		// Methods required for the implementation of MouseMotionListener
		public void mouseDragged (MouseEvent e) { if(task!=null) task.mouseDragged(e);  }
		public void mouseMoved   (MouseEvent e) { if(task!=null) task.mouseMoved(e);    }


		/**
		 * Handles keyboard events, e.g., spacebar toggles
		 * simulation/pausing, and escape resets the current Task.
		 */
		public void dispatchKey(char key, KeyEvent e)
		{
			if(key == ' ') {//SPACEBAR --> TOGGLE SIMULATE
				simulate = !simulate;
				if(simulate) 
					task = new DragParticleTask();  //this happens at runtime
				else 
					task = null;
			}
			else if (key == 'r') {

				if(task != null)  task.reset();

				//Task lastTask = task;
				taskSelector.resetToRest();//sets task=null;
				buttons[0].doClick();
			}
/*			else if (key == 'e') {//increase damping
				frameExporter = ((frameExporter==null) ? (new FrameExporter()) : null);
				System.out.println("'e' : frameExporter = "+frameExporter);
			} */
			else if (key == '=') {//increase nsteps
				N_STEPS_PER_FRAME = Math.max((int)(1.05*N_STEPS_PER_FRAME), N_STEPS_PER_FRAME+1);
				System.out.println("N_STEPS_PER_FRAME="+N_STEPS_PER_FRAME+
						";  dt="+(float)DT+";  dtFrame="+(float)(DT*N_STEPS_PER_FRAME));
				//TODO: put this in a textfield
			}
			else if (key == '-') {//decrease nsteps
				int n = Math.min((int)(0.95*N_STEPS_PER_FRAME), N_STEPS_PER_FRAME-1);
				N_STEPS_PER_FRAME = Math.max(1, n);
				System.out.println("N_STEPS_PER_FRAME="+N_STEPS_PER_FRAME+
						";  dt="+(float)DT+";  dtFrame="+(float)(DT*N_STEPS_PER_FRAME));
				//TODO: put this in a textfield

			}
		}

	}
	/** 
	 * "Task" command base-class extended to support
	 * building/interaction via mouse interface.  All objects
	 * extending Task are implemented here as inner classes for
	 * simplicity.
	 */
	abstract class Task implements MouseListener, MouseMotionListener
	{
		/** Displays any task-specific OpengGL information,
		 * e.g., highlights, etc. */
		public void display(GL gl) {}

		// Methods required for the implementation of MouseListener
		public void mouseEntered (MouseEvent e) {}
		public void mouseExited  (MouseEvent e) {}
		public void mousePressed (MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
		public void mouseClicked (MouseEvent e) {}

		// Methods required for the implementation of MouseMotionListener
		public void mouseDragged (MouseEvent e) {}
		public void mouseMoved   (MouseEvent e) {}

		/** Override to specify reset behavior during "escape" key
		 * events, etc. */
		abstract void reset();

	}
	/** Clicking task that creates particles. */
	class CreateParticleTask extends Task  
	{
		//private Particle lastCreatedParticle = null;

		public void mousePressed (MouseEvent e) {
			Point2d x0 = getPoint2d(e);
			Particle lastCreatedParticle = PS.createParticle(x0);
		}
		void reset() {}
	}

	/** Clicking task that increase mass damping constant. */
	class IncreaseDamping extends Task{
		public void mousePressed (MouseEvent e) {
			//do something to the damping
		}
		void reset() {}
	}

	/** Clicking task that deletes particles. */
	class DeleteParticleTask extends Task  
	{
		public void mousePressed (MouseEvent e) 
		{
			Particle deletedParticle = PS.getNearestParticle(getPoint2d(e));
			if(deletedParticle==null) return;
			PS.removeParticle(deletedParticle);
			PS.processor = null;
		}
		void reset() {}
	}
	
	/** Clicking task that deletes a spring force. */
	class DeleteSpringTask extends Task  
	{
		public void mousePressed (MouseEvent e) 
		{
			Point2d  cursorP = getPoint2d(e);
			SpringForce2Particle s1 = PS.getNearestLink(cursorP);

			if(s1 != null){
				if (PS.F.contains(s1)) {
					PS.removeForce(s1);
					if(PS.rigidSet.contains(s1)){
						PS.rigidSet.remove(s1);
						//System.out.println("constraint removed");

					}
				}
			}
			
			PS.processor = null;
		}
		void reset() {}
	}

	/** Task to move nearest particle. */
	class MoveParticleTask extends Task  
	{
		private Particle moveParticle = null;

		/** Start moving nearest particle to mouse press. */
		public void mousePressed(MouseEvent e) 
		{
			// FIND NEAREST PARTICLE:
			Point2d cursorP = getPoint2d(e);//cursor position
			moveParticle = PS.getNearestParticle(cursorP);

			/// START MOVING (+ HIGHLIGHT):
			updatePosition(cursorP);
		}
		/** Update moved particle state. */
		private void updatePosition(Point2d newX)
		{
			if(moveParticle==null) return;
			moveParticle.setHighlight(true);
			moveParticle.x. set(newX);
			moveParticle.x0.set(newX);
		}
		/** Update particle. */
		public void mouseDragged(MouseEvent e)
		{
			Point2d cursorP = getPoint2d(e);//cursor position 
			updatePosition(cursorP);
		}

		/** Invokes reset() */
		public void mouseReleased(MouseEvent e) {
			reset();
		}

		/** Disable highlight, and nullify moveParticle. */
		void reset() {  
			if(moveParticle!=null) moveParticle.setHighlight(false);
			moveParticle = null; 
		}

		public void display(GL gl) {}
	}


	/** Creates inter-particle springs. */
	class CreateSpringTask extends Task  
	{
		private Particle p1 = null;
		private Particle p2 = null;
		private Point2d  cursorP = null;

		CreateSpringTask() {}

		/** Start making a spring from the nearest particle. */
		public void mousePressed(MouseEvent e) 
		{
			// FIND NEAREST PARTICLE:
			cursorP = getPoint2d(e);//cursor position
			p1 = PS.getNearestParticle(cursorP); /// = constant (since at rest)
			p2 = null;
		}

		/** Update cursor location for display */
		public void mouseDragged(MouseEvent e)
		{
			cursorP = getPoint2d(e);//update cursor position
		}

		/** Find nearest particle, and create a
		 * SpringForce2Particle when mouse released, unless
		 * nearest particle, p2, is same as p1. */
		public void mouseReleased(MouseEvent e) 
		{
			cursorP = getPoint2d(e);//cursor position
			p2      = PS.getNearestParticle(cursorP); /// = constant (since at rest)
			if(p1 != p2) {//make force object
				SpringForce2Particle newForce = new SpringForce2Particle(p1, p2, PS);//params
				PS.addForce(newForce);
				if (autoRigid == true){
					newForce.setLinkHighlight(true);
					PS.rigidSet.add(newForce);
				}
			}
			/// RESET:
			p1 = p2 = null;
			cursorP = null;
		}

		/** Cancel any spring creation. */
		void reset()
		{
			p1 = p2 = null; 
			cursorP = null;
		}

		/** Draw spring-in-progress.  NOTE: created springs are
		 * drawn by ParticleSystem. */
		public void display(GL gl) 
		{
			if(cursorP==null || p1==null) return;

			/// DRAW A LINE:
			gl.glColor3f(1,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2d(cursorP.x, cursorP.y);
			gl.glVertex2d(p1.x.x, p1.x.y);
			gl.glEnd();
		}
	}


	/** Runtime dragging of nearest particle using a spring
	 * force. */
	class DragParticleTask extends Task  
	{
		private Particle dragParticle = null;
		private Point2d  cursorP      = null;

		private SpringForce1Particle springForce = null;

		public void mousePressed(MouseEvent e) 
		{
			// FIND NEAREST PARTICLE:
			cursorP = getPoint2d(e);//cursor position
			//dragParticle = PS.getNearestParticle(cursorP);
			dragParticle = PS.getNearestPinnedParticle(cursorP, false);//unpinned

			if(dragParticle != null) {/// START APPLYING FORCE:
				springForce = new SpringForce1Particle(dragParticle, cursorP, PS);
				PS.addForce(springForce);//to be removed later
			}
		}

		/** Cancel any particle dragging and forces. */
		void reset() {
			dragParticle = null;
			cursorP      = null;
			if(springForce != null)  PS.removeForce(springForce);
		}

		public void mouseDragged(MouseEvent e)
		{
			if(springForce != null) {
				cursorP = getPoint2d(e);//cursor position 

				/// UPDATE DRAG FORCE ANCHOR:
				springForce.updatePoint(cursorP);
			}
		}

		public void mouseReleased(MouseEvent e) 
		{
			cursorP = null;
			dragParticle = null;

			/// CANCEL/REMOVE FORCE: 
			PS.removeForce(springForce);
		}

		public void display(GL gl) {}
	}


	/** Toggle pin constraints. */
	class PinConstraintTask extends Task  
	{
		/** Toggle pin constraint on nearest particle. */
		public void mousePressed(MouseEvent e) 
		{
			Point2d  cursorP = getPoint2d(e);
			Particle p1 = PS.getNearestParticle(cursorP); /// = constant (since at rest)
			if(p1 != null) {// TOGGLE PIN:
				p1.setPin( !p1.isPinned() );
			}
		}
		public void mouseDragged(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
		public void display(GL gl) {}
		void reset() { }
	}


	/** Rigid body constraint. */
	class RigidConstraintTask extends Task  
	{

		/** Toggle rigid constraints on nearest particle. */
		public void mousePressed(MouseEvent e) 
		{
			Point2d  cursorP = getPoint2d(e);
			SpringForce2Particle s1 = PS.getNearestLink(cursorP);

			if(s1 != null) {// TOGGLE SET:

				if( PS.rigidSet.contains(s1) ){

					PS.rigidSet.remove(s1);
					s1.setLinkHighlight(false);
					System.out.println(PS.rigidSet.size() + " Constrained Particles.");

				}else {

					PS.rigidSet.add(s1);
					s1.setLinkHighlight(true);
					System.out.println(PS.rigidSet.size() + " Constrained Particles.");

				}

			}


		}

		public void mouseDragged(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
		public void display(GL gl) {}
		void reset() {}
	}

	private static int exportId = -1;
	private class FrameExporter
	{
		private int nFrames  = 0;
		FrameExporter()  { 
			exportId += 1;
		}
		void writeFrame()
		{ 
			long   timeNS   = -System.nanoTime();
			String number   = Utils.getPaddedNumber(nFrames, 5, "0");
			String filename = "frames/export"+exportId+"-"+number+".png";/// BUG: DIRECTORY MUST EXIST!
			try{  
				java.io.File   file     = new java.io.File(filename);
				if(file.exists()) System.out.println("WARNING: OVERWRITING PREVIOUS FILE: "+filename);
				/// WRITE IMAGE: ( :P Screenshot asks for width/height --> cache in GLEventListener.reshape() impl)
				com.sun.opengl.util.Screenshot.writeToFile(file, width, height);
				timeNS += System.nanoTime();
				System.out.println((timeNS/1000000)+"ms:  Wrote image: "+filename);
			}catch(Exception e) { 
				e.printStackTrace();
				System.out.println("OOPS: "+e); 
			} 
			nFrames += 1;
		}

	}

	/**
	 * ### Runs the ParticleSystemBuilder. ###
	 */
	public static void main(String[] args) 
	{
		try{
			ParticleSystemBuilder psb = new ParticleSystemBuilder();
			psb.start();

		}catch(Exception e) {
			e.printStackTrace();
			System.out.println("OOPS: "+e);
		}
	}
}



