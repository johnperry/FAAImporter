package org.jp.importer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.w3c.dom.*;

/**
 * A program to assemble XML and text files containing airport identifiers and
 * lat/long coordinates from the text contained in the FAA 28-day-subscription
 * zip file. The current FAA zip file is available at:
 * https://www.faa.gov/air_traffic/flight_info/aeronav/aero_data/NASR_Subscription/
 */
public class FAAImporter extends JFrame {

	String windowTitle = "FAA Data Importer";
	ColorPane cpProgress;
	FooterPanel footer;
    Color background = new Color(0xC6D8F9);
    
    File faaZipFile;
    ZipObject zob;
    File aptFile = new File("APT.txt");
    File navFile = new File("NAV.txt");
    File twrFile = new File("TWR.txt");
    File awosFile = new File("AWOS.txt");
    File airFile = new File("FAAFiles/Airports.txt");
    File seaFile = new File("FAAFiles/Seaports.txt");
    File ndbFile = new File("FAAFiles/NDBs.txt");
    File vorFile = new File("FAAFiles/VORs.txt");
    File xmlFile = new File("Airports.xml");
    
	Hashtable<String,Airport> aptIndex = null;
	Hashtable<String,Airport> aptTable = null;
	Hashtable<String,NAV> navIndex = null;
	
	public static void main(String args[]) {
		new FAAImporter();
	}

	/**
	 * Constructor
	 */
	public FAAImporter() {
		super();
		initComponents();
		setVisible(true);
		BufferedReader reader;
		String line;
		String text;
		
		try {
			faaZipFile = getFAAFile();
			if (faaZipFile == null) System.exit(0);
			zob = new ZipObject(faaZipFile);
			cpProgress.println("Creating the FAAFiles directory");
			File faaFiles = new File("FAAFiles");
			faaFiles.mkdirs();
			
			cpProgress.print("Expanding the APT.txt file ");
			File aptFile = zob.extractFile(zob.getEntry("APT.txt"), faaFiles);
			cpProgress.println(aptFile.getAbsolutePath());
			
			cpProgress.print("Expanding the NAV.txt file ");
			File navFile = zob.extractFile(zob.getEntry("NAV.txt"), faaFiles);
			cpProgress.println(navFile.getAbsolutePath());
			
			cpProgress.print("Expanding the TWR.txt file ");
			File twrFile = zob.extractFile(zob.getEntry("TWR.txt"), faaFiles);
			cpProgress.println(twrFile.getAbsolutePath());
			
			cpProgress.print("Expanding the AWOS.txt file ");
			File awosFile = zob.extractFile(zob.getEntry("AWOS.txt"), faaFiles);
			cpProgress.println(awosFile.getAbsolutePath());
			
			cpProgress.print("Expanding the README.txt file ");
			File readMe = zob.extractFile(zob.getEntry("README.txt"), faaFiles);
			cpProgress.println(readMe.getAbsolutePath());

			int count = 0;
			aptIndex = new Hashtable<String,Airport>();
			aptTable = new Hashtable<String,Airport>();
			cpProgress.println("Processing the APT.txt file");
			reader = new BufferedReader(new FileReader(aptFile));
			while ( (line = reader.readLine()) != null ) {
				String type = line.substring(0,3);
				String lineID = line.substring(3,14).trim();
				String base = line.substring(14,27);
				if (type.equals("APT") && (base.startsWith("AIRPORT") || base.startsWith("SEAPLANE"))) {
					Airport airport = new Airport(line);
					aptIndex.put(lineID, airport);
					aptTable.put(airport.id, airport);
					footer.setMessage((++count)+": "+airport.id+" "+airport.state + " " + lineID);
				}
				else if (type.equals("RWY")) {
					Runway runway = new Runway(line);
					Airport airport = aptIndex.get(lineID);
					if (airport != null) airport.add(runway);
				}
			}
			reader.close();

			//Get the frequencies
			reader = new BufferedReader(new FileReader(twrFile));
			cpProgress.println("Processing the TWR.txt file");
			while ( (line = reader.readLine()) != null ) {
				int len = line.length();
				if (len > 8) {
					String type = line.substring(0,4);
					String id = line.substring(4,8).trim();
					if (type.equals("TWR3")) {
						Airport airport = aptTable.get(id);
						if (airport != null) {
							for (int k=8; (k+94<len) && (k<854); k+=94) {
								String freq = line.substring(k, k+44).trim();
								String use = line.substring(k+44, k+94).trim();
								if (!freq.equals("") && !use.equals("") && freq.startsWith("1")) airport.add(new Freq(freq, use));
							}
						}
					}
				}
			}
			reader.close();
			
			//Get the AWOS frequencies
			reader = new BufferedReader(new FileReader(awosFile));
			cpProgress.println("Processing the AWOS.txt file");
			while ( (line = reader.readLine()) != null ) {
				int len = line.length();
				if (len > 112) {
					String rec = line.substring(0,5);
					if (rec.equals("AWOS1")) {
						String sts = line.substring(19,20);
						if (sts.equals("Y")) {
							String type = line.substring(9,19).trim();
							String freq = line.substring(68,75).trim();
							String id = line.substring(110,121).trim();
							Airport airport = aptIndex.get(id);
							if (!freq.equals("") && (airport != null)) {
								airport.add(new Freq(freq, type));
							}
						}
					}
				}
			}
			reader.close();
			
			cpProgress.println(count + " airports found");
			Airport[] airports = new Airport[aptTable.size()];
			airports = aptTable.values().toArray(airports);
			Arrays.sort(airports);
			cpProgress.println("Writing the Airports.txt file");
			text = getAptText(airports, "AIR");
			FileUtil.setText(airFile, text);
			cpProgress.println("Writing the Seaports.txt file");
			text = getAptText(airports, "SEA");
			FileUtil.setText(seaFile, text);
			cpProgress.println("Writing the Airports.xml file");
			Document doc = getXML(airports);
			text = XmlUtil.toPrettyString(doc);
			text = text.replace("    ", " ");
			FileUtil.setText(xmlFile, text);

			count = 0;
			navIndex = new Hashtable<String,NAV>();
			cpProgress.println("Processing the NAV.txt file");
			reader = new BufferedReader(new FileReader(navFile));
			while ( (line = reader.readLine()) != null ) {
				if (line.startsWith("NAV1")) {
					NAV nav = new NAV(line);
					if (!nav.status.startsWith("DECOMMISSIONED")) {
						navIndex.put(nav.id, nav);
						footer.setMessage((++count)+": "+nav.id+" "+nav.state);
					}
				}
			}
			reader.close();
			
			cpProgress.println(count + " navaids found");
			NAV[] navs = new NAV[navIndex.size()];
			navs = navIndex.values().toArray(navs);
			Arrays.sort(navs);
			cpProgress.println("Writing the NDBs.txt file");
			text = getNavText(navs, "NDB");
			FileUtil.setText(ndbFile, text);
			cpProgress.println("Writing the VORs.txt file");
			text = getNavText(navs, "VO");
			FileUtil.setText(vorFile, text);
			cpProgress.println("\nDone.");
		}
		catch (Exception ex) {
			try {
				StringWriter sw = new StringWriter();
				ex.printStackTrace(new PrintWriter(sw));
				cpProgress.println(sw.toString());
			}
			catch (Exception x) { }			
		}
	}
	
	private File getFAAFile() {
		JFileChooser chooser = new JFileChooser();
		File here = new File(System.getProperty("user.dir"));
		chooser = new JFileChooser(here);
		chooser.setDialogTitle("Select the FAA 28DaySubscription zip file");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		else return null;
	}

	private String getAptText(Airport[] airports, String type) {
		StringBuffer sb = new StringBuffer();
		for (Airport a : airports) {
			if (a.type.equals(type)) {
				sb.append(a.toString());
			}
		}
		return sb.toString();
	}
	
	private String getNavText(NAV[] navs, String type) {
		StringBuffer sb = new StringBuffer();
		for (NAV nav : navs) {
			if (nav.type.startsWith(type)) {
				sb.append(nav.toString());
			}
		}
		return sb.toString();
	}
	
	private Document getXML(Airport[] airports) throws Exception {
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("Airports");
		doc.appendChild(root);
		for (Airport a : airports) {
			root.appendChild(a.toXML(doc));
		}
		return doc;
	}
	
	class Airport implements Comparable<Airport> {
		String type;
		String lineID;
		String id;
		String name;
		String city;
		String state;
		LinkedList<Runway> runways;
		String lat = "lat";
		String lon = "lon";
		String elev = "elev";
		String var = "var";
		LinkedList<Freq> freqs;
		
		public Airport(String line) {
			type = line.substring(14,17);
			lineID = line.substring(3, 14);
			id = line.substring(27,31).trim();
			state = line.substring(91,93);
			city = capitalize(line.substring(93,133), state);
			name = capitalize(line.substring(133,183), state);
			elev = line.substring(578, 583).trim();
			lat = getLat(line.substring(523, 537));
			lon = getLon(line.substring(550, 565));
			var = line.substring(586,589).trim();
			if (var.startsWith("0")) var = var.substring(1);
			if (var.endsWith("W")) {
				var = "-" + var.substring(0, var.length()-1).trim();
			}
			else if (var.endsWith("E")) {
				var = var.substring(0, var.length()-1).trim();
			}
			runways = new LinkedList<Runway>();
			freqs = new LinkedList<Freq>();
			String unicom = line.substring(981, 988).trim();
			add(new Freq(unicom, "UNICOM"));
			String ctaf = line.substring(988, 995).trim();
			add(new Freq(ctaf, "CTAF"));
		}
		
		public void add(Runway runway) {
			if (!runway.length.equals("0") && !runway.id.endsWith("X")) {
				runways.add(runway);
			}
		}
		
		public void add(Freq freq) {
			if (!freq.freq.equals("")) freqs.add(freq);
		}
		
		public int compareTo(Airport a) {
			return id.compareTo(a.id);
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(id + "|");
			sb.append(name + "|");
			sb.append(city + "|");
			sb.append(state + "|");
			sb.append(lat + "," + lon + "|");
			sb.append(elev + "|");
			StringBuffer rwySB = new StringBuffer();
			for (Runway runway : runways) {
				if (rwySB.length() > 0) rwySB.append(";");
				rwySB.append(runway.toString());
			}
			sb.append(rwySB.toString() + "|");
			sb.append(var + "|");
			StringBuffer freqSB = new StringBuffer();
			for (Freq freq : freqs) {
				if (freqSB.length() > 0) freqSB.append(";");
				freqSB.append(freq.toString());
			}
			sb.append(freqSB.toString());
			sb.append("\n");
			return sb.toString();
		}
		
		public Element toXML(Document doc) {
			Element a = doc.createElement("Airport");
			a.setAttribute("id", id);
			a.setAttribute("name", name);
			a.setAttribute("city", city);
			a.setAttribute("state", state);
			a.setAttribute("lat", lat);
			a.setAttribute("lon", lon);
			a.setAttribute("elev", elev);
			a.setAttribute("var", var);
			for (Runway r : runways) {
				Element rwy = doc.createElement("rwy");
				rwy.setAttribute("id", r.id);
				rwy.setAttribute("len", r.length);
				rwy.setAttribute("wid", r.width);
				rwy.setAttribute("type", r.type);
				a.appendChild(rwy);
			}
			return a;
		}
	}

	class Runway {
		String id;
		String length;
		String width;
		String type;
		
		public Runway(String line) {
			id = line.substring(16,23);
			if (id.startsWith("0")) id = id.substring(1);
			id = id.trim();
			length = line.substring(23,28).trim();
			width = line.substring(28,32).trim();
			type = line.substring(32,44).trim();
		}
		
		public String toString() {
			return id + ": " + length + "x" + width + ", " + type;
		}
	}

	class Freq {
		String freq;
		String use;
		
		public Freq(String freq, String use) {
			this.freq = freq;
			this.use = use;
		}
		
		public String toString() {
			return freq + " - " + use;
		}
	}
	
	class NAV implements Comparable<NAV> {
		String type;
		String status;
		String id;
		String freq;
		String name;
		String city;
		String state;
		String lat = "lat";
		String lon = "lon";
		String var = "var";

		public NAV(String line) {
			type = line.substring(8,28).trim();
			status = line.substring(766,796).trim();
			id = line.substring(28,32).trim();
			freq = line.substring(533,539).trim();
			state = line.substring(142,144);
			city = capitalize(line.substring(72,112), state);
			name = capitalize(line.substring(42,72), state);
			lat = getLat(line.substring(371, 384));
			lon = getLon(line.substring(396, 410));
			var = line.substring(481,484).trim();
			if (var.startsWith("0")) var = var.substring(1);
			if (var.endsWith("W")) {
				var = "-" + var.substring(0, var.length()-1).trim();
			}
			else if (var.endsWith("E")) {
				var = var.substring(0, var.length()-1).trim();
			}
		}
		
		public int compareTo(NAV n) {
			return id.compareTo(n.id);
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(id + "|");
			sb.append(type + "|");
			sb.append(freq + "|");
			sb.append(name + "|");
			sb.append(city + "|");
			sb.append(state + "|");
			sb.append(lat + "," + lon + "|");
			sb.append(var);
			sb.append("\n");
			return sb.toString();
		}
	}

	//42-08-54.9590N
	private String getLat(String s) {
		int NS = s.length()-1;
		double deg = Double.parseDouble(s.substring(0,2));
		double min = Double.parseDouble(s.substring(3,5));
		double sec = Double.parseDouble(s.substring(6,NS));
		double lat = deg + min/60 + sec/3600;
		if (s.charAt(NS) == 'S') lat = -lat;
		return String.format("%.4f",lat);
	}

	//088-33-43.0095W
	private String getLon(String s) {
		int EW = s.length()-1;
		double deg = Double.parseDouble(s.substring(0,3));
		double min = Double.parseDouble(s.substring(4,6));
		double sec = Double.parseDouble(s.substring(7,EW));
		double lon = deg + min/60 + sec/3600;
		if (s.charAt(EW) == 'W') lon = -lon;
		return String.format("%.4f",lon);
	}
		
	private String capitalize(String s, String state) {
		s = s.replaceAll("\\s+"," ");
		s = s.trim();
		s = s.toUpperCase();
		s.replaceAll("\\s*/\\s*","/");
		String[] words = s.split("\\s");
		StringBuffer sb = new StringBuffer();
		for (String w : words) {
			if (w.length() > 0) {
				if (w.equals(state)) {
					sb.append(w);
				}
				else if (w.equals("OHARE")) {
					sb.append("O'Hare");
				}
				else if (w.equals("DUPAGE")) {
					sb.append("DuPage");
				}
				else if (w.equals("DEKALB")) {
					sb.append("DeKalb");
				}
				else if (w.equals("GTR")) {
					sb.append("Greater");
				}
				else if (w.equals("DE")) {
					sb.append("de");
				}
				else if (w.startsWith("O'") && (w.length() > 2)) {
					sb.append(w.substring(0,3) + w.substring(3).toLowerCase());
				}
				else if (w.startsWith("D'") && (w.length() > 2)) {
					sb.append("d'" + w.charAt(2) + w.substring(3).toLowerCase());
				}
				else {
					sb.append(w.substring(0,1));
					sb.append(w.substring(1).toLowerCase());
				}
				sb.append(" ");
			}
		}
		s = sb.toString();
		if (s.endsWith("/")) s = s.substring(0, s.length()-1);
		s = s.replaceAll("\\s+"," ").trim();
		s = s.replace("de Kalb", "DeKalb");
		int k = s.indexOf("-");
		if ((k > 0) && (k < s.length()-1)) {
			s = s.substring(0, k+1) 
					+ s.substring(k+1, k+2).toUpperCase() 
						+ s.substring(k+2);
		}
		k = s.indexOf("/");
		if ((k > 0) && (k < s.length()-1)) {
			s = s.substring(0, k+1) 
					+ s.substring(k+1, k+2).toUpperCase() 
						+ s.substring(k+2);
		}
		k = s.indexOf("(");
		if ((k > 0) && (k < s.length()-1)) {
			s = s.substring(0, k+1) 
					+ s.substring(k+1, k+2).toUpperCase() 
						+ s.substring(k+2);
		}
		
		return s;
	}
	
	void initComponents() {
		setTitle(windowTitle);

		JPanel progressPanel = new JPanel(new BorderLayout());
		progressPanel.setBackground(background);
		progressPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		cpProgress = new ColorPane();
		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(cpProgress);
		progressPanel.add(jsp, BorderLayout.CENTER);

		footer = new FooterPanel();
		getContentPane().add(progressPanel, BorderLayout.CENTER);
		getContentPane().add(footer, BorderLayout.SOUTH);
		pack();
		centerFrame();
		
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				exit(evt);
			}
		});
	}
	
	class FooterPanel extends JPanel {
		public JLabel message;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEADING));
			this.setBackground(background);
			message = new JLabel(" ");
			this.add(message);
		}
		public void setMessage(String msg) {
			final String s = msg;
			Runnable display = new Runnable() {
				public void run() {
					message.setText(s);
				}
			};
			SwingUtilities.invokeLater(display);
		}
	}
	
	void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width/2, scr.height/2);
		setLocation (new Point ((scr.width-getSize().width)/2,
								(scr.height-getSize().height)/2));
	}

	void exit(java.awt.event.WindowEvent evt) {
		System.exit(0);
	}
}
