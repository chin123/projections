package projections.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class JIntTextField extends JTextField
{
   private String lastValue;
   private int    lastCaretPosition;
   
   public JIntTextField(int defval, int size)
   {
	  super("" + defval, size);
	  addKeyListener(new KeyAdapter()
	  {
		 public void keyTyped(KeyEvent evt)
		 {
			char ch = evt.getKeyChar();
			if(!('0' <= ch && ch <= '9' || Character.isISOControl(ch)))
			   evt.consume();
			else
			   lastCaretPosition = getCaretPosition();
		 }
	  });
	  lastValue = "" + defval;
   }   
   private void checkValue()
   {
	  try
	  {
		 Integer.parseInt(getText().trim() + "0");
		 lastValue = getText();
	  }
	  catch(NumberFormatException e)
	  {
		 setText(lastValue);
		 setCaretPosition(lastCaretPosition);
	  }
   }   
   public int getValue()
   {
	  checkValue();
	  try
	  {
		 return Integer.parseInt(getText().trim());
	  }
	  catch(NumberFormatException e)
	  {
		 return 0;
	  }
   }   

    // public void setText(String text) - implemented by superclass

    /**
     *  Sets a value in the text box.
     */
    public void setValue(int num) {
	lastValue = getText();
	setText(String.valueOf(num));
	checkValue();
    }

   public void textValueChanged(TextEvent evt)
   {
	  checkValue();
   }   
}