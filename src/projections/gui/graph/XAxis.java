/**
 * XAxis-- describe the basic properties of a bar graph X axis.
 *   Users will inherit from this class to describe their X axis.
 *   The X axis is discrete, in that it consists of integer indices.
 * Orion Sky Lawlor, olawlor@acm.org, 4/2/2002.
 */

package projections.gui.graph;

public abstract class XAxis
{
   /**
    * Return a human-readable string to describe this axis.
    *  e.g., "Processor Number", or "Time Interval"
    */
   public abstract String getTitle();

   /**
    * Return the human-readable name of this index.
    *   Indices run from 0 to DataSource.getLastIndex()-1.
    *   Not all indices will necessarily have their name displayed.
    * e.g., "7", "10-11ms"
    */
   public String getIndexName(int index) { return "" + getIndex(index); }
   public double getIndex(int index) { return index;}
   public double getMultiplier() { return 1;}
}
