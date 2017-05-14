package br.ufg.inf.astorworker.entity;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;

import br.ufg.inf.astorworker.executors.CommandExecutorProcess;
import br.ufg.inf.astorworker.executors.AndroidToolsExecutorProcess;
import fr.inria.astor.core.setup.ConfigurationProperties;

/**
 * 
 * @author Kayque de Sousa Teixeira
 *
 */
public class Project {

	private File project;
	private String projectName;
	private String projectLocation;
	private String mainPackage;
	private String dependencies;
	private String failing;
	private String instrumentalFailing;
	private String regression;
	private String instrumentalRegression;
	private int waitTime;
	private Logger logger = Logger.getLogger(Project.class);

	public Project(File project, String projectName, int waitTime) {
		this.project = project;
		this.projectName = projectName;
		this.waitTime = waitTime;
	}

	private void getProjectInformation() throws IOException, InterruptedException, IllegalStateException {
		logger.info("Getting project information");
		
		projectLocation = project.getAbsolutePath();
		
		findMainPackage();

		AndroidToolsExecutorProcess.uninstallAPK(mainPackage + ".test", waitTime);

		AndroidToolsExecutorProcess.uninstallAPK(mainPackage, waitTime);

		AndroidToolsExecutorProcess.compileProject(projectLocation, waitTime);

		AndroidToolsExecutorProcess.generateAPK(projectLocation, waitTime);
		
		AndroidToolsExecutorProcess.generateTestAPK(projectLocation, waitTime);

		AndroidToolsExecutorProcess.signAPK(projectLocation + "/app/build/outputs/apk/app-release-unsigned.apk", waitTime);
		
		AndroidToolsExecutorProcess.signAPK(projectLocation + "/app/build/outputs/apk/app-debug-androidTest.apk", waitTime);

		AndroidToolsExecutorProcess.installAPK(projectLocation + "/app/build/outputs/apk/app-debug-androidTest.apk", waitTime);

		AndroidToolsExecutorProcess.installAPK(projectLocation + "/app/build/outputs/apk/app-release-unsigned.apk", waitTime);		

		findDependencies();

		findJUnitTests();

		findInstrumentationTests();

		AndroidToolsExecutorProcess.uninstallAPK(mainPackage, waitTime);

		AndroidToolsExecutorProcess.uninstallAPK(mainPackage + ".test", waitTime);


		logger.info("Project information:");
		logger.info("Project location: " + projectLocation);
		logger.info("Main package: " + mainPackage);
		logger.info("Failing junit tests: " + failing);
		logger.info("Junit tests for regression: " + regression);
		logger.info("Failing instrumentation tests: " + instrumentalFailing);
		logger.info("Instrumentation tests for regression: " + instrumentalRegression);
		logger.info("Dependencies: " + dependencies);
	}

	private void findMainPackage() throws FileNotFoundException, IOException {
		logger.info("Finding the name of the main package");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectLocation + "/app/src/main/AndroidManifest.xml")));
		String line = null;

		while((line = br.readLine()) != null){
			if(line.contains("package"))
				mainPackage = line.split("\"")[1];
		}
		br.close();
	}


	private void findDependencies() throws IOException, InterruptedException {
		logger.info("Finding dependencies");

		dependencies = "";
		List<String> output = CommandExecutorProcess.execute("find . -type f -name *.jar",projectLocation + "/app/build/intermediates/");

		for(String entry : output)
			dependencies += project.getAbsolutePath() + "/app/build/intermediates/" + entry + ":";
		dependencies += ConfigurationProperties.getProperty("androidjar");
	}

	private void findJUnitTests() throws IOException, InterruptedException {
		logger.info("Finding failing junit tests");
		failing = "";
		List<String> output = CommandExecutorProcess.execute("./gradlew test", projectLocation);
		List<String> regressionTestCases = new ArrayList<String>();
		for(String entry : output){
			if(entry.contains("FAILED")){
				String tokens[] = entry.split(">");
				if(tokens[0].contains("Test") && tokens.length == 2)
					failing += tokens[0].trim()  +  ":";
			}

			//Looking for regression test cases
			if(entry.startsWith("Executing test")){
				String testCaseForRegression = entry.split("\\[")[1].split("\\]")[0];
				regressionTestCases.add(testCaseForRegression);
			}
		}

		if(failing.equals("")){
			logger.info("There is no failing junit tests");
			failing = null;
		}
		else failing = failing.substring(0, failing.length()-1);
		
		logger.info("Finding junit tests for regression");
		if(regressionTestCases.isEmpty()){
			logger.info("There is no junit tests for regression");
			regression = null;
		}
		else {
			regressionTestCases = new ArrayList<String>(new LinkedHashSet<String>(regressionTestCases));
			regression = String.join(":" , regressionTestCases);
		}
	}

	private void findInstrumentationTests() throws IOException, InterruptedException {
		logger.info("Finding failing instrumentation tests");
		instrumentalFailing = "";
		List<String> instrumentalRegressionTestCases = new ArrayList<String>();
		List<String> output = AndroidToolsExecutorProcess.runInstrumentationTests(mainPackage, waitTime);
		for(String entry : output){
			if(entry.contains("Error in")){
				instrumentalFailing  += entry.split("\\(")[1].split("\\)")[0].trim() +  ":";
			}

			//Looking for regression test cases
			if(entry.startsWith(mainPackage)){
				String testCaseForRegression = entry.split(":")[0];
				instrumentalRegressionTestCases.add(testCaseForRegression);
			}
		}

		if(instrumentalFailing.equals("")){
			logger.info("There is no failing instrumentation tests");
			instrumentalFailing = null;
		}
		else instrumentalFailing = instrumentalFailing.substring(0, instrumentalFailing.length()-1);
		
		
		logger.info("Finding instrumentation tests for regression");
		if(instrumentalRegressionTestCases.isEmpty()){
			logger.info("There is no instrumentation tests for regression");
			instrumentalRegression = null;
		}
		else {
			instrumentalRegressionTestCases = new ArrayList<String>(new LinkedHashSet<String>(instrumentalRegressionTestCases));
			instrumentalRegression = String.join(":" , instrumentalRegressionTestCases);
		}
	}


	public void setupProject() throws IOException, InterruptedException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		//Getting all the permissions
		CommandExecutorProcess.execute("chmod -R 777 " + getProjectName(), project.getParentFile().getAbsolutePath());

		//Copying local.properties
		CommandExecutorProcess.execute("cp local.properties " + project.getAbsolutePath());

		getProjectInformation();

		//Creating the default bin dir
		File defaultBin = new File("workDir/AstorWorker-" + getProjectName() + "/default/bin");
		ConfigurationProperties.setProperty("defaultbin", defaultBin.getAbsolutePath());
		defaultBin.mkdirs();

		CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/release/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
		CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/test/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
		CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/androidTest/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");



		//Creating the default source dir
		File defaultSrc = new File("workDir/AstorWorker-" + getProjectName() + "/default/src");
		ConfigurationProperties.setProperty("defaultsrc", defaultSrc.getAbsolutePath());
		defaultSrc.mkdirs();

		CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/src/main/java/. workDir/AstorWorker-" + getProjectName() + "/default/src");
	}

	public String getProjectName(){
		return projectName;
	}

	public String getDependencies(){
		return dependencies;
	}

	public String getFailing(){
		return failing;
	}

	public String getInstrumentationFailing(){
		return instrumentalFailing;
	}

	public String getMainPackage(){
		return mainPackage;
	}

	public String getLocation(){
		return project.getAbsolutePath();
	}

	public String getParentPath(){
		return project.getParentFile().getAbsolutePath();
	}

	public String getRegressionTestCases(){
		return regression;
	}

	public String getInstrumentalRegressionTestCases(){
		return instrumentalRegression;
	}
}