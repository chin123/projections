package projections.gui;

import java.util.Vector;
import java.io.*;
import java.awt.*;

import projections.analysis.*;
import projections.misc.*;

public class TimelineData
{
  // IF YOU MAKE A STUPID NAME FOR A VARIABLE AT LEAST COMMENT IT
  // SHEESH!!!  --JMU
  // what do these mean for God's sake ? -- sayantan

   int vpw, vph;
   // is this timeline width and height ?
   int tlw, tlh;
   int lcw;
   int ath, abh;
   int sbw, sbh;
   int mpw, mph;
  public int tluh;
  public int barheight;
   int numPs;
   
   float scale;
  public int   offset;
   
  public OrderedIntList processorList;
   OrderedIntList oldplist;
   String         processorString;
   String         oldpstring;
// boolean for testing if entries are to be colored by Object ID
   
  public boolean colorbyObjectId;
   

  public double         pixelIncrement;
  public int            timeIncrement;
   int            labelIncrement;
   int            numIntervals;
   

   int[]          entries;
   Color[]        entryColor;
   
   public TimelineObject[][] tloArray;
   public Vector [] mesgVector;	  
	
   UserEvent[][] userEventsArray = null;
   
   TimelineDisplayCanvas displayCanvas;
   
   int xmin, xmax;
   long xmintime, xmaxtime;
   int  xminpixel, xmaxpixel;
   
   float[] processorUsage;
   float[] idleUsage;
   float[] packUsage;
   OrderedUsageList[] entryUsageList;
   
  public long beginTime, endTime, totalTime;
   long oldBT, oldET;
   
   boolean showPacks, showIdle, showMsgs;

   public TimelineWindow timelineWindow;
   
   // points for line joining the creation of a message and its beginning of execution
  public Vector mesgCreateExecVector;

   
   public TimelineData(TimelineWindow timelineWindow)
   {
	  showPacks = false;
	  showMsgs  = true;
	  showIdle  = false;

	  oldBT = -1;
	  oldET = -1;
	  oldplist = null;
	  oldpstring = null;
	  
	  this.timelineWindow = timelineWindow;
	  displayCanvas = timelineWindow.displayCanvas;
	  lcw = 100;
	  sbw = 20;
	  sbh = 20;   
	  barheight = 20;
	  tluh = barheight + 20;
	  numPs = 0;
	  ath = 50;
	  scale = 1;
	  processorUsage = null;
	  entryUsageList = null;
   
	  processorString = Analysis.getValidProcessorString();
	  
	  offset = 10;
	  pixelIncrement = 5.0;
	  timeIncrement  = 100;
	  labelIncrement = 5;
	  numIntervals = 1;
	  beginTime = 0;
	  totalTime = Analysis.getTotalTime();
	  endTime = totalTime;
	  xmin = 0;
	  xmax = numIntervals;
	  xmintime = 0;
	  xmaxtime = 1;
	  xminpixel = 0;
	  xmaxpixel = 1;
	  
	  mesgCreateExecVector = new Vector();
	  
	  tloArray = null;
	  mesgVector = null;
	  entries = new int[Analysis.getNumUserEntries()];
	  entryColor = new Color[Analysis.getNumUserEntries()];
	  float H = (float)1.0;
	  float S = (float)1.0;
	  float B = (float)1.0;
	  float delta = (float)(1.0/Analysis.getNumUserEntries());
	  if (new File(Analysis.getLogDirectory() +
		       File.separator + "color.map").exists()) {
	      try {
		  Util.restoreColors(entryColor, "Timeline Graph");
	      } catch (IOException e) {
		  System.err.println("unable to load color.map");
	      } 
	  } else {
	      for(int i=0; i<Analysis.getNumUserEntries(); i++)
		  {
		      entries[i] = 0;
		      entryColor[i] = Analysis.getEntryColor(i);
		  }   
	  }
		 
   }   
   public void createTLOArray()
   {
	//	System.out.println("createTLOArray() called in TimelineData \n");
	  TimelineObject[][] oldtloArray = tloArray;
	  UserEvent[][] oldUserEventsArray = userEventsArray;
	  mesgVector = new Vector[Analysis.getNumProcessors()];
	  for(int i=0;i < Analysis.getNumProcessors();i++){
	  	mesgVector[i] = null;
	  }
	  
	  tloArray = new TimelineObject[processorList.size()][];
	  userEventsArray = new UserEvent[processorList.size()][];
	  
	  if(oldtloArray != null && beginTime >= oldBT && endTime <= oldET)
	  {
		 int oldp, newp;
		 int oldpindex=0, newpindex=0;
		 
		 processorList.reset();
		 oldplist.reset();

		 newp = processorList.nextElement();
		 oldp = oldplist.nextElement();
		 while(newp != -1)
		 {
			while(oldp != -1 && oldp < newp)
			{
			   oldp = oldplist.nextElement();
			   oldpindex++;
			}   
			if(oldp == -1)
			   break;
			if(oldp == newp)
			{
			   if(beginTime == oldBT && endTime == oldET) {
			     tloArray[newpindex] = oldtloArray[oldpindex];
			     userEventsArray[newpindex] = oldUserEventsArray[oldpindex];
			   }
			   else
			   {
			     // copy timelineobjects from larger array into smaller array
			     int n;
			     int oldNumItems = oldtloArray[oldpindex].length;
			     int newNumItems = 0;
			     int startIndex  = 0;
			     int endIndex    = oldNumItems - 1;
				  
			     // calculate which part of the old array to copy
			     for(n=0; n<oldNumItems; n++) {
			       if(oldtloArray[oldpindex][n].getEndTime() < beginTime) { startIndex++; }
			       else { break; }
			     }
			     for(n=oldNumItems-1; n>=0; n--) {
			       if(oldtloArray[oldpindex][n].getBeginTime() > endTime) { endIndex--; }
			       else { break; }
			     }
			     newNumItems = endIndex - startIndex + 1;

			     // copy the array
			     tloArray[newpindex] = new TimelineObject[newNumItems];
			     mesgVector[newp] = new Vector();
			     for(n=0; n<newNumItems; n++) {
			       tloArray[newpindex][n] = oldtloArray[oldpindex][n+startIndex];
			       tloArray[newpindex][n].setUsage();
			       tloArray[newpindex][n].setPackUsage();
			       for(int j=0;j<tloArray[newpindex][n].messages.length;j++)
				       mesgVector[newp].addElement((TimelineMessage)tloArray[newpindex][n].messages[j]);
			     }

			     // copy user events from larger array into smaller array
			     if (oldUserEventsArray != null && oldUserEventsArray[oldpindex] != null) {
			       oldNumItems = oldUserEventsArray[oldpindex].length;
			       newNumItems = 0;
			       startIndex = 0;
			       endIndex = oldNumItems -1;
			       
			       // calculate which part of the old array to copy
			       for (n=0; n<oldNumItems; n++) {
				 if (oldUserEventsArray[oldpindex][n].EndTime < beginTime) { startIndex++; }
				 else { break; }
			       }
			       for (n=oldNumItems-1; n>=0; n--) {
				 if (oldUserEventsArray[oldpindex][n].BeginTime > endTime) { endIndex--; }
				 else { break; }
			       }
			       newNumItems = endIndex - startIndex + 1;
			       
			       // copy the array
			       userEventsArray[newpindex] = new UserEvent[newNumItems];
			       for (n=0; n<newNumItems; n++) {
				 userEventsArray[newpindex][n] = oldUserEventsArray[oldpindex][startIndex+n];
			       }
			     }
			   }
			}                                       
		 
			newp = processorList.nextElement();
			newpindex++;
		 }   
		 oldtloArray = null;
		 oldUserEventsArray = null;
	  }
	  
	  int pnum;
	  processorList.reset();
	  int numPEs = processorList.size();
	  ProgressDialog bar = new ProgressDialog("Reading timeline data");
	  for(int p=0; p<numPEs; p++)
	  {
	      if (!bar.progress(p+1, numPEs, (p+1) + " of " + numPEs)) {
		  break;
	      }
		 pnum = processorList.nextElement();
		 if(tloArray[p] == null) { tloArray[p] = getData(pnum, p); }
	  }
	  bar.done();
	  for(int e=0; e<Analysis.getNumUserEntries(); e++)
		 entries[e] = 0;
	  
	  processorUsage = new float[tloArray.length];
	  entryUsageList = new OrderedUsageList[tloArray.length];
	  float[] entryUsageArray = new float[Analysis.getNumUserEntries()];
	  idleUsage  = new float[tloArray.length];
	  packUsage  = new float[tloArray.length];
	  
	  for(int p=0; p<tloArray.length; p++)
	  {
		 processorUsage[p] = 0;
		 idleUsage[p] = 0;
		 packUsage[p] = 0;
		 for(int i=0; i<Analysis.getNumUserEntries(); i++)
			entryUsageArray[i] = 0;
			
		 for(int n=0; n<tloArray[p].length; n++)
		 {
			float usage = tloArray[p][n].getUsage();
			int entrynum = tloArray[p][n].getEntry();
			if(entrynum >=0)
			{
			   entries[entrynum]++;
			   processorUsage[p] += usage;
			   packUsage[p] += tloArray[p][n].getPackUsage();
			   entryUsageArray[entrynum] += tloArray[p][n].getNetUsage();
			}
			else
			   idleUsage[p] += usage;
		 }
		 
		 entryUsageList[p] = new OrderedUsageList();
		 for(int i=0; i<Analysis.getNumUserEntries(); i++)
		 {
			if(entryUsageArray[i] > 0)
			   entryUsageList[p].insert(entryUsageArray[i], i);
		 }      
		 
	  } 
   }   
   private TimelineObject[] getData(int pnum, int index)  // index into userEventArray
   {
		//System.out.println("getData called in TimelineData \n");
	  Vector tl, msglist, packlist;
	  TimelineEvent tle;

	  int numItems;
	  long btime, etime, rtime;
	  int entry, pSrc, numMsgs, numpacks, msglen;
	  int EventID;
          ObjectId tid;
	  tl = new Vector();
	  Vector userEvents = new Vector();
	  mesgVector[pnum] = new Vector();
	  Analysis.createTL(pnum, beginTime, endTime, tl, userEvents);
	  // proc userEvents
	  int numUserEvents = userEvents.size();
	  if (numUserEvents > 0) {
	    userEventsArray[index] = new UserEvent[numUserEvents];
	    for (int i=0; i<numUserEvents; i++) {
	      userEventsArray[index][i] = (UserEvent) userEvents.elementAt(i);
	    }
	  }
	  else { userEventsArray[index] = null; } // probably already numm
	  
	  // proc timeline events
	  numItems = tl.size();   
	  TimelineObject[] tlo = new TimelineObject[numItems];
	  for(int i=0; i<numItems; i++)
	  {
		 tle   = (TimelineEvent)tl.elementAt(i);
		 btime = tle.BeginTime;
		 etime = tle.EndTime; 
		 entry = tle.EntryPoint; 
		 pSrc  = tle.SrcPe;
		 msglen  = tle.MsgLen;
 		 tid   = tle.id;
		 rtime = tle.RecvTime;
		 EventID = tle.EventID;
			
		 msglist = tle.MsgsSent;
		 if(msglist == null)
			numMsgs = 0;
		 else
			numMsgs = msglist.size();
			
		 TimelineMessage[] msgs = new TimelineMessage[numMsgs];
		 for(int m=0; m<numMsgs; m++){
			msgs[m] = (TimelineMessage)msglist.elementAt(m);
		 	mesgVector[pnum].addElement((TimelineMessage)msglist.elementAt(m));
		 }	

		 packlist = tle.PackTimes;
		 if(packlist == null)
			numpacks = 0;
		 else
			numpacks = packlist.size();
		 
		 PackTime[] packs = new PackTime[numpacks];
		 for(int p=0; p<numpacks; p++)
			packs[p] = (PackTime)packlist.elementAt(p);
		 
		 //tlo[i] = new TimelineObject(this, btime, etime, entry, msgs, packs, pnum, pSrc, msglen, rtime, tid);
		 tlo[i] = new TimelineObject(this, btime, etime, entry, msgs, packs, pnum, pSrc, msglen, rtime, tid,EventID);
		 
	  }

	  /*
          MY FIRST ATTEMPT TO ADD USER EVENTS TO TIMLINE PROVED FRUITLESS 
          WELL NOT QUITE, BUT THE COMMENTED CODE HERE ENDED UP BEING
	  USELESS.
          THE PROBLEM WAS THAT THE USER EVENTS THAT OCCURED DURING EPS
           SHOWED UP, BUT THOSE THAT OCCURED IN BETWEEN EPS DID NOT
	  THIS HAS TO DO WITH THE "setBounds" AND THE FACT THAT THIS
          IS A COMPONENT. 
      
          CHECKING THE CODE IN SO IN CASE NEEDED IN FUTURE.
      
          IF IT BOTHERS YOU TAKE IT OUT

         -- JOSHUA
  
	  // add userEvents to the TimelineObject.  userEvents are not guaranteed to be 
	  // in any order, so this is somewhat more compilicated than a linear algo
	  if (userEventsArray != null && userEventsArray[index] != null) {
	    int tloIndex = 0;  // timeline object index
	    int ueaIndex = 0;  // user event index
	    Vector userEventsForObject = new Vector();
	    long beginTime;
	    TimelineObject obj1 = null, obj2 = null;
	    // i know this is a long if/else statement.  sorry, i'm just klugeing --JMU
	    while (ueaIndex < userEventsArray[index].length && 
		   tloIndex < tlo.length) 
	    {
	      beginTime = userEventsArray[index][ueaIndex].BeginTime;
	      obj1 = tlo[tloIndex];
	      // ***************************************************************
	      // this userEvent falls AFTER current object
	      if (beginTime > obj1.getBeginTime()) {
		if (beginTime < obj1.getEndTime()) {
		  // if userEvent beginTime within obj's time, add it to current object
		  userEventsForObject.addElement(userEventsArray[index][ueaIndex]);
		  ueaIndex++;
		}
		else if (tloIndex < tlo.length-1) {
		  obj2 = tlo[tloIndex+1];
		  if (beginTime < obj2.getBeginTime()) {
		    // next object occurs AFTER user event, so just add to current object
		    userEventsForObject.addElement(userEventsArray[index][ueaIndex]);
		    ueaIndex++;
		  }
		  else {
		    // current user event falls AFTER next object, so if any user events
		    // for current object, add them, otherwise advance to next object
		    if (userEventsForObject.size() > 0) {
		      obj1.addUserEvents(userEventsForObject);
		      userEventsForObject.removeAllElements();
		    }
		    tloIndex++;
		  }
		}
		else if (tloIndex == tlo.length-1) {
		  // this is the LAST object, add this event here anyway
		  userEventsForObject.addElement(userEventsArray[index][ueaIndex]);
		  ueaIndex++;
		}
	      }
	      // ***************************************************************
	      // this userEvent falls BEFORE current objects
	      else {
		if (tloIndex == 0) {
		  // this is the first object, so add this event here anyway
		  userEventsForObject.addElement(userEventsArray[index][ueaIndex]);
		  ueaIndex++;
		}
		else {
		  // add any user event for current object, and seek back to try to 
		  // put in other objects
		  tloIndex--;
		  if (userEventsForObject.size() > 0) {
		    obj1.addUserEvents(userEventsForObject);
		    userEventsForObject.removeAllElements();
		  }
		}
	      }
	    }
	    // fall out of the loop, so add objects if any
	    if (userEventsForObject.size() > 0) {
	      obj1.addUserEvents(userEventsForObject);
	      userEventsForObject.removeAllElements();
	    }
	  }
	  */
	  
	  return tlo;
   }   

   public int getNumUserEvents() {
     if (userEventsArray == null) { return 0; }
     int num = 0;
     for (int i=0; i<userEventsArray.length; i++) {
       if (userEventsArray[i] != null) { num += userEventsArray[i].length; }
     }
     return num;
   }


   public void drawConnectingLine(int pCreation,long creationtime,int pCurrent,long executiontime,int h,int startY,int drawordelete){
	double yscale;
	
	int startpe_position,endpe_position;
	
	
	Dimension dim = displayCanvas.getSize();
	double calc_xscale = (double )(pixelIncrement/timeIncrement);
	long time = endTime-beginTime+1;
	int mywidth=dim.width;
	int maxx = offset + (int)((endTime-beginTime)*pixelIncrement/timeIncrement);
	
	processorList.reset();
	startpe_position = 0;
	endpe_position = 0;
	int count =0;
	TimelineLine line;
	
	if(drawordelete == 2){
		int flag  = 0;
		int i;
		for (i=0;i<mesgCreateExecVector.size();i++){
			line = (TimelineLine ) mesgCreateExecVector.elementAt(i);
			if(line.pCurrent == pCurrent && line.executiontime == executiontime){
				flag = 1;
				break;
			}
		}
		if(flag == 1){
			mesgCreateExecVector.remove(i);
		}
		displayCanvas.repaint();
		
		return;
	}
	
	for(int i =0;i < processorList.size();i++){
		int pe = processorList.nextElement();
		if(pe == pCreation)
			startpe_position = count;
		if(pe == pCurrent)
			endpe_position = count;
		count++;	
	}
	processorList.reset();
	yscale = (double )dim.height/(double )(processorList.size());
	
	int x1 = (int )((double )(creationtime - beginTime)*calc_xscale+offset);
	int x2 = (int )((double )(executiontime - beginTime)*calc_xscale+offset);
	int y1 = (int )(yscale * (double )startpe_position + h+startY+5+5);
	int y2 = (int )(yscale * (double )endpe_position + h);
	

	//g.setColor(new Color(100,100,255));
	//g.drawLine(x1,y1,x2,y2);
	line = new TimelineLine(x1,y1,x2,y2,pCurrent,executiontime);
	mesgCreateExecVector.add(line);
	displayCanvas.repaint();
	//g.drawLine(offset,0,offset,tlh);
	//g.drawLine(maxx,0,maxx,tlh);
   }

   public void drawAllLines(){
   	Graphics g = displayCanvas.getGraphics();
   	if(!mesgCreateExecVector.isEmpty()){
		 	g.setColor(new Color(100,100,255));
			for(int i=0;i<mesgCreateExecVector.size();i++){
				TimelineLine lineElement = (TimelineLine )mesgCreateExecVector.elementAt(i);
				g.drawLine(lineElement.x1,lineElement.y1,lineElement.x2,lineElement.y2);
			}
	 }

   }

	 public void clearAllLines(){
	 	if(tloArray != null){
			for(int i=0;i<tloArray.length;i++){
				if(tloArray[i] != null)
					for(int j=0;j<tloArray[i].length;j++){
						if(tloArray[i][j]!= null){
							tloArray[i][j].clearCreationLine();
						}
					}
			}
		}
		mesgCreateExecVector.clear();
	 }
}


