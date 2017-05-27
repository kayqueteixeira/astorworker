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

import br.ufg.inf.astorworker.executors.CommandExecutorProcess;
import br.ufg.inf.astorworker.executors.AndroidToolsExecutorProcess;
import br.ufg.inf.astorworker.faultlocalization.entities.Line;
import br.ufg.inf.astorworker.TestType;

public class AndroidFaultLocalization {
	private static String projectLocation;
	private static String projectName;
	private static Logger logger = Logger.getRootLogger();


	private static AbstractHashedMap<String, Line> faulty = new HashedMap();


	public static void setupFaultLocalization(String projctName) 
			throws IOException, InterruptedException {

		projectName = projctName;
		projectLocation = "workDir/AstorWorker-" + projectName + "/faultLocalization/" 
				+ projectName;

		CommandExecutorProcess.execute("chmod -R 777 " + projectLocation);

		//Copying local.properties
		CommandExecutorProcess.execute("cp local.properties " + projectLocation);

		// Moving all the tests to workDir/tests
		copyAllTests(projectLocation, "workDir/AstorWorker-" + projectName 
				+ "/faultLocalization/tests");

		deleteAllTestsFromProject(projectLocation);

		// Creating the reports dir
		CommandExecutorProcess.execute("mkdir -p workDir/AstorWorker-" + projectName 
	    								+ "/faultLocalization/reports/");
		// Slow build
		AndroidToolsExecutorProcess.compileProject(projectLocation, 100000);

		// Modifying build.gradle to run jacoco
		setupBuildGradle(projectLocation);
	}


	public static List<Line> searchSuspicious(String test, TestType type, boolean passing) 
			throws IOException, InterruptedException, ParserConfigurationException, SAXException {

		switch(type){
			case INSTRUMENTATION:
				generateXML(test, "AndroidTest",
							"workDir/AstorWorker-" + projectName + "/faultLocalization/tests/instrumentation", 
							projectLocation + "/app/src/androidTest/java", projectLocation);
				break;

			case UNIT:
				generateXML(test, "UnitTest", 
							"workDir/AstorWorker-" + projectName + "/faultLocalization/tests/unit", 
							projectLocation + "/app/src/test/java", projectLocation);
				break;
		}

		return processXML(test, passing);
	}

	private static void generateXML(String test, String testIdentifier, String testsLocation, String projectTestsLocation, 
									String projectLocation) 
									throws IOException, InterruptedException  {

    	copySingleTest(testsLocation, test, projectTestsLocation);

    	// Fast build
    	List<String> output = AndroidToolsExecutorProcess.recompileProject(projectLocation);

    	List<String> testTasks = new ArrayList<>();

    	for(String line : output){
    		if(line.startsWith("TASK") && line.contains("Debug") && line.contains(testIdentifier))
    			testTasks.add(line.split(":")[1]);
    	}

    	int testTaskCount = 0;

    	for(String task : testTasks){
    		// Running test task
    		AndroidToolsExecutorProcess.runGradleTask(projectLocation, task);

    		// Running jacoco coverage
    		List<String> coverageOutput = AndroidToolsExecutorProcess.runGradleTask(projectLocation, task + "Coverage");

    		// Checking if the task was skipped
    		for(String line : coverageOutput){
    			if(line.contains("SKIPPED")){
    				logger.info("Test task \"" + task + "Coverage\" was skipped");
    				continue;
    			}
    		}

    		// Saving xml at the reports folder
    		CommandExecutorProcess.execute("cp " + projectLocation + "/app/build/reports/jacoco/" + task + "Coverage"
    								   + "/" + task + "Coverage" + ".xml workDir/AstorWorker-" + projectName 
    								   + "/faultLocalization/reports/" + test + "_" + testTaskCount + ".xml");

    		testTaskCount++;

    		logger.info("Report " + test + "_" + testTaskCount + ".xml was created");
    	}

    	deleteAllTestsFromProject(projectLocation);	
	}

	private static List<Line> processXML(String test, boolean passing) 
			throws IOException, ParserConfigurationException, SAXException {

		logger.info("Processing XMLs for the test " + test);
		
		List<Line> candidates = new ArrayList<>();

		String testXML = "workDir/AstorWorker-" + projectName + "/faultLocalization/reports/" + test + "_" + "%d.xml";

		for(int testTaskCount = 0 ; new File(String.format(testXML, testTaskCount)).exists() ; testTaskCount++){
			logger.info("Processing " + test + "_" + testTaskCount + ".xml");

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    	factory.setValidating(false);
			factory.setNamespaceAware(true);
			factory.setFeature("http://xml.org/sax/features/namespaces", false);
			factory.setFeature("http://xml.org/sax/features/validation", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	    	DocumentBuilder builder = factory.newDocumentBuilder();

	    	Document testXMLFile = builder.parse(String.format(testXML, testTaskCount));
	    	
	    	NodeList sourcefileList = testXMLFile.getElementsByTagName("sourcefile");
	    	
	    	for (int i = 0; i < sourcefileList.getLength() ; i++ ) {
	    		Node node = sourcefileList.item(i);
	    		
	    		if(node.getNodeType() == Node.ELEMENT_NODE){
	    			Element sourcefile = (Element) node;
	    			String className = sourcefile.getAttribute("name").split(".java")[0];
	    			logger.info("Source found on " + String.format(testXML, testTaskCount) + ": " + className);

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
		}
		
    	return candidates;
	}

	private static void copyAllTests(String projectLocation, String dest) 
		throws IOException, InterruptedException {

		File file = new File(dest + "/instrumentation");
		file.mkdirs();
		file = new File(dest + "/unit");
		file.mkdirs();
		CommandExecutorProcess.execute("cp -a " + projectLocation + "/app/src/androidTest/java/. "
									   + dest + "/instrumentation");
		CommandExecutorProcess.execute("cp -a " + projectLocation + "/app/src/test/java/. " + dest
									   + "/unit");
	}

	private static void deleteAllTestsFromProject(String projectLocation) 
			throws IOException, InterruptedException {

		List<String> dirsToDelete = CommandExecutorProcess.execute("ls " + projectLocation 
				+ "/app/src/androidTest/java/");

		for(String dir : dirsToDelete)
			CommandExecutorProcess.execute("rm -r " + dir, projectLocation + "/app/src/androidTest/java/");		

		dirsToDelete = CommandExecutorProcess.execute("ls " + projectLocation + "/app/src/test/java/");

		for(String dir : dirsToDelete)
			CommandExecutorProcess.execute("rm -r " + dir, projectLocation + "/app/src/test/java/");
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

	private static void copySingleTest(String testLocation, String test, String projectTestLocation) 
			throws IOException, InterruptedException {

	 	String testPath = test.replaceAll("\\.", "/") + ".java";
	 	File testFile = new File(projectTestLocation + "/" + testPath);
    	testFile.getParentFile().mkdirs();
    	CommandExecutorProcess.execute("cp " + testLocation + "/" + testPath + " " + testFile.getParentFile().getCanonicalPath());
	}

}