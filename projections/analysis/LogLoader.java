package projections.analysis;

/** This class reads in .log files and turns them into a timeline.
 *  @author Sid Cammeresi
 *  @version 1.0
 */

import java.io.*;
import java.lang.*;
import java.text.DecimalFormat;
import java.util.*;

import javax.swing.*;

import org.apache.xpath.operations.And;

import projections.gui.*;
import projections.misc.*;

public class LogLoader extends ProjDefs
{
    private long BeginTime, EndTime;
    private String validPEString;
    
    private int basePE, upperPE;
    private boolean validPERange;
    private StringBuffer validPEStringBuffer;

    // **CW** register previous event timestamp to support delta encoding.
    private long prevTime = 0;
    private boolean deltaEncoded = false;
    private int tokenExpected = 2;

    private boolean isProcessing = false;
    boolean ampiTraceOn = false;
    
    public LogLoader() 
	throws LogLoadException
    {
	int              Type;
	long              Time;
	int              Len;
	long 	       back;
	String           Line;
	File testFile;
	RandomAccessFile InFile;
	StringTokenizer  st;

	//Find the begin and end time across the parallel machine
	BeginTime = 0;
	EndTime   = Integer.MIN_VALUE;
	int nPe=Analysis.getNumProcessors();

	ProgressMonitor progressBar =
	    new ProgressMonitor(Analysis.guiRoot, "Determining end time",
				"", 0, nPe);

	validPEStringBuffer = new StringBuffer();
	validPERange = false;
	basePE = -1;
	upperPE = -1;
	for (int i=0; i<nPe; i++) {
	    if (!progressBar.isCanceled()) {
		progressBar.setNote(i + " of " + nPe);
		progressBar.setProgress(i);
	    } else {
		System.err.println("Fatal error - Projections cannot" +
				   " function without proper end time!");
		System.exit(-1);
	    }
	    try {
		// test the file to see if it exists ...
		testFile = new File(Analysis.getLogName(i));
		if (testFile.exists() == false) {
		    System.out.println(Analysis.getLogName(i) +
				       " does not exist, ignoring.");
		    updatePEStringBuffer();
		    validPERange = false;
		} else {
		    InFile = new RandomAccessFile (testFile, "r");

		    // success, so register processor as valid.
		    registerPE(i);
		    back = InFile.length()-80*3; //Seek to the end of the file
		    if (back < 0) back = 0;
		    InFile.seek(back);
		    while(InFile.readByte() != '\n');
		    //Throws EOFException at end of file
		    while (true) {
			Line = InFile.readLine();
			st   = new StringTokenizer(Line);
			if (Integer.parseInt(st.nextToken()) == 
			    END_COMPUTATION) {
			    Time = Long.parseLong(st.nextToken());
			    if (Time > EndTime)
				EndTime = Time;
			    break;
			}   
		    }
		    InFile.close ();
		}
	    } catch (IOException E) {
		System.out.println("Couldn't read log file " + 
				   Analysis.getLogName(i));
	    }
	}
	updatePEStringBuffer();
	validPEString = validPEStringBuffer.toString();

	progressBar.close();
	Analysis.setTotalTime(EndTime-BeginTime);
    }    

    /**
     * Author: Chao Mei
     * The procThdVec contains different processing threads vector, in which every processing threads
     * have different id (specified by LogEntry's field "id") 
     */
    public void createAMPIUsageProfile(int procId, long beginTime, long endTime, Vector procThdVec)
        throws LogLoadException
    {

        GenericLogReader logFileRd = null;
        LogEntryData rawLogData = null;
        LogEntry curEntry = null;
        LogEntry prevEntry = null;

        /**
         * Variables related to ampi support.
         * Initially, I tend to maintain a local global variable of current function. But it turns out
         * to be unnecessary and possibly wrong because:
         * 1. The current function is always the top element of curProcessing's auxCallFuncStack.
         * 2. There are cases when a single processing only contains part of a function. This function's end
         *    is in the later run of this processing. In log file, it looks like:
         *    BEGIN_PROCESSING...BEGIN_FUNC..END_PROCESSING......BEGIN_PROCESSING...END_FUNC...END_PROCESSING
         *    In these cases, maintain the current function variable is somewhat difficult. But it will be easy
         *    to use current processing's auxCallFuncStack to get the current running function.
         */
        AmpiProcessProfile curProcessing = null;
        

        /**
         * key = process' triple id
         * value = an instance of AmpiProcessProfile
         */
        Hashtable procThdMap = new Hashtable();

        try{
            logFileRd = new GenericLogReader(procId,Analysis.getVersion());
	    rawLogData = new LogEntryData();

            /** 
            * seek the first BEGIN_PROCESSING within this time interval and its timestamp >= beginTime         .
            * Therefore, any functions that before the BEGIN_PROCESSING is ignored.
            * This could be somewhat an error. Consider a dummy function LATER!
            */
            while(true){
                logFileRd.nextEvent(rawLogData);
                curEntry = LogEntry.adapt(rawLogData);
                if(curEntry.TransactionType==BEGIN_PROCESSING 
                   && curEntry.Entry!=-1
                   && curEntry.Time >= beginTime){
                    curProcessing = new AmpiProcessProfile(curEntry.Time, curEntry.id);                    
                    break;
                }
            }

            //Foound the starting point, initialize the prevEntry
            prevEntry = curEntry;

           /** 
            * Processing log file focusing on BEGIN/END_FUNC, BEGIN/END_PROCESSING 
            * Assumming following conditions:
            * 1. BEGIN/END_PROCESSING cannot be overlapped
            * 2. BEGIN/END_PACK/UNPACK cannot be overlapped
            * 3. BEGIN/END_FUNCTION can be overlapped
            * 4. In one processing interval, BEGIN/END_FUNC maynot be paired. But in terms of the whole processing
            * (a processing of same id may be divided into several pieces), BEGIN/END_FUNC must be paired
            * 5. Currently, between the beginTime and endTime, BEGIN/END_FUNC are assumed to be paired.
            */

            boolean reachEndTime = false;
            while(!reachEndTime){
                logFileRd.nextEvent(rawLogData);
                curEntry = LogEntry.adapt(rawLogData);
                if(curEntry.Entry == -1) //something must be wrong with the log file
                    continue;
                switch(curEntry.TransactionType){
                case BEGIN_PROCESSING:{                
                    if(curProcessing!=null){
                        System.err.println("Error in parsing log file as processing overlapped!");
                        return;
                    } else {
                        //just start a new processing but need to check whether it is the same
                        //processing that has been stored in the "procThdMap"
                        AmpiProcessProfile tmp = new AmpiProcessProfile(curEntry.Time, curEntry.id);
                        AmpiProcessProfile storedProfile = (AmpiProcessProfile) procThdMap.get(tmp.toHashKey());
                        curProcessing = (storedProfile==null ? tmp:storedProfile);
                    }
                    break;
                }
                case END_PROCESSING:{                
                    /**
                     * Processing the end of a processing. If there're functions within this processing
                     * push the process into the procThdMap. Otherwise do nothing.
                     * Upto this point, curProcessing mustn't be null!
                     * Processing cannot overlap! i.e it will not appear the sequence like:
                     * BEGIN_PROCESSING ... BEGIN_PROCESSING...END_PROCESSING...END_PROCESSING
                     * Compute the accumlated execution time for this process (in terms of its ObjectId)
                     */
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as processing is not paired!");
                        return;
                    }
                    /*if(!curEntry.id.compare(curProcessing.getProcessID())){
                        System.err.println("Error in parsing log file as processing overlapped!");
                        return;
                    }*/
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    if(!curProcessing.getAuxCallFuncStack().empty()){
                        AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                        curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    }

                    //Only store the processing that hasn't been stored!
                    if(procThdMap.get(curProcessing.toHashKey())==null)
                        procThdMap.put(curProcessing.toHashKey(), curProcessing);                                            
                    curProcessing = null;

                    //The parsing will end only when it reaches completely paired BEGIN/END_PROCESSING
                    if(curEntry.Time >= endTime)
                        reachEndTime = true;

                    break;
                }
                case BEGIN_FUNC:{                
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as a function is not in a processing!");
                        return;
                    }

                    //first compute the accumlated time for the current processing!
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    //second deal with the new function
                    AmpiFunctionData curFunc = curEntry.ampiData;
                    Stack auxStk = curProcessing.getAuxCallFuncStack();
                    auxStk.push(curFunc);
                    break;
                }
                case END_FUNC:{                
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as a function is not in a processing!");
                        return;
                    }
                    if(curProcessing.getAuxCallFuncStack().empty()){                    
                        System.err.println("Error in parsing log file as a function is not paired properly!");
                        return;
                    }
                    AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                    if(curFunc.FunctionID != curEntry.FunctionID){
                        System.err.println("Error in parsing log file as a function is not paired properly!");
                        return;
                    }

                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    //as the current function is completed, it is popped from the auxCallFuncStack and pushed to 
                    //the final callFuncStack associated with curProcessing
                    curProcessing.getAuxCallFuncStack().pop();
                    curProcessing.getFinalCallFuncStack().push(curFunc);
                    break;
                }
                case BEGIN_PACK:
                case BEGIN_UNPACK:
                case CREATION:{
                    if(curProcessing==null)
                        break;
                    
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    if(curProcessing.getAuxCallFuncStack().empty())
                        break;
                    AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                    curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);                    
                    break;
                }

                /**
                 * The cases: END_PACK, END_UNPACK 
                 * are neglected as their time is not contributed to the total execution of 
                 * the current processing and the current function
                 */               
                default:
                    break;
                }
                prevEntry = curEntry;
            }

        } catch (EOFException e) { 
	    /*ignore*/ 
	} catch (FileNotFoundException E) {
	    System.out.println("ERROR: couldn't open file " + 
			       Analysis.getLogName(procId));
	} catch (IOException E) {
	    throw new LogLoadException(Analysis.getLogName(procId), 
				       LogLoadException.READ);
	}

        //finally select the processes that have functions and push them to the procThdVec
        for(Enumeration e=procThdMap.keys(); e.hasMoreElements();){
            AmpiProcessProfile p = (AmpiProcessProfile) procThdMap.get(e.nextElement());
            if(p.getFinalCallFuncStack().size()>0)
                procThdVec.add(p);
        }
    }

    /**
     * Author: Chao Mei
     * The procThdVec contains different processing threads vector, in which every processing threads
     * have different id (specified by LogEntry's field "id")
     * This function's logical and data flow is same with createAMPIUsageProfile.
     * To some extent,createAMPIUsageProfile function can be implemented by calling createAMPIFuncTimeProfile.
     * However, as I was first asked to implement ampi function's usage profile, I designed the implementation
     * without considering the ampi function's time profile. Therefore, both of these functions share very similar
     * codes. If I have time later, I will rewrite the createAMPIUsageProfile function by using this function as 
     * the stub!!!
     */
    public void createAMPIFuncTimeProfile(int procId, long beginTime, long endTime, Vector procThdVec)
        throws LogLoadException        
    {

        GenericLogReader logFileRd = null;
        LogEntryData rawLogData = null;
        LogEntry curEntry = null;
        LogEntry prevEntry = null;

        /**
         * Variables related to ampi support.
         * Initially, I tend to maintain a local global variable of current function. But it turns out
         * to be unnecessary and possibly wrong because:
         * 1. The current function is always the top element of curProcessing's auxCallFuncStack.
         * 2. There are cases when a single processing only contains part of a function. This function's end
         *    is in the later run of this processing. In log file, it looks like:
         *    BEGIN_PROCESSING...BEGIN_FUNC..END_PROCESSING......BEGIN_PROCESSING...END_FUNC...END_PROCESSING
         *    In these cases, maintain the current function variable is somewhat difficult. But it will be easy
         *    to use current processing's auxCallFuncStack to get the current running function.
         */
        AmpiProcessProfile curProcessing = null;
        

        /**
         * key = process' triple id
         * value = an instance of AmpiProcessProfile
         */
        Hashtable procThdMap = new Hashtable();

        try{
            logFileRd = new GenericLogReader(procId,Analysis.getVersion());
	    rawLogData = new LogEntryData();

            /** 
            * seek the first BEGIN_PROCESSING within this time interval and its timestamp >= beginTime         .
            * Therefore, any functions that before the BEGIN_PROCESSING is ignored.
            * This could be somewhat an error. Consider a dummy function LATER!
            */
            while(true){
                logFileRd.nextEvent(rawLogData);
                curEntry = LogEntry.adapt(rawLogData);
                if(curEntry.TransactionType==BEGIN_PROCESSING 
                   && curEntry.Entry!=-1
                   && curEntry.Time >= beginTime){
                    curProcessing = new AmpiProcessProfile(curEntry.Time, curEntry.id);                    
                    break;
                }
            }

            //Foound the starting point, initialize the prevEntry
            prevEntry = curEntry;

           /** 
            * Processing log file focusing on BEGIN/END_FUNC, BEGIN/END_PROCESSING 
            * Assumming following conditions:
            * 1. BEGIN/END_PROCESSING cannot be overlapped
            * 2. BEGIN/END_PACK/UNPACK cannot be overlapped
            * 3. BEGIN/END_FUNCTION can be overlapped
            * 4. In one processing interval, BEGIN/END_FUNC maynot be paired. But in terms of the whole processing
            * (a processing of same id may be divided into several pieces), BEGIN/END_FUNC must be paired
            * 5. Currently, between the beginTime and endTime, BEGIN/END_FUNC are assumed to be paired.
            */

            boolean reachEndTime = false;
            while(!reachEndTime){                
                logFileRd.nextEvent(rawLogData);
                curEntry = LogEntry.adapt(rawLogData);
                if(curEntry.Entry == -1) //something must be wrong with the log file
                    continue;
                switch(curEntry.TransactionType){
                case BEGIN_PROCESSING:{                
                    if(curProcessing!=null){
                        System.err.println("Error in parsing log file as processing overlapped!");
                        return;
                    } else {
                        //just start a new processing but need to check whether it is the same
                        //processing that has been stored in the "procThdMap"
                        AmpiProcessProfile tmp = new AmpiProcessProfile(curEntry.Time, curEntry.id);
                        AmpiProcessProfile storedProfile = (AmpiProcessProfile) procThdMap.get(tmp.toHashKey());
                        curProcessing = (storedProfile==null ? tmp:storedProfile);
                    }
                    break;
                }
                case END_PROCESSING:{                
                    /**
                     * Processing the end of a processing. If there're functions within this processing
                     * push the process into the procThdMap. Otherwise do nothing.
                     * Upto this point, curProcessing mustn't be null!
                     * Processing cannot overlap! i.e it will not appear the sequence like:
                     * BEGIN_PROCESSING ... BEGIN_PROCESSING...END_PROCESSING...END_PROCESSING
                     * Compute the accumlated execution time for this process (in terms of its ObjectId)
                     */
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as processing is not paired!");
                        return;
                    }
                    
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    if(!curProcessing.getAuxCallFuncStack().empty()){
                        AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                        AmpiFunctionData.AmpiFuncExecInterval gap = 
                            new AmpiFunctionData.AmpiFuncExecInterval(prevEntry.Time,curEntry.Time);
                        curFunc.insertExecInterval(gap);
                        curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    }

                    //Only store the processing that hasn't been stored!
                    if(procThdMap.get(curProcessing.toHashKey())==null)
                        procThdMap.put(curProcessing.toHashKey(), curProcessing);                                            
                    curProcessing = null;

                    //The parsing will end only when it reaches completely paired BEGIN/END_PROCESSING
                    if(curEntry.Time >= endTime)
                        reachEndTime = true;

                    break;
                }
                case BEGIN_FUNC:{                
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as a function is not in a processing!");
                        return;
                    }

                    //first compute the accumlated time for the current processing!
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    //second deal with the new function
                    AmpiFunctionData curFunc = curEntry.ampiData;
                    Stack auxStk = curProcessing.getAuxCallFuncStack();
                    auxStk.push(curFunc);
                    break;
                }
                case END_FUNC:{                
                    if(curProcessing==null){
                        System.err.println("Error in parsing log file as a function is not in a processing!");
                        return;
                    }
                    if(curProcessing.getAuxCallFuncStack().empty()){                    
                        System.err.println("Error in parsing log file as a function is not paired properly!");
                        return;
                    }
                    AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                    if(curFunc.FunctionID != curEntry.FunctionID){
                        System.err.println("Error in parsing log file as a function is not paired properly!");
                        return;
                    }

                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    AmpiFunctionData.AmpiFuncExecInterval gap = 
                        new AmpiFunctionData.AmpiFuncExecInterval(prevEntry.Time,curEntry.Time);
                    curFunc.insertExecInterval(gap);
                    curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    //as the current function is completed, it is popped from the auxCallFuncStack and pushed to 
                    //the final callFuncStack associated with curProcessing
                    curProcessing.getAuxCallFuncStack().pop();
                    curProcessing.getFinalCallFuncStack().push(curFunc);
                    break;
                }
                case BEGIN_PACK:
                case BEGIN_UNPACK:
                case CREATION:{
                    if(curProcessing==null)
                        break;
                    
                    curProcessing.incrAccExecTime(curEntry.Time - prevEntry.Time);
                    if(curProcessing.getAuxCallFuncStack().empty())
                        break;
                    AmpiFunctionData curFunc = (AmpiFunctionData) curProcessing.getAuxCallFuncStack().peek();
                    AmpiFunctionData.AmpiFuncExecInterval gap = 
                        new AmpiFunctionData.AmpiFuncExecInterval(prevEntry.Time,curEntry.Time);
                    curFunc.insertExecInterval(gap);
                    curFunc.incrAccExecTime(curEntry.Time - prevEntry.Time);                    
                    break;
                }

                /**
                 * The cases: END_PACK, END_UNPACK 
                 * are neglected as their time is not contributed to the total execution of 
                 * the current processing and the current function
                 */               
                default:
                    break;
                }
                prevEntry = curEntry;
            }

        } catch (EOFException e) { 
	    /*ignore*/ 
	} catch (FileNotFoundException E) {
	    System.out.println("ERROR: couldn't open file " + 
			       Analysis.getLogName(procId));
	} catch (IOException E) {
	    throw new LogLoadException(Analysis.getLogName(procId), 
				       LogLoadException.READ);
	}

        //finally select the processes that have functions and push them to the procThdVec
        for(Enumeration e=procThdMap.keys(); e.hasMoreElements();){
            AmpiProcessProfile p = (AmpiProcessProfile) procThdMap.get(e.nextElement());
            if(p.getFinalCallFuncStack().size()>0)
                procThdVec.add(p);
        }
    }

    public Vector createtimeline(int PeNum, long Begin, long End, 
				 Vector Timeline, Vector userEventVector)
	throws LogLoadException
    {
	int               Entry       = 0;
	long              Time        = Long.MIN_VALUE;
	boolean		LogMsgs     = true;
	LogEntry          LE          = null;
	TimelineEvent     TE          = null;
	Hashtable         userEvents  = new Hashtable();  // store unfinished userEvents
	UserEvent         userEvent   = null;  // jsut for temp purposes
	TimelineMessage   TM          = null;
	PackTime          PT          = null;
	boolean tempte;

	GenericLogReader reader;
	LogEntryData data;

	System.gc ();

	// open the file
	try {
	    reader = new GenericLogReader(PeNum,Analysis.getVersion());
	    data = new LogEntryData();
	    // to treat dummy thread EPs as a special-case EP
	    //  **CW** I consider this a hack. A more elegant way must
	    // be found design-wise.
	    if (Analysis.getNumFunctionEvents() > 0) {
		ampiTraceOn = true;
	    }

	    // Each time we open the file, we need to reset the
	    // previous event timestamp to 0 to support delta encoding.
	    prevTime = 0;

	    isProcessing = false; 
	    while (true) { //Seek to time Begin
		reader.nextEvent(data);
		LE = LogEntry.adapt(data);
		if (LE.Entry == -1) {
		    continue;
		}
		// This is still not ideal. There are cases which may cause
		// a rogue begin event to have data dropped at the beginning.
		if ((LE.TransactionType == BEGIN_PROCESSING) && 
		    (LE.Entry != -1)) {
		    Time       = LE.Time - BeginTime;
		    Entry      = LE.Entry;
		} else if ((LE.TransactionType == END_PROCESSING) &&
			   (LE.Entry != -1)) {
		    Time       = LE.Time - BeginTime;
		    Entry      = LE.Entry;
		} else if (LE.TransactionType == BEGIN_IDLE) {
		    Time = LE.Time - BeginTime;
		}
		if (LE.Time >= Begin) {
		    break;
		}
	    }
	    if (Time == Long.MIN_VALUE) {
		Time = Begin;
	    }
	    if (LE.Time > End) {
		switch (LE.TransactionType) {
		case BEGIN_PROCESSING:
		    System.out.println("finished empty timeline for " + PeNum);
		    return Timeline;                              
		case END_PROCESSING:
		default:
		    Timeline.addElement(TE=new TimelineEvent(Begin-BeginTime,
							     End-BeginTime,
							     LE.Entry,LE.Pe));
		    return Timeline;
		}
	    }
	    //Throws EOFException at end of file; break if past endTime
	    CallStackManager cstack = new CallStackManager();
	    LogEntry enclosingDummy = null;
	    ObjectId tid = null;
	    AmpiFunctionData ampiData = null;
	    while(true) {
		if (LE.Entry != -1) {
		    switch (LE.TransactionType) {
		    case BEGIN_FUNC:
			// Phase 1: Check stack for preceeding functions.
			//          If one is found, we need to "terminate"
			//          the timeline event associated with it.
			// **CW** Right now, there is an unavoidable bug
			// that enclosingDummy could be empty.

			// end previous function's (or Dummy Thread EP's)
			// timeline event.
			if (TE != null) {
			    TE.EndTime = LE.Time - BeginTime;
			}

			// Phase 2: Handle current function. Note that the
			//          Function's messaging properties need
			//          to be suppressed.
			TE = new TimelineEvent();
			TE.isFunction = true;
			TE.BeginTime = LE.Time-BeginTime;
			TE.EntryPoint = LE.FunctionID;
			TE.EventID = -1; // no source.
			TE.id = enclosingDummy.id;
			cstack.push(LE.ampiData, TE.id.id[0], 
				    TE.id.id[1], TE.id.id[2]);
			TE.callStack = 
			    cstack.getStack(TE.id.id[0], TE.id.id[1], 
					    TE.id.id[2]);
			Timeline.addElement(TE);
			break;
		    case END_FUNC:
			// Phase 1: End current function.
			if (TE != null) {
			    TE.EndTime = LE.Time - BeginTime;
			    cstack.pop(TE.id.id[0], TE.id.id[1], TE.id.id[2]);
			}
			TE = null;

			// Phase 2: "create" a new Begin for any previous
			//          functions or the dummy thread ep that
			//          is supposed to enclose it.
			tid = enclosingDummy.id;
			ampiData =
			    (AmpiFunctionData)cstack.read(tid.id[0],
							  tid.id[1],
							  tid.id[2]);
			// Dealing with dummy thread ep
			if ((ampiData == null) ||
			    (ampiData.FunctionID == 0)) {
			    TE = new TimelineEvent(LE.Time-BeginTime,
						   LE.Time-BeginTime,
						   enclosingDummy.Entry, 
						   enclosingDummy.Pe,
						   enclosingDummy.MsgLen, 
						   enclosingDummy.recvTime, 
						   enclosingDummy.id,
						   -1, // EventID no source.
						   enclosingDummy.cpuBegin, 
						   enclosingDummy.cpuEnd,
						   enclosingDummy.numPapiCounts,
						   enclosingDummy.papiCounts);
			    Timeline.addElement(TE);
			} else {
			    // "create" previous function on stack.
			    TE = new TimelineEvent();
			    TE.isFunction = true;
			    TE.BeginTime = LE.Time-BeginTime;
			    TE.EntryPoint = ampiData.FunctionID;
			    TE.EventID = -1; // no source.
			    TE.id = tid;
			    TE.callStack =
				cstack.getStack(TE.id.id[0], TE.id.id[1],
						TE.id.id[2]);
			    Timeline.addElement(TE);
			}
			break;
		    case BEGIN_PROCESSING:
			if (isProcessing) {
			    // We add a "pretend" end event to accomodate
			    // the prior begin processing event.
			    if (TE != null) {
				TE.EndTime = LE.Time - BeginTime;
			    }
			    TE = null;
			}
			isProcessing = true;

			// Handle Dummy Thread EPs to see if we need to
			// resume a function entry.
			if (ampiTraceOn && (LE.Entry == 0)) {
			    enclosingDummy = LE;
			    tid = enclosingDummy.id;
			    ampiData =
				(AmpiFunctionData)cstack.read(tid.id[0],
							      tid.id[1],
							      tid.id[2]);
			    // only handle if there's a function. Otherwise
			    // treat as normal Dummy Thread EP.
			    if ((ampiData != null) &&
				(ampiData.FunctionID != 0)) {
				// "create" last function on stack. Note
				// that the enclosing dummy thread ep's
				// messaging properties need to be transfered
				// to the function's timeline event.
				TE = new TimelineEvent();
				TE.isFunction = true;
				TE.BeginTime = LE.Time-BeginTime;
				TE.EntryPoint = ampiData.FunctionID;
				TE.SrcPe = enclosingDummy.Pe;
				TE.EventID = enclosingDummy.EventID;
				TE.MsgLen = enclosingDummy.MsgLen;
				TE.RecvTime = enclosingDummy.recvTime;
				TE.id = tid;
				TE.callStack =
				    cstack.getStack(TE.id.id[0], TE.id.id[1],
						    TE.id.id[2]);
				Timeline.addElement(TE);
				break;
			    }
			}

			// Normal case of handling EPs
			TE = new TimelineEvent(LE.Time-BeginTime, 
					       LE.Time-BeginTime,
					       LE.Entry, LE.Pe,
					       LE.MsgLen, LE.recvTime, 
					       LE.id,LE.EventID,
					       LE.cpuBegin, LE.cpuEnd,
					       LE.numPapiCounts,
					       LE.papiCounts);
			Timeline.addElement(TE);
			break;
		    case END_PROCESSING:

			// Handle Dummy Thread EPs to see if we need
			// to close off a currently operating function.
			if (ampiTraceOn && (LE.Entry == 0)) {
			    tid = enclosingDummy.id;
			    ampiData =
				(AmpiFunctionData)cstack.read(tid.id[0],
							      tid.id[1],
							      tid.id[2]);
			    // only handle if there's a function. Otherwise
			    // treat as normal Dummy Thread EP.
			    if ((ampiData != null) &&
				(ampiData.FunctionID != 0)) {
				// end previous function's timeline event.
				if (TE != null) {
				    TE.EndTime = LE.Time - BeginTime;
				}
				TE = null;
				enclosingDummy = null;
				break;
			    }
			}

			// Normal case of handling EPs
			if (TE != null) {
			    TE.EndTime = LE.Time - BeginTime;
			    TE.cpuEnd = LE.cpuEnd;
			    for (int i=0; i<LE.numPapiCounts; i++) {
				TE.papiCounts[i] = LE.papiCounts[i] -
				    TE.papiCounts[i];
			    }
			}
			TE = null;
			isProcessing = false;
			break;
		    case CREATION:
			tempte = false;
			//Start a new dummy event
			if (TE == null) { 
			    TE = new TimelineEvent(LE.Time-BeginTime,
						   LE.Time-BeginTime,
						   -2,LE.Pe,LE.MsgLen);
			    Timeline.addElement(TE);
			    tempte = true;
			}
			TM = new TimelineMessage(LE.Time - BeginTime,
						 LE.Entry, LE.MsgLen,
						 LE.EventID);
			TE.addMessage(TM);
			if (tempte) {
			    TE = null;
			}
			break;
		    case CREATION_MULTICAST:
			tempte = false;
			if (TE == null) {
			    TE = new TimelineEvent(LE.Time-BeginTime,
						   LE.Time-BeginTime,
						   -2, LE.Pe, LE.MsgLen);
			    Timeline.addElement(TE);
			    tempte = true;
			}
			TM = new TimelineMessage(LE.Time - BeginTime,
						 LE.Entry, LE.MsgLen,
						 LE.EventID, LE.destPEs);
			TE.addMessage(TM);
			if (tempte) {
			    TE = null;
			}
			break;
		    case USER_EVENT:
			// don't mess with TE, that's just for EPs
			UserEvent event = new UserEvent(LE.Time-BeginTime,
							LE.Entry, LE.EventID,
							UserEvent.SINGLE);
			userEventVector.addElement(event);
			break;
		    case USER_EVENT_PAIR:
			// **CW** UserEventPairs come in a two-line block
			// because of the way the tracing code is currently
			// written.
			userEvent = new UserEvent(LE.Time-BeginTime,
						  LE.Entry, LE.EventID,
						  UserEvent.PAIR); 
			// assume the end time to be the end of range
			// in case the ending userevent gets cut off.
			userEvent.EndTime = End;

			// Now, expect to read the second entry and handle
			// errors if necessary.
			reader.nextEvent(data);
			LE = LogEntry.adapt(data);

			if (LE.TransactionType != USER_EVENT_PAIR) {
			    // DANGLING - throw away the old event
			    // just pass the read data to the next
			    // loop iteration.
			    userEvent = null;
			    continue;
			}
			// MISMATCHED EVENT PAIRS - again, nullify
			// the first read event and pass the newly
			// read entry back through the loop
			if (userEvent.CharmEventID != LE.EventID || 
			    userEvent.UserEventID != LE.Entry) {
			    userEvent = null;
			    continue;
			} else {
                            userEvent.EndTime = LE.Time-BeginTime;
                            userEventVector.addElement(userEvent);
			}
			break;
		    case BEGIN_PACK:
			// Start a new dummy event
			if (TE == null) {
			    TE = new TimelineEvent(LE.Time-BeginTime,
						   LE.Time-BeginTime,-2,
						   LE.Pe);
			    Timeline.addElement(TE);
			}
			TE.addPack (PT=new PackTime(LE.Time-BeginTime));
			break;
		    case END_PACK:
			if (PT!=null) {
			    PT.EndTime = LE.Time-BeginTime;
			}
			PT=null;
			if (TE != null) {
			    if (TE.EntryPoint == -2) {
				TE=null;
			    }
			}
			break;
		    case BEGIN_IDLE:
			if (MainWindow.IGNORE_IDLE) {
			    break;
			}
			TE = new TimelineEvent(LE.Time - BeginTime,
					       Long.MAX_VALUE,
					       -1,-1); 
			Timeline.addElement(TE);
			break;
		    case END_IDLE:
			if (MainWindow.IGNORE_IDLE) {
			    break;
			}
			if (TE != null) {   
			    TE.EndTime = LE.Time - BeginTime;
			}
			TE=null;
			break;
		    }
		}
		reader.nextEvent(data);
		LE = LogEntry.adapt(data);
		// this will still eventually end because of the 
		// END COMPUTATION event.
		if (LE.Entry != -1) {
		    if ((LE.Time - BeginTime) > End) {
			break;
		    }
		}
	    }

	    // check to see if we are stopping in the middle of a message.
	    // if so, we need to keep reading to get its end time
	    while (TE != null) {
		if (LE.Entry != -1) {
		    if (LE.TransactionType == END_PROCESSING) {
			TE.EndTime = LE.Time - BeginTime;
			TE=null;
		    }
		}
		reader.nextEvent(data);
		LE = LogEntry.adapt(data);
	    }
	} catch (EOFException e) { 
	    /*ignore*/ 
	} catch (FileNotFoundException E) {
	    System.out.println("ERROR: couldn't open file " + 
			       Analysis.getLogName(PeNum));
	} catch (IOException E) {
	    throw new LogLoadException(Analysis.getLogName(PeNum), 
				       LogLoadException.READ);
	}
	return Timeline;
    }

    private ViewerEvent entrytotext(LogEntry LE)
    {
	ViewerEvent VE = new ViewerEvent();
	VE.Time        = LE.Time - BeginTime;
	VE.EventType   = LE.TransactionType;

	if (LE.Entry == -1) {
	    return null;
	}

	switch (LE.TransactionType) {
	case BEGIN_IDLE:
	case END_IDLE:
	case BEGIN_PACK:
	case END_PACK:
	case BEGIN_UNPACK:
	case END_UNPACK:
	    return VE;
	case CREATION:
	case BEGIN_PROCESSING:
	case END_PROCESSING:
	case ENQUEUE:
	    String e2desc[][] = Analysis.getEntryNames();
	    VE.Dest = new String(e2desc[LE.Entry][1] + 
				 "::" + e2desc[LE.Entry][0]);     
	    if (LE.TransactionType != CREATION) {
		VE.SrcPe = LE.Pe;
	    }
	    return VE;
	case USER_EVENT:
	case USER_EVENT_PAIR:
	case DEQUEUE:
	case BEGIN_TRACE:
	case END_TRACE:
	case MESSAGE_RECV:
	case BEGIN_INTERRUPT:
	case END_INTERRUPT:
	default:
	    return null;
	}
    }   

    public long searchtimeline(int PeNum, int Entry, int Num)
	throws LogLoadException, EntryNotFoundException
    {
	long           Count = 0;
	LogEntry       LE     = null;

	GenericLogReader reader;
	LogEntryData data;
	
	// open the file
	try {
	    System.gc();
	    reader = new GenericLogReader(PeNum, Analysis.getVersion());
	    data = new LogEntryData();

	    // **CW** each time we open the file, we need to reset the
	    // previous event timestamp to 0 to support delta encoding.
	    prevTime = 0;

	    //Throws EOFException at end of file
	    while(true) {
		reader.nextEvent(data);
		LE = LogEntry.adapt(data);
		if (LE.Entry == -1) {
		    continue;
		}
		if ((LE.Entry == Entry) && 
		    (LE.TransactionType == BEGIN_PROCESSING)) {
		    Count++;
		}
		if (Count > Num) {
		    break;
		}
	    }
	} catch (FileNotFoundException E) {
	    System.out.println("ERROR: couldn't open file " + 
			       Analysis.getLogName(PeNum));
	} catch (EOFException E) {
	    /*ignore*/
	} catch (IOException E) {
	    throw new LogLoadException(Analysis.getLogName(PeNum), 
				       LogLoadException.READ);
	}  
	return LE.Time - BeginTime;
    }   

    public Vector view(int PeNum) 
	throws LogLoadException
    {
	ViewerEvent    VE;
	Vector ret = null;
	String         Line;

	GenericLogReader reader;
	LogEntryData data;

	try {	  
	    ret = new Vector ();
	    reader = new GenericLogReader(PeNum, Analysis.getVersion());
	    data = new LogEntryData();

	    // **CW** each time we open the file, we need to reset the
	    // previous event timestamp to 0 to support delta encoding.
	    prevTime = 0;

	    //Throws EOFException at end of file
	    while (true) {
		reader.nextEvent(data);
		VE = entrytotext(LogEntry.adapt(data));
		if (VE != null) {
		    ret.addElement (VE);
		}
	    }
	} catch (FileNotFoundException E) {
	    System.out.println("ERROR: couldn't open file " + 
			       Analysis.getLogName(PeNum));
	} catch (EOFException E) {
	} catch (IOException E) {
	    System.out.println("throwing....2");
	    throw new LogLoadException(Analysis.getLogName(PeNum), 
				       LogLoadException.READ);
	}
	return ret;
    }   

    public String getValidProcessorString() {
	return validPEString;
    }

    private void registerPE(int peIdx) {
	if (validPERange == false) {
	    basePE = peIdx;
	}
	upperPE = peIdx;
	validPERange = true;
    }

    private void updatePEStringBuffer() {
	if (!validPERange) {
	    return;
	}
	if (validPEStringBuffer.length() > 0) {
	    validPEStringBuffer.append(",");
	}
	if (upperPE > basePE) {
	    validPEStringBuffer.append(String.valueOf(basePE));
	    validPEStringBuffer.append("-");
	    validPEStringBuffer.append(String.valueOf(upperPE));
	} else if (upperPE == basePE) {
	    validPEStringBuffer.append(String.valueOf(basePE));
	} else {
	    // error. Should never happen.
	}
    }
}
