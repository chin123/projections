package projections.analysis;

import projections.misc.*;

import java.lang.*;
import java.io.*;
import java.util.*;

/** 
 *  Adapted by Chee Wai Lee
 *  (originally SumAnalyzer.java in the projections.analysis package)
 *  3/27/2002
 *
 *  The GenericSummaryReader reads .sum files to produce data to be consumed by
 *  the analyzer.
 */

public class GenericSummaryReader
{
    // private static meta-tags - used to allocate space in the data array
    // based on the number of tags.
    private static final int NUM_TAGS = 2;

    // public static tags - used to access the appropriate part of the
    // array.
    public static final int TOTAL_TIME = 0;
    public static final int NUM_MSGS = 1;

    // header values
    public int versionNum;
    public int myPE;
    public int numPE;
    public int numIntervals;
    public int numEPs;  // bizzare ... already found in .sts file
    public double intervalSize;
    public int numPhases;

    // Data values

    // processor utilization data
    public int processorUtil[];

    // epData dimension 1 - indexed by entry point ID (presumably)
    // epData dimension 2 - indexed by tags (see above).
    public long epData[][];

    // Mark pair information -- ignored (markedPairs added for completeness)
    public int numMarkedPairs;

    // markedPairs dim 1 - indexed by current pair counter
    // markedPairs dim 2 - indexed by 0 or 1 (car or cadr).
    public long markedPairs[][];

    // Phase information
    // phaseData dim 1 - indexed by current phase counter
    // phaseData dim 2 - indexed by entry point ID
    // phaseData dim 3 - indexed by tags (see above).
    public long phaseData[][][];

    // private miscellaneous data
    private double version;
    private BufferedReader reader;
    private ParseTokenizer tokenizer;
    private int tokenType;

    public GenericSummaryReader(String filename, double Nversion) 
	throws IOException
    {
	try {
	    reader = new BufferedReader(new FileReader(filename));
	    version = Nversion;
	    read();
	    reader.close();
	    reader = null;
	} catch (IOException e) {
	    throw new IOException("Error reading file " + filename);
	}
    }

    // Methods to parse the summary file

    public void read()
	throws IOException
    {
	//Set up the tokenizer  **GLOBAL** yucks!
	tokenizer=new ParseTokenizer(reader);
	tokenizer.parseNumbers();
	tokenizer.eolIsSignificant(true);
	tokenizer.whitespaceChars('/','/'); 
	tokenizer.whitespaceChars(':',':');
	tokenizer.whitespaceChars('[','[');
	tokenizer.whitespaceChars(']',']');
	tokenizer.wordChars('a','z');
	tokenizer.wordChars('A','Z');

	//Read the first line (Header information)
	tokenizer.checkNextString("ver");
	versionNum = (int)tokenizer.nextNumber("Version Number");
	myPE = (int)tokenizer.nextNumber("processor number");
	numPE = (int)tokenizer.nextNumber("number of processors");
	tokenizer.checkNextString("count");
	numIntervals = (int)tokenizer.nextNumber("count");
	tokenizer.checkNextString("ep");
	numEPs = (int)tokenizer.nextNumber("number of entry methods");
	tokenizer.checkNextString("interval");
	double intervalSize = 
	    tokenizer.nextScientific("processor usage sample interval"); 
	if (versionNum > 2) {
	    tokenizer.checkNextString("phases");
	    numPhases = (int)tokenizer.nextNumber("phases");
	} else {
	    numPhases = 1;
	}
	if (StreamTokenizer.TT_EOL!=tokenizer.nextToken()) {
	    throw new IOException("extra garbage at end of line 1");
	}

	// prepare to store summary data into arrays
	processorUtil = new int[numIntervals];
	epData = new long[numEPs][NUM_TAGS];

	// Read the SECOND line (processor usage)
	int nUsageRead=0;
	while (StreamTokenizer.TT_NUMBER==(tokenType=tokenizer.nextToken())) {
	    processorUtil[nUsageRead++] = (int)tokenizer.nval;
	}
	// Make sure we're at the end of the line
	if (StreamTokenizer.TT_EOL!=tokenType) {
	    throw new IOException("extra garbage at end of line 2");
	}

	// Read in the THIRD line (time spent by entries)
	int currentUserEntry = 0;
	while ((StreamTokenizer.TT_NUMBER==(tokenType=tokenizer.nextToken()))
	       && (numEPs>currentUserEntry)) {
	    epData[currentUserEntry][TOTAL_TIME] = (int)tokenizer.nval;
	    currentUserEntry++;
	}
	// Make sure we're at the end of the line
	if (StreamTokenizer.TT_EOL!=tokenType) {
	    throw new IOException("extra garbage at end of line 3");
	}

	// Read in the FOURTH line (number of messages)
	currentUserEntry = 0;
	while ((StreamTokenizer.TT_NUMBER==(tokenType=tokenizer.nextToken()))
	       && (numEPs>currentUserEntry)) {
	    epData[currentUserEntry][NUM_MSGS] = (int)tokenizer.nval;
	    currentUserEntry++;
	}
	//Make sure we're at the end of the line
	if (StreamTokenizer.TT_EOL!=tokenType) {
	    throw new IOException("extra garbage at end of line 4");
	}
	
	// Read in the FIFTH line
	int numberofPairs;
	numberofPairs = (int)tokenizer.nextNumber("Number of Marked Events");
	// **CW** for some reason we are ignoring this
	for (int g=0; g<numberofPairs; g++) {
	    tokenizer.nextNumber("Number of Marked Events");
	    tokenizer.nextNumber("Number of Marked Events");
	}
	// Make sure we're at the end of the line
	if (StreamTokenizer.TT_EOL!=tokenType) {
	    throw new IOException("extra garbage at end of line 5");
	}
	
	// Dealing with the phases
	if (numPhases > 1) {
	    phaseData = new long[numPhases][numEPs][NUM_TAGS];
	    for(int m=0; m<numPhases; m++) {		

		// Read total time info
		currentUserEntry = 0;
		tokenizer.nextToken();
		tokenizer.nextToken();
		while ((StreamTokenizer.TT_NUMBER==(tokenType=tokenizer.nextToken())) &&
		       (numEPs>currentUserEntry)) {
		    phaseData[m][currentUserEntry][TOTAL_TIME] = 
			(int)tokenizer.nval;
		    currentUserEntry++;
		}
		// Make sure we're at the end of the line
		if (StreamTokenizer.TT_EOL!=tokenType) {
		    throw new IOException("extra stuff after (I) phase " + m);
		}

		// Read number of messages info
		currentUserEntry = 0;
		tokenizer.nextToken();
		tokenizer.nextToken();
		while ((StreamTokenizer.TT_NUMBER==(tokenType=tokenizer.nextToken())) &&
		       (numEPs>currentUserEntry)) {
		    phaseData[m][currentUserEntry][NUM_MSGS] = 
			(int)tokenizer.nval;
		    currentUserEntry++;
		}
		//Make sure we're at the end of the line
		if (StreamTokenizer.TT_EOL!=tokenType) {
		    throw new IOException("extra stuff after (II) phase " + m);
		}
	    }
	}
	tokenizer = null;
    }
}
