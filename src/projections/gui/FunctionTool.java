package projections.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Paint;
import java.awt.event.ActionEvent;

import java.io.IOException;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import projections.analysis.CallStackManager;
import projections.analysis.EndOfLogSuccess;
import projections.analysis.GenericLogReader;
import projections.analysis.ProjDefs;
import projections.misc.LogEntryData;

/**
 *  FunctionTool.java
 *  by Chee Wai Lee
 *  9/14/2004
 *
 *  This is a temporary place holder for a tool to deal with
 *  functions.  It will form the basis for a general profile facility
 *  for all activities.
 *
 */
class FunctionTool extends GenericGraphWindow
    implements PopUpAble
{
  

	// Temporary hardcode. This variable will be assigned appropriate
    // meaning in future versions of Projections that support multiple
    // runs.
	private int myRun = 0;
    
    // local GUI components
    private JPanel mainPanel;
    private JPanel graphPanel;
    private JPanel radioButtonPanel;

    private JRadioButton countCB;
    private JRadioButton timeCB;
    // data
    private double countData[][];
    private double timeData[][];
    private String currentArrayName = "";

    private FunctionTool thisWindow;

    protected FunctionTool(MainWindow mainWindow) {
	super("Function tracing", mainWindow);
	mainPanel = new JPanel();
	setLayout(mainPanel);
	createLayout();
	super.createMenus();
	setPopupText("timeData");
	pack();
	thisWindow = this;
	showDialog();
    }

    private void setPopupText(String input){
	currentArrayName = input;
    }

    private void createLayout() {
	GridBagConstraints gbc = new GridBagConstraints();
	GridBagLayout gbl = new GridBagLayout();

	gbc.fill = GridBagConstraints.BOTH;
	mainPanel.setLayout(gbl);

	graphPanel = getMainPanel();
	radioButtonPanel = new JPanel();

	ButtonGroup group = new ButtonGroup();
	countCB = new JRadioButton("Call Counts", false);
	timeCB = new JRadioButton("Time Spent", true);
	group.add(countCB);
	group.add(timeCB);

	countCB.addActionListener(this);
	timeCB.addActionListener(this);

	Util.gblAdd(radioButtonPanel, countCB, gbc, 0,0, 1,1, 1,1);
	Util.gblAdd(radioButtonPanel, timeCB,  gbc, 1,0, 1,1, 1,1);

	Util.gblAdd(mainPanel, graphPanel, gbc, 0,1, 1,1, 1,1);
	Util.gblAdd(mainPanel, radioButtonPanel, gbc, 0,2, 1,1, 0,0);
    }



	/** A class that provides the colors for the display */
	public class FunctionColorer implements GenericGraphColorer {
		public Paint[] getColorMap() {
			return MainWindow.runObject[myRun].getFunctionColors();
		}
	}
    
    
    
    
    
    protected void setGraphSpecificData(){
	if (currentArrayName.equals("timeData")) {
	    setDataSource("Total Function Time", timeData, 
	    		new FunctionColorer(), thisWindow);
	    setXAxis("Processor", "");
	    setYAxis("Time Spent in Function", "us");
	} else if (currentArrayName.equals("countData")) {
	    setDataSource("Total Function Calls", countData, 
			  new FunctionColorer(), thisWindow);
	    setXAxis("Processor", "");
	    setYAxis("# Times Called", "");
	}
	super.refreshGraph();
    }

    public void showDialog() {
	if (dialog == null) {
		dialog = new RangeDialog(this, "select Range", null, false);
	}
	
	dialog.displayDialog();
	if (!dialog.isCancelled()){

	    final SwingWorker worker =  new SwingWorker() {
	    	public Object doInBackground() {
	    		getData();
	    		return null;
	    	}
	    	public void done() {
	    		setGraphSpecificData();
	    		thisWindow.setVisible(true);
	    		thisWindow.repaint();
	    	}
	    };
	    worker.execute();
	}
    }

    public void repaint() {
	super.refreshGraph();
    }

    private void getData() {
	// setup the reader to read all data.
	LogEntryData logEntry = new LogEntryData();
	OrderedIntList validPEs = MainWindow.runObject[myRun].getValidProcessorList();
	validPEs.reset();
	int numFunc = MainWindow.runObject[myRun].getNumFunctionEvents();
	CallStackManager stack = new CallStackManager();
	int activeThread[] = new int[3];

	// read data and look only for functions.
	countData = new double[validPEs.size()][numFunc];
	timeData = new double[validPEs.size()][numFunc];
	int pe;

	int curPeArrayIndex = 0;
	ProgressMonitor progressBar =
	    new ProgressMonitor(this, "Reading log files",
				"", 0, validPEs.size());
	while (validPEs.hasMoreElements()) {
	    pe = validPEs.nextElement();
		GenericLogReader reader = new GenericLogReader(pe, MainWindow.runObject[myRun].getVersion());
		try {
		if (!progressBar.isCanceled()) {
		    progressBar.setNote("[PE: " + pe + " ] Reading data");
		    progressBar.setProgress(curPeArrayIndex+1);
		    validate();
		} else {
		    progressBar.close();
		    return;
		}
		double lastFuncTime = 0.0;
		Integer stackEntry;
		// find first begin processing event.
		logEntry = reader.nextEventOfType(ProjDefs.BEGIN_PROCESSING);
		while (true) {
		    switch (logEntry.type) {
		    case ProjDefs.BEGIN_PROCESSING:
			// marks the beginning of an MPI thread segment.
			if (logEntry.entry == 0) { // if dummy thread ep
			    // set the current function's time. (it's not
			    // important to know what that function is -
			    // that information is maintained in the stack
			    // structure and used later.
			    lastFuncTime = logEntry.time;
			
			    // set active thread
			    activeThread[0] = logEntry.id[0];
			    activeThread[1] = logEntry.id[1];
			    activeThread[2] = logEntry.id[2];
			}
			break;
		    case ProjDefs.END_PROCESSING:
			// marks the end of an MPI thread segment (which may
			// be an explicit suspension as a result of a receive)
			if (logEntry.entry == 0) { // if dummy thread ep
			    // get the last function on the stack and update
			    // the information for that function.
			    stackEntry = 
				(Integer)stack.read(activeThread[0],
						    activeThread[1],
						    activeThread[2]);
			    if (stackEntry != null) {
				timeData[curPeArrayIndex][stackEntry.intValue()] +=
				    logEntry.time - lastFuncTime;
			    }

			    // DO NOT mark the next timestamp. No valid
			    // function execution should occur after this
			    // point.

			    // We set the activeThread to -1 to catch
			    // possible problems when a function event
			    // is detected when not within a proper
			    // active thread.
			    activeThread[0] = -1;
			    activeThread[1] = -1;
			    activeThread[2] = -1;
			}
			break;
		    case ProjDefs.BEGIN_FUNC:
			// update informtation for the last function (if any)
			stackEntry = 
			    (Integer)stack.read(activeThread[0],
						activeThread[1],
						activeThread[2]);
			if (stackEntry != null) {
			    timeData[curPeArrayIndex][stackEntry.intValue()] 
				+= logEntry.time - lastFuncTime;
			}
			// push function onto current thread id's stack.
			stack.push(new Integer(logEntry.entry), 
				   activeThread[0],
				   activeThread[1],
				   activeThread[2]);
			// mark the next timestamp
			lastFuncTime = logEntry.time;
			countData[curPeArrayIndex][logEntry.entry] += 1.0;
			break;
		    case ProjDefs.END_FUNC:
			// pop function off the stack.
			stackEntry = 
			    (Integer)stack.pop(activeThread[0],
					       activeThread[1],
					       activeThread[2]);
			if (stackEntry != null) {
			    // further check for consistency
			    if (logEntry.entry != stackEntry.intValue()) {
				System.err.println("ERROR: Function end " +
						   "type " + logEntry.entry +
						   " does not match stack " +
						   "type " +
						   stackEntry.intValue());
				System.exit(-1);
			    }
			    timeData[curPeArrayIndex][logEntry.entry] +=
				logEntry.time - lastFuncTime;
			} else {
			    // Something terrible has gone wrong.
			    System.err.println("ERROR: Impossible for " +
					       "an empty stack when " +
					       "processing end of function");
			    System.exit(-1);
			}
			// mark the next timestamp
			lastFuncTime = logEntry.time;
			break;
		    }
		    logEntry = reader.nextEvent();
		}
	    } catch (EndOfLogSuccess e) {
	    	// Successfully read the log file
	    } catch (IOException e) {
	    	System.err.println("Failed to read log at processor [" + pe + "]");
	    }
	    
		try {
			reader.close();
		} catch (IOException e1) {
			System.err.println("Error: could not close log file reader for processor " + pe );
		}
	    
	    curPeArrayIndex++;
	}
	progressBar.close();
    }

    public void actionPerformed(ActionEvent ae){
	if(ae.getSource() instanceof JRadioButton){
	    JRadioButton cb = (JRadioButton)ae.getSource();
	    if(cb == countCB) {
		setPopupText("countData");
		setGraphSpecificData();
	    } else if(cb == timeCB) {
		setPopupText("timeData");
		setGraphSpecificData();
	    }
	}
	super.actionPerformed(ae);
    }

    public String[] getPopup(int pe, int funcID) {
	String popupText[] = new String[3];

	if (currentArrayName.equals("timeData")) {
	    popupText[0] = "Processor: " + pe;
	    popupText[1] = "Function: " + MainWindow.runObject[myRun].getFunctionName(funcID);
	    popupText[2] = "Time spent (us): " + (long)(timeData[pe][funcID]);
	} else if (currentArrayName.equals("countData")) {
	    popupText[0] = "Processor: " + pe;
	    popupText[1] = "Function: " + MainWindow.runObject[myRun].getFunctionName(funcID);
	    popupText[2] = "Times called: " + (int)(countData[pe][funcID]);
	}

	return popupText;
    }
}
