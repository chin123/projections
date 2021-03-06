package projections.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Iterator;
import java.util.SortedSet;

import javax.swing.JPanel;

class AnimationDisplayPanel extends JPanel
    implements MouseMotionListener , MouseListener
{

	// Temporary hardcode. This variable will be assigned appropriate
    // meaning in future versions of Projections that support multiple
    // runs.
	private int myRun = 0;

    private float MAXHUE = (float)0.65;
    private int numPs = -1;
    private int numIs = -1;
    private int pwidth;
    private int pheight;
    private int numrows;
    private int numcols;
    private int pold = -1;
    private int phoffset;
    private int pvoffset;
    private int hoffset;
    private int voffset;
    private float psize = (float)0.75;
    private float fontOffset = (float)0.90;
    private FontMetrics fm = null;
    private Font font = null;
    private SortedSet<Integer> chosenPEs;
    

    // -1 is an initial "invalid" value. Once a valid is set by someone,
    // this -1 value goes away forever.
    private int curI = -1;  
    private int curP = -1;
    private long Isize = 0; //Interval length, microseconds
    private int[][] data;
    private Image offscreen;
   
    private Color[] colors;
   
    private int w;
	private int h;
   
    private AnimationWindow animationWindow;
   
    protected AnimationDisplayPanel(AnimationWindow animationWindow)
    {
	this.animationWindow = animationWindow;
	setBackground(MainWindow.runObject[myRun].background);

	addComponentListener(new ComponentAdapter()
	    {
		public void componentResized(ComponentEvent evt)
		{
		    w = getSize().width;
		    h = getSize().height;
		    if (w > 0 && h > 0) {
			offscreen = createImage(w, h); 
			
	   		numcols = (int)Math.sqrt(numPs)-(int)(Math.sqrt(numPs)%5);
	    		if (numcols == 0) { numcols = Math.min(numPs, 10); }
			numrows = (int)Math.ceil((double)numPs/numcols);
			pwidth  = Math.min(w/numcols, h/numrows);
			pheight = pwidth;
			
			hoffset  = (w-numcols*pwidth)/2;
			voffset  = (h-numrows*pheight)/2;
			phoffset = (int)((1-psize)*pwidth)/2;
			pvoffset = (int)((1-psize)*pheight)/2;
			
			clearScreen();
		    }   
		    
		    w = getSize().width;
		    h = getSize().height;
		    
		}
	    }); 
          
	addMouseMotionListener(this);
	addMouseListener(this);  
          
	colors = new Color[101];
	for (int i=0; i<=100; i++) {
	    colors[i] = Color.getHSBColor((float)((100-i)/100.0)*MAXHUE,1,1);
	}
	// parameters are assumed to be set and available in AnimationWindow
	setParameters(); 
    }

    private void clearScreen()
    {
	if (offscreen == null) {
	    return;
	}
	Graphics og = offscreen.getGraphics();
	if (og != null) {
	    og.clearRect(0, 0, w, h);
	}
	repaint();   
    }   

    public int getCurI()
    {
	return curI;
    }   
    
    //sharon add getNumI() for AnimationWindow slider bar
     public int getNumI()
    {
	return numIs;
    }   
    
    //Make sure we aren't made too tiny
    public Dimension getMinimumSize() {return new Dimension(150,100);}   
    public Dimension getPreferredSize() {return new Dimension(550,400);}   

    private void makeNextImage(Graphics g, int I)
    {
	int tothoffset = phoffset + hoffset;
	int totvoffset = pvoffset + voffset;
	int pw = (int)(pwidth*psize);
	int ph = (int)(pheight*psize);
          
	g.translate(tothoffset, totvoffset);
	
	if(font == null){
	    font = new Font("Times New Roman",Font.BOLD,12);
	    g.setFont(font);
	    fm = g.getFontMetrics(font);		    
	}

	int p = 0;
	int curPE = 0;
	Iterator<Integer> iter = chosenPEs.iterator();
	for (int r=0; r<numrows; r++) {
	    for (int c=0; c<numcols; c++) {
		int usage = data[p++][I];
		curPE = iter.next();
		if (usage >=0 && usage <=100) {
		    g.setColor(colors[usage]);
		    g.fillRect(c*pwidth, r*pheight, pw, ph); 

		    if ((fm.stringWidth(String.valueOf(numPs)) <= (fontOffset*pw))
		    && (fm.getHeight() <= (fontOffset*ph))) {
		    	g.setColor(Color.black);
			g.drawString(String.valueOf(curPE),
			    c*pwidth + (pw-fm.stringWidth(String.valueOf(curPE)))/2,
			    r*pheight + (ph+fm.getHeight())/2);		    
		    }   
		} 
		if (p >= numPs) {
		    break;
		}
	    }
	}
    }   

    public void mouseClicked(MouseEvent evt)
    {}   
    public void mouseDragged(MouseEvent evt)
    {}   
    public void mouseEntered(MouseEvent evt)
    {}   
    public void mouseExited(MouseEvent evt)
    {
	pold = -1;
	animationWindow.setStatusInfo(-1, -1, -1);
    }   

    public void mouseMoved(MouseEvent evt)
    {
	if (pwidth <=0 || pheight <=0) {
	    return;
	}
	int row = (evt.getY() - voffset) / pheight;
	int col = (evt.getX() - hoffset) / pwidth;
	
	curP = row * numcols + col;
          
	if (curP >= numPs || curP < 0 || 
	    row < 0 || col < 0 || 
	    row >= numrows || col >= numcols) {
	    curP = -1;
	}
	if (curP != pold) {
	    pold = curP;
	    // need to translate curP to actual PE number
	    int count = 0;
	    int pe = 0;
	    Iterator<Integer> iter = animationWindow.selectedPEs.iterator();
	    while (count <= curP) {
		pe = iter.next();
		count++;
	    }
	    if ((curP >= 0) && (curI != -1)) {
		animationWindow.setStatusInfo(pe, curI, data[curP][curI]);
	    } else {
		animationWindow.setStatusInfo(-1, -1, -1);   
	    }   
	}   
    }
    public void mousePressed(MouseEvent evt)
    {}   
    public void mouseReleased(MouseEvent evt)
    {}
   
    public void paint(Graphics g)
    {
	if (offscreen == null) {
	    return;
	}
	if (curI != -1) {
	    makeNextImage(offscreen.getGraphics(), curI);
	}
	g.drawImage(offscreen, 0, 0, null);
    }   

    public void setCurI(int i)
    {
	curI = (i % numIs);
	if (curI < 0) {
	    curI += numIs;
	}
	if (curP >= 0 && curP < numPs) {
	    animationWindow.setStatusInfo(curP, curI, data[curP][curI]);
	}
	repaint();        
    }   

    protected void setParameters()
    {
	SortedSet<Integer> selectedPEs = animationWindow.selectedPEs;
	chosenPEs = selectedPEs;
	numPs = selectedPEs.size();
	Isize = animationWindow.intervalSize;
	data = getAnimationData(Isize,
				animationWindow.startTime, 
				animationWindow.endTime,
				selectedPEs);
	numIs = data[0].length;
	if (numIs > 0) {
	    curI = 0;
	}
	
	w = getSize().width;
	h = getSize().height;
	if (w>0 && h>0) {
	    numcols = (int)Math.sqrt(numPs)-(int)(Math.sqrt(numPs)%5);
	    if (numcols == 0) { numcols = Math.min(numPs, 10); }
	    numrows = (int)Math.ceil((double)numPs/numcols);
	    pwidth  = Math.min(w/numcols, h/numrows);
	    pheight = pwidth;
          
	    hoffset  = (w-numcols*pwidth)/2;
	    voffset  = (h-numrows*pheight)/2;
	    phoffset = (int)((1-psize)*pwidth)/2;
	    pvoffset = (int)((1-psize)*pheight)/2;
	    
	    animationWindow.setTitleInfo(curI);
	    clearScreen();
	}
    }   

    private int[][] getAnimationData(long intervalSize, 
				    long startTime, long endTime, 
				    SortedSet<Integer> desiredPEs) {
	if (intervalSize >= endTime-startTime) {
	    intervalSize = endTime-startTime;
	}
	int startI = (int)(startTime/intervalSize);
	int endI = (int)(endTime/intervalSize);
	int numPs = desiredPEs.size();
	MainWindow.runObject[myRun].LoadGraphData(intervalSize,startI,endI-1,false, desiredPEs);
	int[][] animationdata = new int[ numPs ][ endI-startI ];
	int p = 0;
	while(p < numPs){
	    for( int t = 0; t <(endI-startI); t++ ){
		animationdata[ p ][ t ] = 
		    MainWindow.runObject[myRun].getSystemUsageData(1)[ p ][ t ];
	    }
	    p++;
	}
	return animationdata;
    }

    public void update(Graphics g)
    {
	paint(g);
    }      
}
