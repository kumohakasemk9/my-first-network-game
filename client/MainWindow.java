import javax.swing.*;
import javax.imageio.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.awt.geom.*;
import java.awt.datatransfer.*;

public class MainWindow extends JFrame implements ActionListener,KeyListener,MouseListener, Runnable {
	public static void main(String args[]) throws IOException {new MainWindow();}
	JPanel SCREEN;
	BufferedImage Screen = new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);
	Graphics2D Painter;
	String CmdBuf = null;
	int CmdBufCur,SocketTimeout,SysMsgTim;
	String MsgBuf[] = new String[10];
	int MsgTim[] = {0,0,0,0,0,0,0,0,0,0};
	BufferedImage IMGS[] = new BufferedImage[0];
	int Cooldowns[] = {0,0,0,0};
	int QState = 0;
	int WState = 0;
	int EState = 0;
	int RState = 0;
	int CameraX = 0;
	int CameraY = 0;
	int PlayableId = -1;
	final int MapLimitX = 10000;
	final int MapLimitY = 10000;
	boolean ConnectionInitialized,PreventAuth;
	CharactersObject CHARA[] = new CharactersObject[1000];
	Socket Sock;
	InputStream SockIn;
	OutputStream SockOut;
	Color TextColour = Color.WHITE;
	Color TextBackcolor = new Color(255,255,255,100);
	Color IndicatorColour = Color.RED;
	boolean ConsoleMode = false;
	boolean ShowFukidashi = true;
	String word, SysMsg;
	InetSocketAddress Addr;
	Thread SocketWorker = null;
	int MessageDecreaseTick = 30;
	long ClientPing;
	String[] UserTable;
	int[] UserID;
	int[] AttackRange = {0,0,0,0};
	int[] AttackAngle = {0,0,0,0};
	int[] DAttackRange = {0,0,0,0};
	public MainWindow() throws IOException {
		super("VRUUMO Client");
		boolean antialias = false;
		int fontsize = 0;
		String fontname = "";
		//Load settings file
		if(new File("config.txt").exists()) {
			BufferedReader br = new BufferedReader(new FileReader("config.txt"));
			String line;
			while(true) {
				line = br.readLine();
				if(line == null) {break;}
				line = line.trim();
				if(line.equals("")) {continue;}
				int sep = line.indexOf("=");
				if(sep == -1) {continue;}
				String r = line.substring(0,sep).toLowerCase();
				String v = line.substring(sep + 1);
				if(r.equals("") || v.equals("")) {continue;}
				System.out.printf("Configuration record loaded: \"%s\"=\"%s\"\n" , r,v);
				//Configuration file keys are
				//word, Fontsize, Fontname, antialias, textcolor, indicatorcolor, cui, messagebycharacters, indrange, indangle, indrange2
				if(r.equals("textcolor")) {
					try {TextColour = GetColorFromRGBStr(v, 0);}
					catch (Exception e) {System.out.println("Textcolor record was bad: " + e.toString());}
				}
				if(r.equals("indicatorcolor")) {
					try {IndicatorColour = GetColorFromRGBStr(v, 0);}
					catch (Exception e) {System.out.println("Indicatorcolor record was bad: " + e.toString());}
				}
				if(r.equals("textbackcolor")) {
					try {TextBackcolor = GetColorFromRGBStr(v, 128);}
					catch (Exception e) {System.out.println("Textbackcolor record was bad: " + e.toString());}
				}
				if(r.equals("cui")) {
					if(v.equals("true")) {
						ConsoleMode = true;
						System.out.println("cui=true, this will prevent ingame popup message. (Only shows in terminal.)");
					} else {
						System.out.println("cui=false, In game message popup enabled.");
					}
				}
				if(r.equals("mesbychara")) {
					if(v.equals("true")) {
						System.out.println("mesbychara=true, chat message will be on by the character.");
					} else {
						ShowFukidashi = false;
						System.out.println("mesbychara=false, chat message will only shown in top of screen.");
					}
				}
				if(r.equals("indrange")) {
					try {AttackRange = GetIntegersFromStr(v, 0, 10000, 4);}
					catch (Exception e) {System.out.println("indicatorranges record was bad: " + e.toString());}
				}
				if(r.equals("indangle")) {
					try {AttackAngle = GetIntegersFromStr(v, 0, 360, 4);}
					catch (Exception e) {System.out.println("indicatorangles record was bad: " + e.toString());}
				}
				if(r.equals("indrange2")) {
					try {DAttackRange = GetIntegersFromStr(v, 0, 10000, 4);}
					catch (Exception e) {System.out.println("indicatorranges2 record was bad: " + e.toString());}
				}
				if(r.equals("antialias")) {
					if(v.equals("true")) {
						antialias = true;
						System.out.println("antialias=true, screen antialias enabled.");
					} else {
						System.out.println("antialias=false, screen antialias disabled.");
					}
				}
				if(r.equals("fontsize")) {
					try {fontsize = GetIntegerFromStr(v, 8, 32);}
					catch (Exception e) {System.out.println("fontsize record was bad: " + e.toString());}
				}
				if(r.equals("messagetimeout")) {
					try {MessageDecreaseTick = (10000/GetIntegerFromStr(v,1,10))/66;}
					catch (Exception e) {System.out.println("messagetimeout record was bad: " + e.toString());}
				}
				if(r.equals("font")) {fontname = v;}
				if(r.equals("word")) {
					if(v.getBytes().length < 255) {
						word = v;
					} else {
						System.out.println("word ignored because it is too long.");
					}
				}
			}
			br.close();
		}
		//Preload Images
		int i = 0;
		while (true) {
			File im = new File("images" + File.separator + Integer.toString(i));
			if(im.exists()) {
				BufferedImage[] t = IMGS.clone();
				IMGS = new BufferedImage[IMGS.length + 1];
				System.arraycopy(t, 0, IMGS, 0, t.length);
				try{IMGS[IMGS.length - 1] = ImageIO.read(im);}
				catch(IOException ex) {
					System.out.println("Image read error. Can not continue: " + ex.toString());
					return;
				}
				if(IMGS[IMGS.length - 1] == null) {
					System.out.println("Image read error. Can not continue: " + im.getName());
					return;
				}
			} else {break;}
			i++;
		}
		System.out.printf("Preloaded %d images.\n",i);
		//Prepare Game Screen.
		Painter = Screen.createGraphics();
		Painter.setBackground(Color.BLACK);
		PictureBox p = new PictureBox(Screen);
		SCREEN = p;
		//Apply some options
		if(antialias) {
			Painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		}
		if(fontsize != 0) {
			Painter.setFont(new Font(Painter.getFont().getName(),0,fontsize));
		}
		if(!fontname.equals("")) {
			Painter.setFont(new Font(fontname,0,Painter.getFont().getSize()));
		}
		//Timer for Animation
		javax.swing.Timer t1 = new Timer(15,this);
		t1.setActionCommand("draw");
		t1.start();
		add(p);
		pack();
		addKeyListener(this);
		p.addMouseListener(this);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setResizable(false);
		setVisible(true);
		//PlayableId = 0;
		//UserID = new int[0];
		//UserTable = new String[0];
		//CHARA[0] = new CharactersObject(100, 100, 100, 100, 0, 0, 0, 0);
	}
	//Convert Screen X coordinate To Map X coordinate
	public int TranslateScreenToMapX(int x) {
		return CameraX + x;
	}
	//Y Ver
	public int TranslateScreenToMapY(int y) {
		return  (Screen.getHeight() - 1 - y) + CameraY;
	}
	//Convert Map X coordinate to Screen X coordinate
	public int TranslateMapToScreenX(int x) {
		return x - CameraX;
	}
	public int TranslateMapToScreenY(int y) {
		return Screen.getHeight() - (y - CameraY);
	}
	//Restrict string length in specified width
	public int RestrictString(String s,int w) {
		int len = s.length();
		int i;
		do {
			i = Painter.getFontMetrics().stringWidth(s.substring(0,len));
			len--;
		} while (i >= w && len != 0);
		return len + 1;
	}
	//draws string within specified boundary with background colour
	public Point drawString(String s,int x,int y,int w,int h,Color back) {
		int wl,coff,cl,hl,cy;
		int mwl = 0;
		Color fgcolor = Painter.getColor();
		String t;
		hl = Painter.getFontMetrics().getHeight() + 5;
		coff = 0;
		cy = hl;
		do {
			cl = s.length() + 1;
			//Adjust text length to fit in width
			do {
				cl--;
				t = s.substring(coff,cl);
				wl = Painter.getFontMetrics().stringWidth(t);
			} while(wl > w - 4 && cl > 2);
			Painter.setColor(back);
			//background
			if(cl != s.length()) {
				Painter.fillRect(x,y + cy - hl,w,hl);
				mwl = w;
			} else {
				Painter.fillRect(x,y + cy - hl,wl + 4,hl);
				if(mwl == 0) {mwl = wl + 4;}
			}
			Painter.setColor(fgcolor);
			Painter.drawString(t,x + 2,y + cy - 5);
			coff = cl;
			if(h != 0 && cy > h) {break;}
			cy += hl;
		} while (cl < s.length());
		return new Point(mwl,cy - hl);
	}
	//Shows specified message at main screen
	public void Announce(String s) {
		System.out.println(s);
		for(int i = MsgBuf.length - 1;i > 0;i--) {
			MsgBuf[i] = MsgBuf[i - 1];
			MsgTim[i] = MsgTim[i - 1];
		}
		MsgBuf[0] = s;
		MsgTim[0] = 10000;
	}
	public void AnnounceSysMsg(String s) {
		SysMsg = s;
		SysMsgTim = 10000;
		System.out.println(s);
	}
	//Global disconnection handler
	public void Disconnect() {
		try {
			if(Sock != null) {Sock.close();}
		} catch (IOException e) {}
		PlayableId = -1;
		CHARA = new CharactersObject[1000]; //Clear characters
	}
	public void run() {
		//Network Socket Worker, initialize connections and checks connection availablity.
		//After connection, send authorization code, and check connection was made or not
		try {
			PreventAuth = false;
			ConnectionInitialized = false;
			ClientPing = -1;
			UserID = new int[0];
			UserTable = new String[0];
			Sock = new Socket();
			Sock.connect(Addr);
			SockIn = Sock.getInputStream();
			SockOut = Sock.getOutputStream();
			if(SocketTimeout != 0) {Sock.setSoTimeout(SocketTimeout);}
			//Read ACK
			int b = Readuint8();
			if(b == 'O') {
				ConnectionInitialized = true;
				Announce("Connected to server.");
			} else {
				int s = Readuint8();
				if(s == -1) {
					throw new IOException("Server closed connection.");
				}
				byte[] a = new byte[s];
				ReadData(a);
				Disconnect();
				Announce("Disconnected by server: " + new String(a));
				return;
			}
			ConnectionInitialized = true;
			while(!Sock.isClosed()) {
				long s = System.nanoTime();
				SendSyncCmd();
				//Information Receive handler
				//Information Format
				//AADDMqwer (AA: Altered characters length, DD: Deleted characters index length, M : Extra data length, q: Q cooldown, w: W cooldown, e: E cooldown, r: R cooldown)
				//[IIEEEEEEEEEE][DD] (uu: length([IIEEEEEEEEEEE]), II: element id, EEEEEEEEEEEE: element data, DD: deleted element index)
				//[m] (m: Extra data)
				byte infos[] = new byte[5];
				ReadData(infos); //AADDM
				int altered = getuint16(infos,0);
				int deleted = getuint16(infos,2);
				int extra = Byte.toUnsignedInt(infos[4]);
				if(altered > CHARA.length || deleted > CHARA.length) {
					Announce("Protocol error: too much data");
					Disconnect();
				}
				byte data[] = new byte[altered * 12 + deleted * 2 + extra + 4]; //qwer[IIEEEEEEEEEE][DD][m]
				ReadData(data);
				ClientPing = System.nanoTime() - s;
				for(int i = 0;i < 4;i++) {Cooldowns[i] = Byte.toUnsignedInt(data[i]);} //qwer
				//[IIEEEEEEEEEEE]
				for(int i = 0;i < altered;i++) {
					int id = getuint16(data, i * 12 + 4); //II
					if(id > CHARA.length) {
						Announce("Protocol error: [IIEEEEEEEEEE].");
						Disconnect();
					}
					/*
						EEEEEEEEEEE is 11 bytes struct
						uint8_t imageid;
						uint16_t x;
						uint16_t y;
						uint8_t w;
						uint8_t h;
						uint8_t rotate;
						uint8_t hprate;
						uint8_t statusflag;
					 */
					int imageid = Byte.toUnsignedInt(data[i * 12 + 6]);
					int x = getuint16(data, i * 12 + 7);
					int y = getuint16(data, i * 12 + 9);
					int w = Byte.toUnsignedInt(data[i * 12 + 11]);
					int h = Byte.toUnsignedInt(data[i * 12 + 12]);
					int rotate = data[i * 12 + 13];
					int hprate = Byte.toUnsignedInt(data[i * 12 + 14]);
					int statusflag = Byte.toUnsignedInt(data[i * 12 + 15]);
					CHARA[id] = new CharactersObject(x,y,w,h,rotate,imageid,hprate,statusflag);
				}
				//dd[DD]
				for(int i = 0;i < deleted;i++) {
					int id = getuint16(data, i * 2 + altered * 12 + 4); //DD
					if(id > CHARA.length) {
						Announce("Protocol error: [DD].");
						Disconnect();
					}
					CHARA[id] = null;
				}
				//c[m]
				int doff = altered * 12 + deleted * 2 + 4;
				if(extra > 0) {
					if((data[doff] == 1 || data[doff] == 2) && extra > 3) {
						String param = new String(data,doff + 1,extra-1);
						int k = param.indexOf(1);
						if(k != -1) {
							int uid = 0;
							String cname = param.substring(k + 1);
							if(!cname.equals("")) {
							try {
								uid = new Integer(param.substring(0, k));
								String[] tmp = UserTable.clone();
								UserTable = new String[UserTable.length + 1];
								System.arraycopy(tmp, 0, UserTable, 0, tmp.length);
								UserTable[UserTable.length - 1] = cname;
								int[] tmp2 = UserID.clone();
								UserID = new int[UserID.length + 1];
								System.arraycopy(tmp2, 0, UserID, 0, tmp2.length);
								UserID[UserID.length - 1] = uid;
								if(data[doff] == 1) {Announce(cname + " joined.");}
							} catch(NumberFormatException e) {}
							}
						}
					} else if(data[doff] == 3 && extra > 1) {
						for(int i = 0;i < UserTable.length; i++) {
							String leftuser = new String(data,doff+1,extra-1);
							if(UserTable[i].equals(leftuser)) {
								String[] tmp = UserTable.clone();
								UserTable = new String[UserTable.length - 1];
								System.arraycopy(tmp, 0, UserTable, 0, i);
								if(i + 1 < UserTable.length) {System.arraycopy(tmp, i + 1,UserTable,i,tmp.length - i);}
								int[] tmp2 = UserID.clone();
								UserID = new int[UserID.length - 1];
								System.arraycopy(tmp2, 0, UserID, 0, i);
								if(i + 1 < UserID.length) {System.arraycopy(tmp2, i + 1,UserID,i,tmp2.length - i);}
								Announce(leftuser + " left.");
								break;
							}
						} 
					} else if(data[doff] == 4 && extra > 1) {
						try {
							PlayableId = new Integer(new String(data,doff+1,extra-1));
						} catch (NumberFormatException e) {}
					} else {
						Announce(new String(data,doff,extra));
					}
				}
				//try{Thread.sleep(30);}catch(Exception e) {}
			}
		} catch (IOException ex) {
			Announce("Network error, disconnected. Reason: " + ex.toString());
			Disconnect();
		}
	}
	//Read Function that reads specified length
	void ReadData(byte[] b) throws IOException {
		int i = 0;
		while(i < b.length) {
			int r = SockIn.read(b,i,b.length - i);
			if(r == -1) {throw new IOException("Connection closed.");}
			i += r;
		}
	}
	//Read uint8 from socket
	int Readuint8() throws IOException {
		int i = SockIn.read();
		if(i == -1) {throw new IOException("Connection closed.");}
		return i;
	}
	//Read uint16 from socket
	int Readuint16() throws IOException {
		byte b[] = new byte[2];
		ReadData(b);
		return getuint16(b, 0);
	}
	//Getting uint16 from byte[]
	int getuint16(byte[] data,int offset) {
		return (Byte.toUnsignedInt(data[offset]) << 8) + Byte.toUnsignedInt(data[offset + 1]);
	}
	//Get color form RGB value string splited by spaces
	public Color GetColorFromRGBStr(String s,int Alpha) throws NumberFormatException,IllegalArgumentException {
		int rgb[] = GetIntegersFromStr(s, 0, 255, 3);
		return new Color(rgb[0],rgb[1],rgb[2],Alpha);
	}
	//Get numeric value from string (with limit)
	public int GetIntegerFromStr(String s,int l,int h) throws NumberFormatException,IllegalArgumentException {
		int a = new Integer(s);
		if(!(l <= a && a <= h)) {throw new IllegalArgumentException("Out of range.");}
		return a;
	}
	//Get Numeric values from string
	public int[] GetIntegersFromStr(String s,int l,int h,int n) throws NumberFormatException,IllegalArgumentException {
		String t[] = s.split(" ");
		int r[] = new int[n];
		if(t.length != n) {throw new IllegalArgumentException("Not enough number given.");}
		for(int i = 0;i < n;i++) {
			r[i] = GetIntegerFromStr(t[i], l, h);
		}
		return r;
	}
	public void CommandHandler(String c) {
		int sep = c.indexOf(" ");
		String cmd = c.toLowerCase();
		String param = "";
		if(sep != -1) {
			cmd = cmd.substring(0, sep);
			param = c.substring(sep + 1);
		}
		//System.out.printf("Cmd: \"%s\", Param: \"%s\"\n",cmd,param);
		if(cmd.equals("?connect")) { //Connect command. Connect to game server.
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?connect hostname:port, if :port part was omitted or bad, port will be 15000.");
			} else if(SocketWorker != null && SocketWorker.isAlive()) {
				AnnounceSysMsg("Already connected.");
			} else {
				int p = 15000;
				SocketTimeout = 1000;
				sep = param.indexOf(" ");
				if(sep != -1) {
					String t = param.substring(sep + 1);
					param = param.substring(0,sep);
					if(!t.equals("")) {
						try {SocketTimeout = new Integer(t);}
						catch(NumberFormatException e) {Announce("Time out value bad. Defaulting to 1000");}
					}
				}
				String h = param;
				sep = param.indexOf(":");
				if(sep != -1) {
					String t = param.substring(sep + 1);
					h = param.substring(0,sep);
					if(!t.equals("")) {
						try {p = GetIntegerFromStr(t, 1, 65535);} 
						catch(NumberFormatException e) {Announce("Not valid port number. Defaulting to 15000.");}
						catch(IllegalArgumentException e) {Announce("Port number out of range, must be 1~65535 , defaulting to 15000.");}
					} else {Announce("Bad port number, defaulting to 15000.");}
				}
				Announce("Attempting to connect.");
				Addr = new InetSocketAddress(h,p);
				SocketWorker = new Thread(this);
				SocketWorker.setDaemon(true);
				SocketWorker.start();
			}
		} else if(cmd.equals("?messagetimeout")) {
			//MessageTimeout config command
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?messagetimeout Timeout, Changes times how long messages are shown. Timeout can be 1 ~ 10.");
			} else {
				try {MessageDecreaseTick = (10000/GetIntegerFromStr(param,1,10))/66;}
				catch(NumberFormatException e) {AnnounceSysMsg("Bad MessageTimeout value.");}
				catch(IllegalArgumentException e) {AnnounceSysMsg("MessageTimeout value out of range. must be 60~3000.");}
			}
		} else if(cmd.equals("?fontsize")) {
			//Fontsize config command
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?fontsize size, Changes global font size. (8~32)");
			} else {
				try {
					int i = GetIntegerFromStr(param,8,32);
					Painter.setFont(new Font(Painter.getFont().getName(),0,i));
				} catch(NumberFormatException e) {AnnounceSysMsg("Bad font size.");
				} catch(IllegalArgumentException e) {AnnounceSysMsg("Font size out of range. Must be 8~32.");}
			}
		} else if(cmd.equals("?font")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?font fontname, Changes global font face. If specified font was not found, java's default font will be selected.");
			} else {
				Painter.setFont(new Font(param,0,Painter.getFont().getSize()));
			}
		} else if(cmd.equals("?antialias")) {
			if(param.toLowerCase().equals("true")) {
				Painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				//Painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
			} else if(param.toLowerCase().equals("false")) {
				Painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
				//Painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
			} else if(param.equals("")) {
				AnnounceSysMsg("Usage: ?antialias boolean, Enable or disable antialias. boolean is true or false. Enabling it makes your font smooth, but consumes more machine power.");
			} else {
				AnnounceSysMsg("true or false, please.");
			}
		} else if(cmd.equals("?textcolor")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?textcolor r g b, Changes global text color. r g b represent color values (Red, Green and Blue) and it must be in 0~255.");
			} else {
				try {
					Color tc = GetColorFromRGBStr(param,255);
					if(tc.getRed() == TextBackcolor.getRed() && tc.getGreen() == TextBackcolor.getGreen() && tc.getBlue() == TextBackcolor.getBlue()) {
						AnnounceSysMsg("You are setting same color as background! You will be blind!");
					} else {
						TextColour = tc;
					}
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~255.");}
			}
		} else if(cmd.equals("?textbackcolor")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?textbackcolor r g b, Changes global text background color. r g b represent color values (Red, Green and Blue) and it must be in 0~255.");
			} else {
				try {
					TextBackcolor = GetColorFromRGBStr(param,128);
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~255.");}
			}
		} else if(cmd.equals("?indicatorcolor")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?indicatorcolor r g b, Changes attack indicator color. r g b represent color values (Red, Green and Blue) and it must be in 0~255.");
			} else {
				try {
					IndicatorColour = GetColorFromRGBStr(param,128);
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~255.");}
			}
		} else if(cmd.equals("?indrange")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?setindrange Q W E R, Set attack range (diameter) of each skills. 0 to disable corresponding indicator.");
			} else {
				try {
					AttackRange = GetIntegersFromStr(param, 0, 10000,4);
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~10000.");}
			}
		} else if(cmd.equals("?indangle")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?setindangle Q W E R, Set degree of corn range attack of each skills. 0 means single line, 360 means round.");
			} else {
				try {
					AttackAngle = GetIntegersFromStr(param, 0, 360, 4);
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~360.");}
			}
		} else if(cmd.equals("?indrange2")) {
			if(param.equals("")) {
				AnnounceSysMsg("Usage: ?setindrange Q W E R, Set size of range attack of each skills that can threw away. 0 means disabled.");
			} else {
				try {
					DAttackRange = GetIntegersFromStr(param, 0, 10000, 4);
				} catch (NumberFormatException e) {AnnounceSysMsg("Not valid number.");
				} catch (IllegalArgumentException e) {AnnounceSysMsg("Number out of range, must be 0~10000.");}
			}
		} else if(cmd.equals("?cui")) {
			if(param.toLowerCase().equals("true")) {
				AnnounceSysMsg("Changed to cui mode. Popup message will be hidden in game screen.");
				ConsoleMode = true;
			} else if(param.toLowerCase().equals("false")) {
				AnnounceSysMsg("Changed to gui mode. Popup message will be shown here.");
				ConsoleMode = false;
			} else if(param.equals("")) {
				AnnounceSysMsg("Usage: ?cui boolean, Enable disable cui mode. When enabled, no popup message will be shown on your game screen. This will be useful when you only want chat history in your console. boolean is true or false.");
			} else {
				AnnounceSysMsg("true or false, please.");
			}
		} else if(cmd.equals("?mesbychara")) {
			if(param.toLowerCase().equals("true")) {
				AnnounceSysMsg("Message will be by characters, chat message will be also shown by the speaker.");
				ShowFukidashi = true;
			} else if(param.toLowerCase().equals("false")) {
				AnnounceSysMsg("Message will only shown in message history.");
				ShowFukidashi = false;
			} else if(param.equals("")) {
				AnnounceSysMsg("Usage: ?mesbychara boolean, Enable disable Fukidashi mode. When enabled, popup message will be shown by speaker's character. boolean is true or false.");
			} else {
				AnnounceSysMsg("true or false, please.");
			}
		} else if(cmd.equals("?disconnect")) { //Disconnect command
			if(SocketWorker != null && SocketWorker.isAlive()) {
				Disconnect();
				AnnounceSysMsg("Disconnected.");
			} else {
				AnnounceSysMsg("Already disconnected.");
			}
		} else if(cmd.equals("?")) {
			AnnounceSysMsg("Client commands: ?connect, ?disconnect, ?messagetimeout, ?fontsize, ?font, ?antialias, ?textcolor, ?textbackcolor, ?indicatorcolor, ?indrange, ?indangle, ?indangle2, ?cui, ?msgbychara, ?auth and ?ping. ?auth: send your word (if set in config.txt), ?ping: measure your sync time.");
		} else if(ConnectionInitialized && Sock != null && !Sock.isClosed() && SockOut != null) {
			//Not client command and connected
			if(cmd.equals("?auth") && !PreventAuth && !word.equals("")) {
				//?auth command
				PreventAuth = true; //Only once
				RemoteCommandHandler(word.getBytes());
			} else if(cmd.equals("?ping") && ClientPing != -1) {
				AnnounceSysMsg(String.format("Client data sync time=%f[mS]", (double)ClientPing / 1000000.0));
			} else if(cmd.equals("?users")) {
				String r = "";
				for(String s : UserTable) {
					r += s + " ,";
				}
				if(UserTable.length != 0) {AnnounceSysMsg(r.substring(0,r.length() - 2));}
			} else {
				//other commands
				byte[] ct = c.trim().getBytes();
				if(ct.length < 200) {
					RemoteCommandHandler(ct);
				} else {
					AnnounceSysMsg("Message too long.");
				}
			}
		}
	}
	public synchronized void SendSyncCmd() throws IOException {SockOut.write('I');}
	public synchronized void RemoteCommandHandler(byte[] data) {
		try {
			//Command: Mx[y] (M: 'M', x: length([y]), y: data[])
			byte[] b = new byte[data.length + 2];
			b[0] = 'M';
			b[1] = (byte)data.length;
			System.arraycopy(data,0,b,2,data.length);
			SockOut.write(b);
		} catch(IOException e) {
			System.out.printf("IOException in RemoteCommandHandler(), disconnecting.");
			Disconnect();
		}
	}
	public synchronized void ActionHandler(char a,int x,int y) {
		//System.out.printf("Action Handler at %d,%d\n",x,y);
		if(ConnectionInitialized == false || Sock == null || Sock.isClosed()) {
			return;
		}
		try {
			//Command: Xxxyy : X: command type 'L','Q','W','E','R' (uint8_t) xx: Clicked X (uint16_t) yy: Clicked Y (uint16_t)
			byte b[] = new byte[5];
			b[0] = (byte)a;
			b[1] = (byte)((x >> 8) & 0xff);
			b[2] = (byte)(x & 0xff);
			b[3] = (byte)((y >> 8) & 0xff);
			b[4] = (byte)(y & 0xff);
			SockOut.write(b);
		} catch(IOException e) {
			System.out.printf("IOException in ActionHandler(), disconnecting.");
			Disconnect();
		}
	}
	public int limit(int u,int l,int v) {
		if(v < u) {
			return u;
		} else if(v > l) {
			return l;
		}
		return v;
	}
	public Point FixCoordinateToTarget(int sx, int sy,int dx, int dy,int lim) {
		int rx,ry;
		double r;
		if(dx == sx) {
			r = Math.PI / 2.0;
		} else {
			r = Math.atan((double)Math.abs(dy - sy) / (double)Math.abs(dx - sx));
		}
		int x = (int)((double)lim * Math.cos(r));
		int y = (int)((double)lim * Math.sin(r));
		if(dx > sx) {rx = x;} else {rx = -x;}
		if(dy > sy) {ry = y;} else {ry = -y;}
		return new Point(rx,ry);
	}
	public void drawLine(int x,int y,int angle,int len) {
		double dx = (double)len * Math.cos(Math.toRadians(angle));
		double dy = (double)len * Math.sin(Math.toRadians(angle));
		Painter.drawLine(x, y, x + (int)dx, y + (int)dy);
	}
	public void actionPerformed(ActionEvent e) {
		int lh = Painter.getFontMetrics().getHeight() + 5;
		int ho;
		AffineTransform tback = Painter.getTransform(); //backup transformation info for reverting
		Painter.clearRect(0,0,Screen.getWidth(),Screen.getHeight());
		//If has playable, operate canera and draw indicator
		if(PlayableId != -1 && PlayableId < CHARA.length && CHARA[PlayableId] != null) {
			//adjust camera
			CameraX = limit(0,MapLimitX - Screen.getWidth(),CHARA[PlayableId].x - (Screen.getWidth() / 2));
			CameraY = limit(0,MapLimitY - Screen.getHeight(),CHARA[PlayableId].y - (Screen.getHeight() / 2));
			//draw attack indicator
			Point scr = SCREEN.getMousePosition();
			int atlen = 0;
			int atdeg = 0;
			int datlen = 0;
			if(scr != null) {
				if(QState == 1) {
					atlen = AttackRange[0];
					atdeg = AttackAngle[0];
					datlen = DAttackRange[0];
				}
				if(WState == 1) {
					atlen = AttackRange[1];
					atdeg = AttackAngle[1];
					datlen = DAttackRange[1];
				}
				if(EState == 1) {
					atlen = AttackRange[2];
					atdeg = AttackAngle[2];
					datlen = DAttackRange[2];
				}
				if(RState == 1) {
					atlen = AttackRange[3];
					atdeg = AttackAngle[3];
					datlen = DAttackRange[3];
				}
			}
			if(atlen != 0) {
				int x = TranslateMapToScreenX(CHARA[PlayableId].x);
				int y = TranslateMapToScreenY(CHARA[PlayableId].y);
				Stroke s = Painter.getStroke();
				Painter.setStroke(new BasicStroke(3));
				if(atdeg == 0 || datlen != 0) {
					//Line indicator
					Point t = FixCoordinateToTarget(x, y, (int)scr.getX(), (int)scr.getY(), atlen / 2);
					Painter.setColor(IndicatorColour);
					Painter.drawLine(x, y, x + (int)t.getX(), y + (int)t.getY());
					//Line + Ring indicator
					if(datlen != 0) {
						if(Math.abs(t.getX()) > Math.abs(scr.getX() - x)) {t.x = (int)scr.getX() - x;}
						if(Math.abs(t.getY()) > Math.abs(scr.getY() - y)) {t.y = (int)scr.getY() - y;}
						Painter.setColor(new Color(IndicatorColour.getRed(),IndicatorColour.getGreen(),IndicatorColour.getBlue(),100));
						Painter.fillArc(x + (int)t.getX() - (datlen / 2), y + (int)t.getY() - (datlen / 2), datlen, datlen, 0, 360);
					}
				} else if(atdeg == 360) {
					//Circle type indicator
					Painter.setColor(new Color(IndicatorColour.getRed(),IndicatorColour.getGreen(),IndicatorColour.getBlue(),100));
					Painter.fillArc(x - (atlen / 2), y - (atlen / 2), atlen, atlen, 0, 360);
				} else {
					//Corn Type Indicator
					Painter.setColor(new Color(IndicatorColour.getRed(),IndicatorColour.getGreen(),IndicatorColour.getBlue(),100));
					double d = Math.atan2(y - scr.getY(),scr.getX() - x);
					Painter.fillArc(x - (atlen / 2), y - (atlen / 2), atlen, atlen, (int)(Math.toDegrees(d) - (atdeg / 2)), atdeg * 2);
				}
				Painter.setStroke(s);
			}
		}
		//Draw Character Objects
		int cid = 0;
		for(CharactersObject c : CHARA) {
			if(c != null) {
				int x = TranslateMapToScreenX(c.x) - (c.w / 2);
				int y = TranslateMapToScreenY(c.y) - (c.h / 2);
				//Draw user label
				for(int i = 0; i < UserID.length; i++) {
					if(UserID[i] == cid && UserTable[i] != null) {
						int w = Painter.getFontMetrics().stringWidth(UserTable[i]);
						int labx = x + (c.w / 2) - (w / 2);
						Painter.setColor(TextBackcolor);
						Painter.fillRect(labx, y - lh, w, lh);
						Painter.setColor(TextColour);
						Painter.drawString(UserTable[i], labx, y - 5);
						//Draw Fukidashi
						if(ShowFukidashi) {
							for(int j = 0;j < MsgBuf.length; j++) {
								if(MsgBuf[j] != null && MsgBuf[j].startsWith("[" + UserTable[i] + "] ") && MsgTim[j] > 0) {
									//If current processing character's username matches to message sender
									Painter.setColor(TextColour);
									Point p = drawString(MsgBuf[j].substring(UserTable[i].length() + 3),x + c.w + 2,y,100,c.h,TextBackcolor);
									Painter.fillRect(x + c.w + 2,y,(int)(p.getX() * ((double)MsgTim[j] / 10000.0)),3);
									break;
								}
							}
						}
					}
				}
				//Draw a HP bar if necessary
				if((c.statusflag & 0x80) != 0) {
					Painter.setColor(TextBackcolor);
					Painter.fillRect(x + 1,y + c.h + 2,(int)(((double)c.hprate / 255.0) * (c.w - 1)),5);
					Painter.setColor(TextColour);
					Painter.drawRect(x,y + c.h + 2,c.w,5);
				}
				//Draw a character
				if(c.img > IMGS.length) {
					Painter.setColor(Color.RED);
					Painter.fillRect(x,y,c.w,c.h);
				} else {
					//Draw image
					if((c.statusflag & 0x40) == 0) {
						//Apply rotate
						Painter.rotate(Math.toRadians(c.r),x + (c.w / 2),y + (c.h / 2));
						Painter.drawImage(IMGS[c.img],x,y,c.w,c.h,this);
					} else {
						//Inverted
						//Apply rotate
						Painter.rotate(-Math.toRadians(c.r),x + (c.w / 2),y + (c.h / 2));
						Painter.drawImage(IMGS[c.img], x + c.w, y, -c.w,c.h, this);
					}
					//Reset transformation
					Painter.setTransform(tback);
				}
			}
			cid++;
		}
		//Cooldown Timer Indicator
		for(int i = 0;i < 4;i++) {
			if(Cooldowns[i] != 0) {
				Painter.setColor(TextBackcolor);
				Painter.fillRect((i * 102) + 2, Screen.getHeight() - 10, (int)(100.0 * ((double)Cooldowns[i] / 255.0)), 7);
				Painter.setColor(TextColour);
				Painter.drawRect((i * 102) + 2, Screen.getHeight() - 10, 100, 7);
			}
		}
		ho = 0;
		if(CmdBuf != null && CmdBufCur >= 0) {
			//Input system
			int lw,coff = 0;
			lw = Painter.getFontMetrics().stringWidth(CmdBuf.substring(0,CmdBufCur));
			//Adjust text offset to fit in screen.
			while(lw > Screen.getWidth() - 4) {
				coff++;
				lw = Painter.getFontMetrics().stringWidth(CmdBuf.substring(coff,CmdBufCur));
			}
			//Draw Background
			Painter.setColor(TextBackcolor);
			Painter.fillRect(0,0,Screen.getWidth(),lh + 1);
			//Draw adjusted text.
			Painter.setColor(TextColour);
			Painter.drawString(CmdBuf.substring(coff),2,lh - 5);
			Painter.drawLine(lw + 2,0,lw + 2,lh); //Draw cursor
			//Chat Message History
			if(!ConsoleMode) {
				//Draw Separator Line
				Painter.setColor(Color.WHITE);
				Painter.drawLine(0, lh, Screen.getWidth(), lh);
				//Draw Message history
				int i = 0;
				int off = 0;
				ho = lh + lh;
				while(ho < Screen.getHeight() / 2 && i < MsgBuf.length && MsgBuf[i] != null) {
					int r = RestrictString(MsgBuf[i].substring(off), Screen.getWidth() - 4);
					//Draw Background
					Painter.setColor(TextBackcolor);
					Painter.fillRect(0, ho - lh, Screen.getWidth(), lh);
					//Draw message
					Painter.setColor(TextColour);
					Painter.drawString(MsgBuf[i].substring(off,off + r), 2, ho - 5);
					if(r + off >= MsgBuf[i].length()) {
						i++;
						off = 0;
					} else {
						off += r;
					}
					ho += lh;
				}
			}
		} else if(!ConsoleMode) {
			//If not inputing anything, then show popup message
			ho = 5;
			for(int i = 0;i < MsgBuf.length; i++) {
				if(MsgBuf[i] != null && MsgTim[i] > 0) {
					Point p;
					MsgTim[i] -= MessageDecreaseTick;
					Painter.setColor(TextColour);
					p = drawString(MsgBuf[i],2,ho,Screen.getWidth(),0,TextBackcolor);
					//Draw Remaining Showtime Indicator
					Painter.fillRect(2,ho,(int)(p.getX() * ((double)MsgTim[i] / 10000.0)),3);
					ho += p.getY() + 5;
				}
			}
		}
		if(SysMsgTim > 0) {
			SysMsgTim -= MessageDecreaseTick;
			Painter.setColor(TextColour);
			Point p = drawString(SysMsg, 2, ho, Screen.getWidth(), 0, TextBackcolor);
			Painter.fillRect(2,ho,(int)(p.getX() * ((double)SysMsgTim / 10000.0)),3);
		}
		SCREEN.repaint();
	}
	public void mousePressed(MouseEvent e) {
		Point p = SCREEN.getMousePosition();
		if(e.getButton() == e.BUTTON1) {
			ActionHandler('L',TranslateScreenToMapX((int)p.getX()),TranslateScreenToMapY((int)p.getY()));
		}
		if(e.getButton() == e.BUTTON3) {
			if(QState == 1) {QState = 2;}
			if(WState == 1) {WState = 2;}
			if(EState == 1) {EState = 2;}
			if(RState == 1) {RState = 2;}
		}
	}
	public void keyReleased(KeyEvent e) {
		Point p = SCREEN.getMousePosition();
		if(e.getKeyCode() == e.VK_Q) {
			if(QState == 2 || p == null) {
				QState = 0;
			} else if(QState == 1) {
				QState = 0;
				ActionHandler('Q',TranslateScreenToMapX((int)p.getX()),TranslateScreenToMapY((int)p.getY()));
			}
		}
		if(e.getKeyCode() == e.VK_W) {
			if(WState == 2 || p == null) {
				WState = 0;
			} else if(WState == 1) {
				WState = 0;
				ActionHandler('W',TranslateScreenToMapX((int)p.getX()),TranslateScreenToMapY((int)p.getY()));
			}
		}
		if(e.getKeyCode() == e.VK_E) {
			if(EState == 2 || p == null) {
				EState = 0;
			} else if(EState == 1) {
				EState = 0;
				ActionHandler('E',TranslateScreenToMapX((int)p.getX()),TranslateScreenToMapY((int)p.getY()));
			}
		}
		if(e.getKeyCode() == e.VK_R) {
			if(RState == 2 || p == null) {
				RState = 0;
			} else if(RState == 1) {
				RState = 0;
				ActionHandler('R',TranslateScreenToMapX((int)p.getX()),TranslateScreenToMapY((int)p.getY()));
			}
		}
	}
	public void keyPressed(KeyEvent e) {
		if(CmdBuf != null) {
			if(e.getKeyCode() == e.VK_LEFT && CmdBufCur > 0) {CmdBufCur--;}
			if(e.getKeyCode() == e.VK_RIGHT && CmdBufCur < CmdBuf.length()) {CmdBufCur++;}
			if(e.getKeyCode() == e.VK_ENTER) {
				CmdBuf = CmdBuf.trim();
				enableInputMethods(false);
				if(!CmdBuf.equals("")) {CommandHandler(CmdBuf);}
				CmdBuf = null;
			}
		} else {
			if(e.getKeyCode() == e.VK_ENTER) {
				enableInputMethods(true);
				CmdBuf = "";
				CmdBufCur = 0;
			}
			if(e.getKeyCode() == e.VK_Q && QState == 0 && WState != 1 && EState != 1 && RState != 1 && Cooldowns[0] == 0) {QState = 1;}
			if(e.getKeyCode() == e.VK_W && WState == 0 && QState != 1 && EState != 1 && RState != 1 && Cooldowns[1] == 0) {WState = 1;}
			if(e.getKeyCode() == e.VK_E && EState == 0 && QState != 1 && WState != 1 && RState != 1 && Cooldowns[2] == 0) {EState = 1;}
			if(e.getKeyCode() == e.VK_R && RState == 0 && QState != 1 && WState != 1 && EState != 1 && Cooldowns[3] == 0) {RState = 1;}
		}
	}
	public void keyTyped(KeyEvent e) {
		if(CmdBuf != null) {
			if(e.getKeyChar() == '\b') {
				//Backspace
				if(CmdBufCur > 0) {
					CmdBuf = CmdBuf.substring(0,CmdBufCur - 1) + CmdBuf.substring(CmdBufCur);
					CmdBufCur--;
				}
			} else if(e.getKeyChar() == 127) {
				//Delete
				if(CmdBufCur < CmdBuf.length()) {CmdBuf = CmdBuf.substring(0,CmdBufCur) + CmdBuf.substring(CmdBufCur + 1);}
			} else if(e.getKeyChar() == 22) {
				//Ctrl + V (0b1010110 + ctrl = 0b10110)
				Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
				if(c.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
					try {
						String s = (String)c.getData(DataFlavor.stringFlavor);
						if(s != null) {
							CmdBuf = CmdBuf.substring(0,CmdBufCur) + s + CmdBuf.substring(CmdBufCur);
							CmdBufCur += s.length();
						}
					}
					catch (UnsupportedFlavorException ex) {System.out.println("Software bug detected!");}
					catch(IOException ex) {System.out.println("IO Error.");}
				}
			} else if(e.getKeyChar() >= 0x20) {
				//Number, alphabets and symbols
				CmdBuf = CmdBuf.substring(0,CmdBufCur) + e.getKeyChar() + CmdBuf.substring(CmdBufCur);
				CmdBufCur++;
			}
		}
	}
	public void mouseExited(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
}
