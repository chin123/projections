package projections.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import projections.misc.*;

public class MainWindow extends Frame
   implements ActionListener
{

   private GraphWindow          graphWindow;
   private TimelineWindow       timelineWindow;
   private AnimationWindow      animationWindow;
   private ProfileWindow        profileWindow;
   private HelpWindow           helpWindow;
   private LogFileViewerWindow  logFileViewerWindow;
   private HistogramWindow      histogramWindow;
   private StlWindow            stlWindow;

   private AboutDialog          aboutDialog;

   private MainTitlePanel       titlePanel;
   private MainButtonPanel      buttonPanel;
   private boolean toolsEnabled = false;
   private Image paper;

   private Image bgimage;
   public MainWindow()
   {
	  addWindowListener(new WindowAdapter()
	  {
		 public void windowClosing(WindowEvent e)
		 {
			System.exit(0);
		 }
	  });

	  setBackground(Color.lightGray);

	  CreateMenus();
	  CreateLayout();
	  
   }                              
   public void actionPerformed(ActionEvent evt)
   {
	  if(evt.getSource() instanceof MenuItem)
	  {
		 MenuItem mi = (MenuItem)evt.getSource();
		 String arg = mi.getLabel();
		 
		 if(arg.equals("Open..."))
			ShowOpenFileDialog();
		 else if(arg.equals("Quit"))
			System.exit(0);
		 else if(arg.equals("Index"))
			ShowHelpWindow();
		 else if(arg.equals("About"))
			ShowAboutDialog(this);

		 else if(toolsEnabled)
		 {
			if(arg.equals("Graphs"))
			   ShowGraphWindow();
			else if(arg.equals("Timelines"))
			   ShowTimelineWindow();
			else if(arg.equals("Animations"))
			   ShowAnimationWindow();
			else if(arg.equals("Usage Profile"))
			   ShowProfileWindow();
			else if(arg.equals("View Log Files"))
			   ShowLogFileViewerWindow();
			else if(arg.equals("Histograms"))
			   ShowHistogramWindow();
			else if(arg.equals("Overview"))
			   ShowStlWindow();
		 }
	  }
   }               
   public void CloseGraphWindow()
   {
	  graphWindow = null;
   }   
   public void CloseHistogramWindow()
   {
	  if(histogramWindow != null)
		 histogramWindow = null;
   }   
   public void CloseLogFileViewerWindow()
   {
	  if(logFileViewerWindow != null)
		 logFileViewerWindow = null;
   }   
   public void CloseProfileWindow()
   {
	  profileWindow = null;
   }   
   public void CloseTimelineWindow()
   {
	  timelineWindow = null;
   }   
   private void CreateLayout()
   {
	  try {
		URL imageURL = ((Object)this).getClass().getResource("/projections/images/bgimage");
	  	bgimage = Toolkit.getDefaultToolkit().getImage(imageURL);
	  	Util.waitForImage(this, bgimage);
	  } catch (Exception E) {
		System.out.println("Error loading background image.  Continuing.");
	  }
   
	  GridBagLayout      gbl = new GridBagLayout();
	  GridBagConstraints gbc = new GridBagConstraints();
	  gbc.fill = GridBagConstraints.BOTH;

	  setLayout(gbl);

	  titlePanel  = new MainTitlePanel(this);
	  buttonPanel = new MainButtonPanel(this);

	  Util.gblAdd(this, titlePanel,  gbc, 0,0, 1,1, 1,1,  0, 0, 0, 0);
	  Util.gblAdd(this, buttonPanel, gbc, 0,1, 1,1, 1,0, 10,10,10,10);

	  pack();
   }         
   private void CreateMenus()
   {
	  MenuBar mbar = new MenuBar();

	  mbar.add(Util.makeMenu("File", new Object[]
	  {
		 "Open...",
		 null,
		 "Quit"
	  },
	  this));

	  mbar.add(Util.makeMenu("View", new Object[]
	  {
		 new CheckboxMenuItem("WindowShade")
	  },
	  this));


	  mbar.add(Util.makeMenu("Tools", new Object[]
	  {
		 "Graphs",
		 "Timelines",
		 "Usage Profile",
		 "Animations",
		 "View Log Files",
		 "Histograms",
		 "Overview"
	  },
	  this));


	  Menu helpMenu;
	  mbar.add(helpMenu = Util.makeMenu("Help", new Object[]
	  {
		 "Index",
		 "About"
	  },
	  this));

	  mbar.setHelpMenu(helpMenu);
	  setMenuBar(mbar);
   }   
   public Color getGraphColor(int e)
   {
	  if(graphWindow != null)
		 return graphWindow.getGraphColor(e);
	  else
		 return null;
   }   
   public boolean GraphExists()
   {
	  if(graphWindow != null)
		 return true;
	  else
		 return false;
   }   
   public static void main(String args[])
   {
	  MainWindow f = new MainWindow();
	  f.pack();
	  f.setTitle("Projections");
	  f.setResizable(false);
	  f.setVisible(true);
   }   
   public void paint(Graphics g)
   {
	  if (bgimage!=null) Util.wallPaper(this, g, bgimage);
	  super.paint(g);
   }            
   public void ShowAboutDialog(Frame parent)
   {
	  if(aboutDialog == null)
		 aboutDialog = new AboutDialog(parent);
	  aboutDialog.setVisible(true);
   }   
   public void ShowAnimationWindow()
   {
	  if(animationWindow == null)
		new Thread(new Runnable() {public void run() {
			animationWindow = new AnimationWindow();
			animationWindow.setVisible(true);
	 	}}).start();
		 
   }      
   public void ShowGraphWindow()
   {
	   if(graphWindow == null)
		 graphWindow = new GraphWindow(this);
   }   
   public void ShowHelpWindow()
   {
	  if(helpWindow == null)
		 helpWindow = new HelpWindow(this);
	  helpWindow.setVisible(true);
   }   
   public void ShowHistogramWindow()
   {
	  if(histogramWindow == null)
		 histogramWindow = new HistogramWindow(this);
	  histogramWindow.setVisible(true);
   }   
   public void ShowLogFileViewerWindow()
   {
	  if(logFileViewerWindow == null)
		 logFileViewerWindow = new LogFileViewerWindow(this);
	  logFileViewerWindow.setVisible(true);
   }   
   public void ShowOpenFileDialog()
   {
	  buttonPanel.disableButtons();
	  FileDialog d = new FileDialog((Frame)this, "Select .sts file to load", FileDialog.LOAD);

	  d.setDirectory(".");
	  d.setFilenameFilter(new FilenameFilter() {
	    public boolean accept(File dir, String name) {
	      //System.out.println("Asked to filter "+name);//Never gets called!
	      return name.endsWith(".sts");
	    }
	  });
	  d.setFile("pgm.sts");
	  d.setVisible(true);

	  String filename = d.getFile();
	  if(filename == null)
		 return;
	  filename = d.getDirectory() + filename;
	  try
	  {
		 filename = filename.substring(0, filename.length()-4);
		 Analysis.initAnalysis(filename);
		 toolsEnabled = true;
		 buttonPanel.enableButtons();
	  }
	  catch(IOException e)
	  {
		 InvalidFileDialog ifd = new InvalidFileDialog(null);
		 ifd.setVisible(true);
	  }
	  catch(StringIndexOutOfBoundsException e)
	  {
		 InvalidFileDialog ifd = new InvalidFileDialog(null);
		 ifd.setVisible(true);
	  }
   }                     
   public void ShowProfileWindow()
   {
	  if(profileWindow == null)
	  	profileWindow = new ProfileWindow(this, null);
	  profileWindow.setVisible(true);
   }               
   public void ShowStlWindow()
   {
	  new Thread(new Runnable() {public void run() {
	  	stlWindow = new StlWindow();
	  }}).start();
   }                              
   public void ShowTimelineWindow()
   {
	  if(timelineWindow == null)
		 timelineWindow = new TimelineWindow(this);
   }   
   public void update(Graphics g)
   {
	  paint(g);
   }   
}
