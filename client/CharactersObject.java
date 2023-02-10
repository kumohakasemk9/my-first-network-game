public class CharactersObject {
	public int x;
	public int y;
	public int w;
	public int h;
	public int r;
	public int img;
	public int hprate;
	public int statusflag; //Statusflag 0x80: Show HP Bar
	public CharactersObject(int initx,int inity,int width,int height,int degree,int imageid,int hpr,int stat) {
		x = initx;
		y = inity;
		w = width;
		h = height;
		r = degree;
		img = imageid;
		hprate = hpr;
		statusflag = stat;
	}
}
