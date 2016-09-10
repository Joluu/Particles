package Applet;

/**
 * Default constants.
 * 
 * @author Doug James, February 2009
 */
public interface Constants
{
    /** Mass-proportional damping. */
    public static final double DAMPING_MASS      = 1; 

    /** Mass of a particle. */
    public static final double PARTICLE_MASS     = 1.0;

    /** Spring stretching stiffness. */
//    public static double STIFFNESS_STRETCH = 10000.0; 
    
    /** numerical damping */
//    public static double ks    = STIFFNESS_STRETCH/2; 
    
//    public static double kd    = STIFFNESS_STRETCH/2; 


    /** Spring bending stiffness. */
    public static final double STIFFNESS_BEND    = 4.; 
    

    
    public static final double GRAVITY    = 10f; 
    
    /** Commands for File Menu */
	public static final String OPEN = "Open";
	public static final String SAVE = "Save";
	public static final String SAVE_AS = "Save As...";


}

