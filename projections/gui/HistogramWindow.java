package projections.gui;
import projections.misc.LogEntryData;
import projections.analysis.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class HistogramWindow extends GenericGraphWindow 
    implements ActionListener,ItemListener
{
    static final int NO_OF_BINS = 300;
    static final int FREQUENCY  = 1000;

    // sum total of the execution times of all the entry points selected
    private long totalExecutionTime;	
    // longest EntryPoint execution time
    private long longestExecutionTime;	
    
    //variables to be passed to EntrySelectionDialog
    private EntrySelectionDialog entryDialog;
    private boolean stateArray[][];
    private Color colorArray[][];
    private String [] entryNames; 
    
    // displays the number of EPs in each bin as an ordered pair
    private JTextArea statusArea;		
    // should longest entrypoints be recorded & displayed as a table?
    private boolean recordEP;			
    private EntryPointWindow epFrame;

    // show both range dialog & epdialog during startup
    private boolean startUp;

    // variables (in addition to those in the super class) 
    // to be set by SimpleThresholdDialog 
    public double threshold;

    private HistogramWindow thisWindow;
    
    public HistogramWindow(MainWindow mainWindow, Integer myWindowID)
    {
	super("Projections Histograms", mainWindow, myWindowID);
	thisWindow = this;

	// **Temporary threshold setting (should be read from dialog later)
	threshold = 1000; // 1ms

	setTitle("Projections Histograms");
	setGraphSpecificData();
	  
	int noEPs = Analysis.getNumUserEntries();
	stateArray = new boolean[1][noEPs];	// where should this be?
	for(int i=0; i < noEPs; i++)
	    stateArray[0][i] = true;
	
	String names[][] = Analysis.getEntryNames();
	entryNames = new String[noEPs];
	for(int i=0; i<noEPs ; i++)
	    entryNames[i] = names[i][0];
	
	colorArray = new Color[1][noEPs];
	for(int i=0; i < noEPs; i++)
	    colorArray[0][i] = Analysis.getEntryColor(i);
	
	recordEP = true;	// record longest EPs by default
	epFrame = null;
	// to display the no. of EPs vs bins as text	
	statusArea = new JTextArea(6,2);	
	startUp = true;
	
	createMenus();
	getContentPane().add(getMainPanel());
	
	pack();
	showDialog();
    }   
    
    /* if there is an epFrame existing, dispose it before disposing 
       the window 
    */
    public void close(){
	if(epFrame != null)
	    epFrame.dispose();
	super.close();
    }

    /* 
     *  Show the SimpleThresholdDialog 
     */
    public void showDialog()
    {
	if (dialog == null) {
	    dialog = new SimpleThresholdDialog(this,
					       "Select Histogram Time Range",
					       SimpleThresholdDialog.TIME);
	} else {
	    setDialogData();
	}
	dialog.displayDialog();
	if (!dialog.isCancelled()) {
	    getDialogData();
	    final SwingWorker worker = new SwingWorker() {
		    public Object construct() {
			if (!startUp) {
			    thisWindow.refreshGraph();
			} else {
			    thisWindow.showEntryDialog();
			}
			return null;
		    }
		    public void finished() {
			thisWindow.setVisible(true);
		    }
		};
	    worker.start();
	}
    }

    public void showWindow() {
	// do nothing for now
    }

    public void getDialogData() {
	SimpleThresholdDialog dialog = (SimpleThresholdDialog)this.dialog;
	threshold = dialog.getThreshold();
	// use GenericGraphWindow's method for the rest.
	super.getDialogData();
    }

    public void setDialogData() {
	SimpleThresholdDialog dialog = (SimpleThresholdDialog)this.dialog;
	dialog.setThreshold(threshold);
	super.setDialogData();
    }

    /* Show the EntrySelectionDialog to select Entrypoints to be considered */
    // **CW** 1/12/2004 this should really be the legend panel. Will be fixed
    // once the generic legend panel gets written.
    void showEntryDialog()
    {
	if (startUp) startUp = false;
	
	int noEPs = Analysis.getNumUserEntries();
	String typeLabelStrings[] = {"Entry Points"};

	boolean existsArray[][] = new boolean[1][noEPs];
	for(int i=0; i<noEPs; i++)
		existsArray[0][i] = true;
	  
	if(entryDialog == null)
		 entryDialog = new EntrySelectionDialog(this, typeLabelStrings,stateArray,colorArray,existsArray,entryNames);
	entryDialog.showDialog();
	refreshGraph();
    }

    public void actionPerformed(ActionEvent evt)
    {
	if (evt.getSource() instanceof JMenuItem) {
	    JMenuItem m = (JMenuItem)evt.getSource();
	    if(m.getText().equals("Set Range"))
		showDialog();
	    else if(m.getText().equals("Select Entry Points"))
		showEntryDialog();
	    else if(m.getText().equals("Close"))
		close();
	}
    } 

    public void itemStateChanged(ItemEvent evt)
    {
	recordEP = (evt.getStateChange()==1)?true:false;	// if the itemStateChanged is to 1, then it is selected
	if(recordEP)
	    refreshGraph();
	else
	    epFrame.setVisible(false);
    }
 
    protected JPanel getMainPanel()
    {
	JPanel mainPanel = super.getMainPanel(); 
	mainPanel.add(Box.createRigidArea(new Dimension(0,6)));
	mainPanel.add(new JScrollPane(statusArea,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));
	return mainPanel;
    }  

    protected void setGraphSpecificData(){
	setXAxis("Entry Point Execution Time (us)","us",0,100);
	setYAxis("Instances","");
    }

    protected void refreshGraph()
    {
	// get new counts and redraw the graph
	int [] counts = getCounts();
	setDataSource("",counts);
	super.refreshGraph();
	String firstRow  ="Bin  ", secondRow="EPs  ";	
	String  thirdRow = "Total Execution Time: " + 
	    String.valueOf(totalExecutionTime)+ " us";
	String fourthRow = "Longest Entry Point Execution Time: " +
	    longestExecutionTime +" us";
	for (int i=0; i<counts.length; i++) {
	    if (counts[i]!=0) {
		firstRow = firstRow + i + "\t";
		secondRow = secondRow + counts[i] +"\t";
	    }
	}
	// clear the text area and enter new set
	statusArea.setText("");	
	statusArea.append(firstRow+"\n");
	statusArea.append(secondRow+"\n\n");
	statusArea.append(thirdRow+"\n");
	statusArea.append(fourthRow);
	// print to the screen
    }

    public String[] getPopup(int xVal, int yVal) {
	return null;
    }

    private int[] getCounts()
    {
	int instances = 0;
	totalExecutionTime = 0;
	longestExecutionTime=0;
	
	OrderedIntList tmpPEs = validPEs.copyOf();
	GenericLogReader r;
	if (recordEP) {
	    if (epFrame == null) {
		epFrame = new EntryPointWindow();
		epFrame.setSize(600,600);
	    } else {
		// prepare table to enter new data in place of old
		epFrame.clearTableData();	
	    }
	}
	
	int [] counts = new int[NO_OF_BINS];
	for(int i=0; i<NO_OF_BINS; i++)
	    counts[i] = 0;
	
	LogEntryData logdata,logdata2;
	logdata = new LogEntryData();
	logdata2 = new LogEntryData();
	
	ProgressMonitor progressBar = 
	    new ProgressMonitor(this, "Reading log files",
				"", 0, tmpPEs.size());
	int curPeCount = 0;
	while (tmpPEs.hasMoreElements()) {
	    int pe = tmpPEs.nextElement();
	    if (!progressBar.isCanceled()) {
		progressBar.setNote("Reading data for PE " + pe);
		progressBar.setProgress(curPeCount);
	    } else {
		progressBar.close();
	    }
	    curPeCount++;
	    r = new GenericLogReader(Analysis.getLogName(pe),Analysis.getVersion());
	    try {
		r.nextEventOnOrAfter(startTime,logdata);
		while(true){
		    r.nextEventOfType(ProjDefs.BEGIN_PROCESSING,logdata);
		    r.nextEventOfType(ProjDefs.END_PROCESSING,logdata2);
		    // if the entry method is selected, count it
		    if (stateArray[0][logdata.entry]){			
			long executionTime = (logdata2.time - logdata.time);
			totalExecutionTime += executionTime;

			int diff = (int)(executionTime/FREQUENCY);
			if (diff >= NO_OF_BINS) {
			    // enter the data into the table
			    if (recordEP)
				epFrame.writeToTable(pe,entryNames[logdata.entry],logdata.time,logdata2.time,colorArray[0][logdata.entry]);

			    longestExecutionTime=(executionTime>longestExecutionTime)?executionTime:longestExecutionTime;
			    diff = NO_OF_BINS-1;
			}
			counts[diff]++;
			instances++;
		    }
		    if(logdata2.time > endTime)
			break;
		}
		
	    }catch(EOFException e){
	     	// do nothing just reached end-of-file
	    }catch(Exception e){
		System.out.println("Exception " + e);
		e.printStackTrace();
	    }
	}
	progressBar.close();
	if (recordEP)	
	    epFrame.setVisible(true);
	return(counts);
    }
    
    // override the super class' createMenus(), add any menu items in fileMenu if needed, add any new menus to the menuBar
    // then call super class' createMenus() to add the menuBar to the Window
    protected void createMenus()
    {
	fileMenu = Util.makeJMenu(fileMenu,
				  new Object[]
	    {
		"Select Entry Points"
	    },
				  null,
				  this);
	menuBar.add(Util.makeJMenu("View", 
				   new Object[]
	    {
		new JCheckBoxMenuItem("Show Longest EPs",true)
	    },
				   null,
				   this));
	super.createMenus();
    }
}
