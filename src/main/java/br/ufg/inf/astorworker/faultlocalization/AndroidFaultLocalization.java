package br.ufg.inf.astorworker.faultlocalization;

import java.io.IOException;
import java.io.FileWriter;
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
import br.ufg.inf.astorworker.faultlocalization.entities.Line;
import br.ufg.inf.astorworker.TestType;

public class AndroidFaultLocalization {
	private static String projectLocation;
	private static String projectName;
	private static boolean unitBuildGradleFullyModified;
	private static boolean instrumentationBuildGradleFullyModified;
	private static Logger logger = Logger.getRootLogger();


	private static final String ACTIVATE_COVERAGE_STRING = "\n\nandroid {\n"
			+ "\tbuildTypes {\n\t\trelease {\n\t\t\ttestCoverageEnabled true\n\t\t}\n"
			+ "\t\tdebug {\n\t\t\ttestCoverageEnabled true\n\t\t}\n\t}\n}\n\n"
			+ "apply plugin: 'jacoco'\n\njacoco {\n\ttoolVersion = '0.7.9'\n}\n\n"
			+ "def coverageSourceDirs = [\n\t'../app/src/main/java'\n]";

	private static String JACOCO_TASK_STRING = "\n\ntask %s(type: JacocoReport) {\n"
			+ "\tgroup = \"Reporting\"\n" 
			+ "\tdescription = \"Generate Jacoco coverage reports after running tests.\"\n"
			+ "\treports {\n\t\txml.enabled = true\n\t\thtml.enabled = true\n"
			+ "\t\tcsv.enabled = true\n\t}\n\n\tclassDirectories = fileTree(\n"
			+ "\t\tdir: './build/intermediates/classes/debug',\n\t\texcludes: ['**/R*.class'"
			+ ",\n\t\t\t\t'**/*$InjectAdapter.class',\n\t\t\t\t'**/*$ModuleAdapter.class',\n"
			+ "\t\t\t\t'**/BuildConfig.*',\n\t\t\t\t'**/*$ViewInjector*.class'\n\t])\n\n"
			+ "\tsourceDirectories = files(coverageSourceDirs)\n\texecutionData = files("
			+ "\"$buildDir/%s/%s\")\n\n\tdoFirst {\n"
			+ "\t\tnew File(\"$buildDir/intermediates/classes/\").eachFileRecurse { file ->\n"
			+ "\t\t\tif (file.name.contains('$$')) {\n\t\t\t\tfile.renameTo(file.path.replace"
			+ "('$$', '$'))\n\t\t\t}\n\t\t}\n\t}\n}";

	private static AbstractHashedMap<String, Line> faulty = new HashedMap();


	public static void setupFaultLocalization(String projctName) 
			throws IOException, InterruptedException {

		projectName = projctName;
		projectLocation = "workDir/AstorWorker-" + projectName + "/faultLocalization/" 
				+ projectName;

		CommandExecutorProcess.execute("chmod -R 777 "+projectLocation);

		//Creating local.properties
		CommandExecutorProcess.execute("echo sdk.dir=$ANDROID_HOME | tee local.properties", projectLocation);

		instrumentationBuildGradleFullyModified = false;
		unitBuildGradleFullyModified = false;

		// Moving all the tests to workDir/tests
		copyAllTests(projectLocation, "workDir/AstorWorker-" + projectName 
				+ "/faultLocalization/tests");

		deleteAllTestsFromProject(projectLocation);

		// Slow build
		CommandExecutorProcess.execute("./gradlew build", projectLocation);

		// Modifying build.gradle for the first time
		appendToBuildGradle(projectLocation, ACTIVATE_COVERAGE_STRING);
	}


	public static List<Line> searchSuspicious(String test, TestType type, boolean passing) 
			throws IOException, InterruptedException, ParserConfigurationException, SAXException {

		switch(type){
			case INSTRUMENTATION:
				generateXML(test, 
							"workDir/AstorWorker-" + projectName + "/faultLocalization/tests/instrumentation", 
							projectLocation + "/app/src/androidTest/java", projectLocation, 
							"connectedAndroidTest", "outputs/code-coverage/connected", "ec", 
							"jacocoInstrumentationTestReport", !instrumentationBuildGradleFullyModified);

				instrumentationBuildGradleFullyModified = true;
				break;

			case UNIT:
				generateXML(test, 
							"workDir/AstorWorker-" + projectName + "/faultLocalization/tests/unit", 
							projectLocation + "/app/src/test/java", projectLocation, "test", "jacoco", "exec", 
							"jacocoUnitTestReport", !unitBuildGradleFullyModified);

				unitBuildGradleFullyModified = true;
				break;
		}

		return processXML(test, passing);
	}

	private static void generateXML(String test, String testsLocation, String projectTestsLocation, 
									String projectLocation, String gradlewTestArg, String subBuildCoverageLocation, 
									String coverageFileExtension, String jacocoTaskName, boolean modifyBuildGradle) 
									throws IOException, InterruptedException  {

    	copySingleTest(testsLocation, test, projectTestsLocation);

    	// Fast build
    	CommandExecutorProcess.execute("./gradlew -a -m build", projectLocation);

    	// connectedAndroidTest
    	CommandExecutorProcess.execute("./gradlew -a " + gradlewTestArg, projectLocation);

    	if(modifyBuildGradle){
    		// Finding the name of the .ec file
	    	List<String> findOutput = CommandExecutorProcess.execute("find . -name *." + coverageFileExtension, 
	    				projectLocation + "/app/build/");

	    	if(findOutput.size() == 0){
	    		logger.error("Search didn't find any " + coverageFileExtension + ": " + findOutput);
	    		System.exit(1);
	    	}

	    	if(findOutput.size() > 1){
	    		logger.error("Search found more than 1 " + coverageFileExtension + ": " + findOutput);
	    		System.exit(1);
	    	}

	    	String tokens[] = findOutput.get(0).split("/");
	    	String coverage = tokens[tokens.length-1];
	    	logger.info("Coverage file: "+coverage);

	    	// Modifying build.gradle for the second time
	    	appendToBuildGradle(projectLocation, String.format(JACOCO_TASK_STRING, jacocoTaskName, 
	    			subBuildCoverageLocation, coverage));

	    	CommandExecutorProcess.execute("mkdir -p workDir/AstorWorker-" + projectName 
	    								   + "/faultLocalization/reports/");
    	}


    	// Executing jacocoTestReport
    	CommandExecutorProcess.execute("./gradlew -a " + jacocoTaskName, projectLocation);
    	
    	CommandExecutorProcess.execute("cp " + projectLocation + "/app/build/reports/jacoco/" + jacocoTaskName
    								   + "/" + jacocoTaskName + ".xml workDir/AstorWorker-" + projectName 
    								   + "/faultLocalization/reports/" + test + ".xml");

    	logger.info("Report " + test + ".xml created");

    	deleteAllTestsFromProject(projectLocation);
	}

	private static List<Line> processXML(String test, boolean passing) 
			throws IOException, ParserConfigurationException, SAXException {

		logger.info("Processing the XML of the test " + test);
		String testXML = "workDir/AstorWorker-" + projectName + "/faultLocalization/reports/" + test + ".xml";

		List<Line> candidates = new ArrayList<>();

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
    			logger.info("Source found on "+testXML+": "+className);

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

	private static void appendToBuildGradle(String projectLocation, String toAppend) throws IOException {
		FileWriter fw = new FileWriter(projectLocation + "/app/build.gradle", true); 
	    fw.write(toAppend);
	    fw.close();
	}

	private static void copySingleTest(String testLocation, String test, String projectTestLocation) 
			throws IOException, InterruptedException {

	 	String testPath = test.replaceAll("\\.", "/") + ".java";
	 	File testFile = new File(projectTestLocation + "/" + testPath);
    	testFile.getParentFile().mkdirs();
    	CommandExecutorProcess.execute("cp " + testLocation + "/" + testPath + " " + testFile.getParentFile().getCanonicalPath());
	}

}