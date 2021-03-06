package vreAnalyzer.Text2HTML;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class Text2HTML {
	
	
	private Map<String,String>HTMLtoJava;
	public Text2HTML(File inputFile,File outPutFile){
		FileReader input = null;
		FileWriter output = null;
		BufferedReader br = null;
		BufferedWriter wt = null;
		HTMLtoJava = new HashMap<String,String>();
		try {
			input = new FileReader(inputFile);
			output = new FileWriter(outPutFile);
			br = new BufferedReader(input);
			wt = new BufferedWriter(output);
			String line;
			while((line = br.readLine()) != null){				
				line+='\n';
				String htmlString = txtToHtml(line);
				HTMLtoJava.put(htmlString, line);
				wt.write(htmlString);
			}
			
			input.close();
			br.close();
			wt.close();
			output.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static String txtToHtml(String s) {
	    StringBuilder builder = new StringBuilder();
	    boolean previousWasASpace = false;
	    for( char c : s.toCharArray() ) {
	        if( c == ' ' ) {
	            if( previousWasASpace ) {
	                builder.append("&nbsp;");
	                previousWasASpace = false;
	                continue;
	            }
	            previousWasASpace = true;
	        } else {
	            previousWasASpace = false;
	        }
	        switch(c) {
	            case '<': builder.append("&lt;"); break;
	            case '>': builder.append("&gt;"); break;
	            case '&': builder.append("&amp;"); break;
	            case '"': builder.append("&quot;"); break;
	            case '\n': builder.append("<br>"); break;
	            // We need Tab support here, because we print StackTraces as HTML
	            case '\t': builder.append("&nbsp; &nbsp; &nbsp;"); break;  
	            default:
	                if( c < 128 ) {
	                    builder.append(c);
	                } else {
	                    builder.append("&#").append((int)c).append(";");
	                }    
	        }
	    }
	    return builder.toString();
	}
	public Map getHTMLMapping(){
		return HTMLtoJava;
	}
}
