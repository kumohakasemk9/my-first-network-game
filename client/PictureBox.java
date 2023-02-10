import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

public class PictureBox extends JPanel {
	public BufferedImage InternalScreen;
	public PictureBox(BufferedImage img) {
		super();
		InternalScreen = img;
		setPreferredSize(new Dimension(img.getWidth(),img.getHeight()));
	}
	public void paint(Graphics g) {
		g.drawImage(InternalScreen,0,0,this);
	}
}
