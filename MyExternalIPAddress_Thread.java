package imapClient;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
/**
 * This thread checks the external IP address and compares it with the IP address that should be 
 * (externalIPHost). If they match, the label (Form.label_externalIP) is marked green, if not - red.
 *  - Form.label_externalIP - JLabel object on the main form displaying the result of checking the external IP address
 *  - Form.propFile - pathname string to txt-file that consists properties 
 *  - proxySet - use proxy or not
 *  - socksProxyHost - proxy host
 *  - socksProxyPort - proxy port
 *  - externalIPSet - checking external IP address or not
 *  - externalIPName - name just display in label
 *  - externalIPHost - IP address which the user expects to see
 *  - externalIPInterval - periodicity checking in seconds
 */
public class MyExternalIPAddress_Thread extends Thread{
	
	public boolean isCancelled;
	private String proxySet = "";
	private String socksProxyHost = "";
	private String socksProxyPort = "";	
	private String externalIPSet = "";
	private String externalIPName = "";
	private String externalIPHost = "";
	private String externalIPInterval = "";
		
	public void cancel() {
		isCancelled = true;
		
		//reset label when thread is canceled
		Form.label_externalIP.setText("");
		
		this.interrupt();
	}

	public void run() {
		isCancelled = false;
		while(!isCancelled) {
			
			//obtaining the necessary properties
			File file = new File(Form.propFile);
			try {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(
								new FileInputStream(file), Charset.forName("UTF-8")
								)
						);
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("proxySet")) proxySet = line.substring(line.indexOf("proxySet")+9);
					else if (line.startsWith("socksProxyHost")) socksProxyHost = line.substring(line.indexOf("socksProxyHost")+15);
					else if (line.startsWith("socksProxyPort")) socksProxyPort = line.substring(line.indexOf("socksProxyPort")+15);
					else if (line.startsWith("externalIPSet ")) externalIPSet = line.substring(line.indexOf("externalIPSet")+14);
					else if (line.startsWith("externalIPName ")) externalIPName = line.substring(line.indexOf("externalIPName")+15);
					else if (line.startsWith("externalIPHost ")) externalIPHost = line.substring(line.indexOf("externalIPHost")+15);
					else if (line.startsWith("externalIPInterval ")) externalIPInterval = line.substring(line.indexOf("externalIPInterval")+19);
				}
				reader.close();
			} catch (Exception ex) {
				addExToLogs(ex);
			}			
			if (isCancelled) break;
			
			//validating properties and setting proxy properties
		    Properties props = System.getProperties();
			if (proxySet.equalsIgnoreCase("true")) {
				if (Form.incorrectHost(socksProxyHost, true) || Form.incorrectPort(socksProxyPort, true)) {
					isCancelled = true;
					Form.label_externalIP.setText("PROXY ERROR");
					Form.label_externalIP.setToolTipText("Error: You entered an incorrect IP address or port of the SOCKS host!");
					Form.label_externalIP.setBackground(Color.RED);					
					break;
				}
				props.setProperty("proxySet", proxySet);
		        props.setProperty("socksProxyHost", socksProxyHost);
		        props.setProperty("socksProxyPort", socksProxyPort);		        
			} else {
			    props.remove("proxySet");
			    props.remove("socksProxyHost");
			    props.remove("socksProxyPort");				
			}			
			if (isCancelled) break;
			
			
			if (externalIPSet.equalsIgnoreCase("true")) {
				
				//receiving ip address
				String externalIP = getExternalIP();
				if (isCancelled) break;
				
				//displaying the received ip address and checking for a match with the ip address that was expected
				//if the one that was expected is green, if not - red
				String text = externalIPName + ": " + externalIP;
				if (text.length() > 19) text = "<html>" + externalIPName + ":<br>" + externalIP;
				Form.label_externalIP.setText(text);
				Form.label_externalIP.setToolTipText("it should be " + externalIPHost);
				if (externalIP.equals(externalIPHost) ) {
					Form.label_externalIP.setBackground(Color.GREEN);
				} else Form.label_externalIP.setBackground(Color.RED);
				
				//the thread went to sleep))
				try {
					if (incorrectExternalIPInterval(externalIPInterval)) {						
						this.cancel();
						break;
					}
					Thread.sleep(Integer.parseInt(externalIPInterval)*1000);
				} catch (InterruptedException ex) {
					if (ex.getMessage().equals("sleep interrupted")) break;
					else addExToLogs(ex);
				}			
			} else this.cancel();
		}
	}

	/**
	 * URL request is redirected to InputStream to InputStreamReader to BufferedReader.
	 * Reading reader and cut only ip address.
	 * Checking the ip address and if it is correct we return it.
	 */
	private String getExternalIP() {
		String result = "null";
        try {
            BufferedReader reader = null;
            try {
            	URL url = new URL("https://myip.by/");
                InputStream inputStream = null;
                inputStream = url.openStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder allText = new StringBuilder();
                char[] buff = new char[1024];
 
                int count = 0;
                while ((count = reader.read(buff)) != -1) {
                    allText.append(buff, 0, count);
                }
                // The string containing the IP is as follows <a href="whois.php?127.0.0.1">whois 127.0.0.1</a> 
                Integer indStart = allText.indexOf("\">whois ");
                Integer indEnd = allText.indexOf("</a>", indStart);
                
                if (indStart == -1 || indEnd == -1) {
                	JOptionPane.showMessageDialog(null, "<html><font color=red>Error:</font> An incorrect response was received when requesting an external IP address!", "ImapClient", JOptionPane.ERROR_MESSAGE);
                	return "null";
                }
                
                String ipAddress = new String(allText.substring(indStart + 8, indEnd));
                if (correctExternalIP(ipAddress)) result = ipAddress;
            } catch (Exception ex) {
    	    	addExToLogs(ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ex) {
                    	addExToLogs(ex);
                    }
                }
            }
        } catch (Exception ex) {
        	addExToLogs(ex);
        }
        return result;
	}
	
    private Boolean correctExternalIP(String testString) { 
    	Pattern p = Pattern.compile("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");  
    	Matcher m = p.matcher(testString);
    	if (m.matches()) return true;
    	Form.addLogs("Error: Received incorrect external IP-address!\r\n\n", -1);
        return false;
    }
    
    private Boolean incorrectExternalIPInterval(String testString) { 
	    Pattern p = Pattern.compile("^[0-9]+$");
	    Matcher m = p.matcher(testString);
	    int testInt = -1;
	    try {
	    	testInt = Integer.parseInt(testString);
	    } catch (Exception ex) {}
	    if (!m.matches() || testInt<1 || testInt>65535) {
	    	Form.addLogs("Error: An incorrect external IP check interval has been entered!\r\n\n", -1);
	    	return true; 
	    }
    	return false;
    }    
      
	private void addExToLogs(Exception ex) {
		Form.saveExceptions(ex, "imapClient.MyExternalIPAddress_Thread");//ex.printStackTrace();
		String exception = ex.getClass().getName();
		String exMsg = ex.getMessage();
		if (exMsg != null) exception += ": " + exMsg;
		else exception += " at " + ex.getStackTrace()[0].toString();
		Throwable cause = ex.getCause();
		if (cause != null) exception = exception + " Caused by: " + cause.getMessage();
		Form.addLogs("ERROR [imapClient.MyExternalIPAddress_Thread]: "+exception+"\r\n", -1);	
	}
}