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
 * A program to abstract reelevant portions of the FARs from the FAA zip file.
 */
public class FARImporter extends JFrame {

	String windowTitle = "FAR Data Importer";
	ColorPane cpProgress;
	FooterPanel footer;
    Color background = new Color(0xC6D8F9);
    
    File faaZipFile;
    ZipObject zob;
    
    File assets = new File("/Development/Android/FARs/app/src/main/assets");
    
	public static void main(String args[]) {
		new FARImporter();
	}

	/**
	 * Constructor
	 */
	public FARImporter() {
		super();
		initComponents();
		setVisible(true);
		
		try {
			faaZipFile = getFAAFile();
			if (faaZipFile == null) System.exit(0);
			zob = new ZipObject(faaZipFile);
			File dir = new File(System.getProperty("user.dir"));
			String name = faaZipFile.getName().substring(0, "CFR-2021-title".length()) + "14-vol2.xml";
			File faaFile = zob.extractFile(zob.getEntry(name), dir);
			cpProgress.println(faaFile.getAbsolutePath());
			
			Document doc = XmlUtil.getDocument(faaFile);
			Element root = doc.getDocumentElement();
			cpProgress.println("Parsed XML: root element: "+root.getTagName());
			Document far = getParts(root);
			filterExtraneousElements(far);
			filterSectionNumbers(far);
			filterParagraphs(far);
			File local = new File(dir, "FAR.xml");
			FileUtil.setText(local, XmlUtil.toString(far));
			
			int option = JOptionPane.showConfirmDialog(null,
				"Upload FAR file?", "Upload FAR file", JOptionPane.YES_NO_OPTION);
			if (option == JOptionPane.YES_OPTION) {
				assets.mkdirs();
				FileUtil.copy(local, new File(assets, "FAR.xml"));
			}
			
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
	
	private Document getParts(Element faa) throws Exception {
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("FAR");
		doc.appendChild(root);
		root.appendChild(doc.importNode(getPart(faa, "PART 61"), true));
		root.appendChild(doc.importNode(getPart(faa, "PART 91"), true));
		return doc;
	}
	
	private Element getPart(Element root, String partName) throws Exception {
		NodeList parts = root.getElementsByTagName("PART");
		for (int i=0; i<parts.getLength(); i++) {
			 Element part = (Element)parts.item(i);
			 NodeList nl = part.getElementsByTagName("HD");
			 if ((nl.getLength() > 0) && nl.item(0).getTextContent().startsWith(partName)) {
			 	return part;
			}
		 }
		 return null;
	}
	
	private void filterExtraneousElements(Document doc) throws Exception {
		Element root = doc.getDocumentElement();
		remove(root, "AUTH");
		remove(root, "PRTPAGE");
		remove(root, "CITA");
		remove(root, "SOURCE");
	}
	
	private void remove(Element root, String name) throws Exception {
		NodeList nl = root.getElementsByTagName(name);
		cpProgress.println(name+": "+nl.getLength());
		for (int i=nl.getLength()-1; i>=0; i--) {
			Node n = nl.item(i);
			Node parent = n.getParentNode();
			parent.removeChild(n);
		}
	}
	
	private void filterSectionNumbers(Document doc) throws Exception {
		Element root = doc.getDocumentElement();
		filterSectionNumbers(root);
	}
	
	private void filterSectionNumbers(Node n) {
		int type = n.getNodeType();
		if (type == Node.TEXT_NODE) {
			String s = n.getTextContent();
			if (s.contains("§") && n.getParentNode().getNodeName().equals("SECTNO")) {
				cpProgress.println(s);
				s = s.replaceAll("[^§0-9\\.]", "");
				n.setTextContent(s);
			}
		}
		else if (type == Node.ELEMENT_NODE) {
			Element e = (Element)n;
			Node c = e.getFirstChild();
			while (c != null) {
				filterSectionNumbers(c);
				c = c.getNextSibling();
			}
		}
	}
		
	private void filterParagraphs(Document doc) throws Exception {
		Element root = doc.getDocumentElement();
		NodeList nl = root.getElementsByTagName("P");
		for (int i=0; i<nl.getLength(); i++) {
			filterParagraph(nl.item(i));
		}
	}
	
	private void filterParagraph(Node n) {
		if (n.getNodeType() == Node.ELEMENT_NODE) {
			Element e = (Element)n;
			if (e.getTagName().equals("P")) {
				boolean isFirstChildNode = true;
				Node c = e.getFirstChild();
				while (c != null) {
					if (c.getNodeType() == Node.TEXT_NODE) {
						String s = c.getTextContent();
						s = s.replaceAll("[\\s]+"," ");
						if (isFirstChildNode) s = s.replaceAll("^ ","");
						c.setTextContent(s);
						isFirstChildNode = false;
					}
					c = c.getNextSibling();
				}
			}
		}
	}
		
	private void filterWhitespace(Document doc) throws Exception {
		Element root = doc.getDocumentElement();
		filterWhitespace(root);
	}
	
	private void filterWhitespace(Node n) {
		int type = n.getNodeType();
		if (type == Node.TEXT_NODE) {
			String s = n.getTextContent();
			s = s.replaceAll("[\\s]*"," ");
			n.setTextContent(s);
		}
		else if (type == Node.ELEMENT_NODE) {
			Element e = (Element)n;
			Node c = e.getFirstChild();
			while (c != null) {
				filterWhitespace(c);
				c = c.getNextSibling();
			}
		}
	}
		
	private File getFAAFile() {
		JFileChooser chooser = new JFileChooser();
		File here = new File(System.getProperty("user.dir"));
		chooser = new JFileChooser(here);
		chooser.setDialogTitle("Select the FAA FAR zip file");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		else return null;
	}
	
	private void uploadAppFiles() {
		/*
		int option = JOptionPane.showConfirmDialog(null,
			"Upload app files?", "Upload app files", JOptionPane.YES_NO_OPTION);
		if (option == JOptionPane.YES_OPTION) {
			FileUtil.copy(verFile, new File(assets, verFile.getName()));
			FileUtil.copy(aptFile, new File(assets, aptFile.getName()));
			FileUtil.copy(seaFile, new File(assets, seaFile.getName()));
			FileUtil.copy(vorFile, new File(assets, vorFile.getName()));
			FileUtil.copy(ndbFile, new File(assets, ndbFile.getName()));
			FileUtil.copy(fxxFile, new File(assets, fxxFile.getName()));
		}
		*/
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
