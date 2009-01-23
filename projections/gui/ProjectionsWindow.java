package projections.gui;

import java.awt.event.*;
import javax.swing.*;
import java.awt.*;

/**
 *  ProjectionsWindow
 *  written by Chee Wai Lee
 *  6/28/2002
 *  changed by Sindhura Bandhakavi
 *  8/1/2002
 *  major refactoring for ease-of-use by Chee Wai Lee
 *  12/15/2003
 *
 *  This class should be inherited by all projections tools.
 *  The user may choose to include a dialog box whose base class
 *  allows the user to input a range of processors and a time interval.
 *
 *  The implementer would need to implement (otherwise abstract) methods to
 *  support the basic RangeDialog object (if any). This includes data
 *  communication mechanisms between dialog and window.
 *
 *  The implementer would also have to define various public global variables
 *  in the concrete class to hold data for parameters, either from range
 *  dialogs or directly set.
 *
 *  e.g. public OrderedIntList validPEs;
 *       public long startTime;
 *       public long endTime;
 *
 */

public abstract class ProjectionsWindow
    extends JFrame
    implements ActionListener
{
    private Button window[];

    
    /**
     *  reference to the main window
     */
    protected MainWindow parentWindow;

    // NOTE: There NEED NOT be a dialog.
    public RangeDialog dialog;

    /**
     *  All implementing classes MUST use windowInit() to set up
     *  ProjectionWindow's default parameters. These values will
     *  then be passed on to any dialog creation.
     *
     *  IMPLEMENTATION NOTE:
     *  The implementing class can choose to either allow the parent
     *  class to dictate default values or set it's own default
     *  parameter values.
     *  
     *  DANGER NOTE:
     *  windowInit() is called at the beginning of the class's constructor
     *  
     */
    protected abstract void windowInit();

    /**
     *  Must implement code to show the dialog box and handle
     *  the return values. There NEED NOT be a dialog.
     *  The dialog blocks until the user hits OK or CANCEL.
     *
     *  Depending on the kind of behavior required, developers of
     *  ProjectionsWindows should decide whether or not they wish
     *  to call setDialogData();
     */
    protected abstract void showDialog();

    
    /**
     *  Must implement code to set parameter data to the window
     *  (more specifically the tool) via the dialog. Because of the
     *  arbitrary nature of parameter data in projections, this has
     *  to be in the form of global variables:
     *  e.g.     getDialogData() {
     *              validPEs = dialog.validPEs;
     *              startTime = dialog.startTime;
     *              endTime = dialog.endTime;
     *           }
     *
     *  This is intended to be used after the showDialog code.
     *  e.g.     showDialog();
     *           if (!dialog.isCancelled()) {
     *             getDialogData();
     *             ... blah blah blah ...
     *           }
     */
    protected abstract void getDialogData();


    /**
     *  Subclasses of ProjectionsWindow are *expected* to set the
     *  dialog's parameter variables by either (or both):
     *
     *  1) using the dialog's accessors.
     *  2) directly setting the dialog's public parameter variables.
     *
     *  The exact manner this is done will depend on how the dialog
     *  is actually implemented.
     *
     *  This should be done before calling super.setDialogData();
     *
     */
    protected void setDialogData() {
	if (dialog != null) {
	    dialog.updateFields();
	}
    }


    public ProjectionsWindow(MainWindow parentWindow) {
    	this("", parentWindow);
    }

    public ProjectionsWindow(String title, MainWindow parentWindow) {
    	this.parentWindow = parentWindow;
    	setTitle(title);
    	addWindowListener(new WindowAdapter() {
    		public void windowClosing(WindowEvent e){
    			close();
    		}
    	});
    	// FIXME:  Dangerous because we call a subclass's method before the associated object has been fully constructed(we are still in the constructor here
    	windowInit(); 
    }

    public void setLayout(JPanel mainPanel) {
	GridBagConstraints gbc = new GridBagConstraints();
	GridBagLayout gbl = new GridBagLayout();
	
	gbc.fill = GridBagConstraints.BOTH;
	JPanel contentPane = (JPanel) getContentPane();
	contentPane.setLayout(gbl);
	
	Util.gblAdd(contentPane, mainPanel, gbc, 0,1, 1,1, 1,1);
    }
    
    public void actionPerformed(ActionEvent ae){
	if (ae.getSource() instanceof Button) {
	    Button b = (Button)ae.getSource();
	    for(int k=0; k < MainWindow.NUM_WINDOWS;k++) {
		if (b == window[k]) {
		    break;
		}
	    }
	}
    }
    
    // close (and destroy all access) to the window
    public void close(){
    	dispose();
    	parentWindow.closeChildWindow(this);
    }

    /** A method to be overridden by any subclasses that wish to be able to have a new PE loaded from within another tool. */
	public void addProcessor(int pe) {
	}
    
    
    
}
