package br.ufg.inf.astorworker.faultlocalization;

import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.collections4.map.AbstractHashedMap;
import org.apache.commons.collections4.MapIterator;
import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;

import br.ufg.inf.astorworker.executors.AndroidToolsExecutorProcess;
import br.inf.ufg.astorworker.utils.FileSystemUtils;
import br.ufg.inf.astorworker.faultlocalization.entities.Line;
import br.ufg.inf.astorworker.TestType;
import br.inf.ufg.astorworker.utils.FileSystemUtils;

public class AndroidFaultLocalization {
	private static String projectLocation;
	private static String projectName;
	private static String unitTestTask;
	private static String instrumentationTestTask;
	private static Logger logger = Logger.getRootLogger();


	private static AbstractHashedMap<String, Line> faulty = new HashedMap();

	public static String setUnitTestTask(String task){
		return unitTestTask = task;
	}

	public static String setInstrumentationTestTask(String task){
		return instrumentationTestTask = task;
	}

	public static void setProjectLocation(String location){
		projectLocation = location;
	}

	public static void setProjectName(String name){
		projectName = name;
	}

	public static void setupFaultLocalization() 
			throws IOException, InterruptedException {

		// Creating the reports dir
		new File("workDir/AstorWorker-" + projectName + "/faultLocalization/reports/").mkdirs();

		// Modifying build.gradle to run jacoco
		setupBuildGradle(projectLocation);
	}


	public static List<Line> searchSuspicious(String test, TestType type, boolean passing) throws Exception {

		boolean result = generateXML(test, type);
		if(result)
			return processXML(test, passing);
		else
			return new ArrayList<Line>();
	}

	private static boolean generateXML(String test, TestType type) throws Exception {

		//Removing old .ec and .exec files
		FileSystemUtils.findFilesWithExtensionAndDelete(new File(projectLocation + "/app/"), "ec");
		FileSystemUtils.findFilesWithExtensionAndDelete(new File(projectLocation + "/app/"), "exec");

    	List<String> testTasks = new ArrayList<>();

    	String task = null;

    	if(type.equals(TestType.INSTRUMENTATION))
    		task = instrumentationTestTask;
    	

    	if(type.equals(TestType.UNIT))
    		task = unitTestTask;

    	logger.info("Task selected: " + task);

    	List<String> output = null;

		// Running test task
		if(type.equals(TestType.INSTRUMENTATION))
			output = AndroidToolsExecutorProcess.runGradleTask(projectLocation, "-Pandroid.testInstrumentationRunnerArguments.class=" + test + " " + task, false);

		if(type.equals(TestType.UNIT))
			output = AndroidToolsExecutorProcess.runGradleTask(projectLocation, task + " --tests=" + test.replaceAll("#","\\."), false);

		for(String line : output){
			if(line.contains("No tests found")){
				logger.info("\"" + test + "\" is not a test!");
				return false;
			}
		}

		// Running jacoco coverage
		List<String> coverageOutput = AndroidToolsExecutorProcess.runGradleTask(projectLocation, "--continue " + task + "Coverage", false);

		// Checking if the task was skipped
		for(String line : coverageOutput){
			if(line.contains("SKIPPED")){
				logger.info("Test task \"" + task + "Coverage\" was skipped");
				return false;
			}

			if(line.contains("BUILD SUCCESSFUL"))
				logger.info("Test task \"" + task + "Coverage\" was successful");

			if(line.contains("BUILD FAILED")){
				logger.info("Test task \"" + task + "Coverage\" failed");
				return false;
			}
		}

		// Saving xml at the reports folder
		FileUtils.copyFile(new File(projectLocation + "/app/build/reports/jacoco/" + task + "Coverage/" + task + "Coverage.xml"),
						 	new File("workDir/AstorWorker-" + projectName + "/faultLocalization/reports/" + test.replaceAll("#", "\\.") + ".xml"));

		logger.info("Report " + test.replaceAll("#", "\\.") + ".xml was created");
		return true;	
	}

	private static List<Line> processXML(String test, boolean passing) 
			throws IOException, ParserConfigurationException, SAXException {

		logger.info("Processing XMLs for the test " + test);
		
		List<Line> candidates = new ArrayList<>();

		String testXML = "workDir/AstorWorker-" + projectName + "/faultLocalization/reports/" + test.replaceAll("#", "\\.") + ".xml";

		logger.info("Processing " + test.replaceAll("#", "\\.") + ".xml");

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	factory.setValidating(false);
		factory.setNamespaceAware(true);
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    	DocumentBuilder builder = factory.newDocumentBuilder();

    	Document testXMLFile = builder.parse(testXML);
    	
    	NodeList sourcefileList = testXMLFile.getElementsByTagName("sourcefile");
    	
    	for (int i = 0; i < sourcefileList.getLength() ; i++ ) {
    		Node node = sourcefileList.item(i);
    		
    		if(node.getNodeType() == Node.ELEMENT_NODE){
    			Element sourcefile = (Element) node;
    			String className = sourcefile.getAttribute("name").split(".java")[0];
    			logger.info("Source found on " + testXML + ": " + className);

    			NodeList childList = sourcefile.getChildNodes();

    			for (int j = 0; j < childList.getLength() ; j++) {
    				Node child = childList.item(j);

    				if (child.getNodeType() == Node.ELEMENT_NODE) {
    					Element childElement = (Element) child;

    					//If childElement is a line and the line was executed by the test
    					if(childElement.getTagName() == "line" 
    							&&  Integer.parseInt(childElement.getAttribute("ci")) != 0){

    						candidates.add(new Line(Integer.parseInt(childElement.getAttribute("nr")), 
	    									className, test, passing));
    					}	
    				}
    			}
    		}
    		
    	}
		
		logger.info("Found "+candidates.size()+" candidates");
    	return candidates;
	}

	private static void setupBuildGradle(String projectLocation) throws IOException {
   		BufferedWriter out = new BufferedWriter(new FileWriter(projectLocation + "/app/build.gradle", true));
    	BufferedReader in = new BufferedReader(new FileReader("coverage.gradle"));
    	String line;

       	while ((line = in.readLine()) != null) 
            out.write("\n" + line);
        
    	in.close();
   		out.close();
	}
}