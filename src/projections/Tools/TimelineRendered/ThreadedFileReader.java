package projections.Tools.TimelineRendered;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.SortedSet;
import java.util.TreeSet;

import projections.Tools.Timeline.Data;
import projections.Tools.Timeline.MainHandler;
import projections.Tools.Timeline.MainPanel;
import projections.gui.JPanelToImage;

/** A runnable object that acts like a MainHandler and renders an image of a single PE's Timeline. */
class ThreadedFileReader implements MainHandler, Runnable {
	protected int PE;
	private Color background;
	private Color foreground;
	private long startTime, endTime;
	private BufferedImage image;
	private int width;

	protected ThreadedFileReader(int pe, long startTime, long endTime, Color backgroundColor, Color foregroundColor, int width){
		this.PE = pe;
		this.startTime = startTime;
		this.endTime = endTime;
		this.background = backgroundColor;
		this.foreground = foregroundColor;
		this.width = width;
	}

	public void run() { 

		SortedSet<Integer> validPEs = new TreeSet<Integer>();
		validPEs.add(PE);

		// setup the Data for this panel 
		Data data = new Data(null);
		data.setProcessorList(validPEs);
		data.setRange(startTime, endTime);
		data.setViewType(Data.ViewType.VIEW_COMPACT);
		
		if(background != null && foreground != null)
			data.setColors(background,foreground);

		data.setHandler(this);

		// create a MainPanel for it	
		MainPanel displayPanel = new MainPanel(data, this);
		System.out.println("Calling loadTimelineObjects");
		displayPanel.loadTimelineObjects(false, null, false);

		displayPanel.setSize(width,data.singleTimelineHeight());
		displayPanel.revalidate();
		displayPanel.doLayout();

		image = JPanelToImage.generateImage(displayPanel);
		
		System.out.println("Created image for PE " + PE);

		displayPanel = null;
		data = null;
		
		long oneGB = 1024*1024*1024;
		if(Runtime.getRuntime().freeMemory() < oneGB){
			System.out.println("Calling garbage collector");
			Runtime.getRuntime().gc();
		}
		
	}

	
	public void displayWarning(String message) {
		// do nothing
	}

	public void notifyProcessorListHasChanged() {
		// do nothing
	}

	public void refreshDisplay(boolean doRevalidate) {
		// do nothing
	}

	public void setData(Data data) {
		// do nothing
	}

	public BufferedImage getImage() {
		return image;
	}


}





