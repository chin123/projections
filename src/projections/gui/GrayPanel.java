package projections.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Panel;

class GrayPanel extends Panel
{

public GrayPanel()
   {
	  setBackground(Color.lightGray);
   }   
   public void paint(Graphics g)
   {
	  g.setColor(Color.black);
	  g.drawRect(0, 0, getSize().width-1, getSize().height-1);
	  
	  super.paint(g);
   }   
}