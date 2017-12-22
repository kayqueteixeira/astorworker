package br.ufg.inf.astorworker.entity;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.collections4.map.AbstractHashedMap;

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
	private String testPackage;
	private String dependencies;
	private String failing;
	private String instrumentalFailing;
	private boolean regression;
	private boolean instrumentationRegression;
	private Logger logger = Logger.getLogger(Project.class);
	private AbstractHashedMap<String, String> testRunners; 
	private List<String> flavors;
	private String flavor;
	private String testRunner;
	private Pattern functionPattern; 

	public Project(File project, String projectName) throws Exception {
		this.project = project;
		this.projectName = projectName;
		this.flavor = null;
		this.instrumentalFailing = null;
		this.failing = null;
		this.functionPattern = Pattern.compile("\\s*((public|private|protected)\\s+)?(static\\s+)?([a-zA-Z_0-9<>]+)(\\s+)(\\w+)\\s*\\(.*?\\)\\s*\\{?\\s*");
		this.projectLocation = project.getAbsolutePath();
		activateTestLogging();
	}

	private void getProjectInformation() throws Exception {
		logger.info("Getting project information");

		findMainPackage();
		findTestPackage();

		AndroidToolsExecutorProcess.uninstallAPK(testPackage);
		AndroidToolsExecutorProcess.uninstallAPK(mainPackage);

		findDependencies();
		findFlavors();	
		findRegressionTestCases();

		logger.info("Project information:");
		logger.info("Project location: " + projectLocation);
		logger.info("Main package: " + mainPackage);
		logger.info("Test package: " + testPackage);
		logger.info("Failing junit tests: " + failing);
		logger.info("Failing instrumentation tests: " + instrumentalFailing);
		logger.info("Dependencies: " + dependencies);
	}

	public void activateTestLogging() throws Exception {
   		BufferedWriter out = new BufferedWriter(new FileWriter(projectLocation + "/app/build.gradle", true));
    	BufferedReader in = new BufferedReader(new FileReader("test.gradle"));
    	String line;

       	while ((line = in.readLine()) != null) 
            out.write("\n" + line);
        
    	in.close();
   		out.close();
	}

	private void findRegressionTestCases() throws Exception {
		if(new File(projectLocation + "/app/src/test").exists()) {
			List<String> output = CommandExecutorProcess.execute("find app/src/test -name *.java", projectLocation);
			regression = !output.isEmpty();
		}
		else regression = false;

		if(new File(projectLocation + "/app/src/androidTest").exists()) {
			List<String> output = CommandExecutorProcess.execute("find app/src/androidTest -name *.java", projectLocation);
			instrumentationRegression = !output.isEmpty();
		}
		else instrumentationRegression = false;
	} 

	private void findFlavors() throws IOException, InterruptedException {
		List<String> output = CommandExecutorProcess.execute("ls app/build/intermediates/classes/", projectLocation);
		flavors = new ArrayList<String>();

		for(String entry : output){
			if(!entry.equals("release") && !entry.equals("debug") && !entry.equals("test") && !entry.equals("androidTest")){
				flavors.add(entry);
				logger.info("Flavor found: "+entry);
			}
		}

		if(!flavors.isEmpty())
			flavor = flavors.get(0);
	}

	private void findTestPackage() throws FileNotFoundException, IOException {
		logger.info("Finding the name of the test package");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectLocation + "/app/build.gradle")));
		String line = null;
		testPackage = null;

		while((line = br.readLine()) != null){
			if(line.contains("testApplicationId")){
				int tempFix = line.split("\"").length;
				
				if(tempFix > 1)
					testPackage = line.split("\"")[1];
				else
					testPackage = line.split("\'")[1];
				break;
			}
		}

		if(testPackage == null)
			testPackage = mainPackage + ".test";

		br.close();
	}

	private void findMainPackage() throws Exception {
		logger.info("Finding the name of the main package");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectLocation + "/app/src/main/AndroidManifest.xml")));
		String line = null;

		while((line = br.readLine()) != null){
			if(line.contains("package")){
				int tempFix = line.split("\"").length;
				
				if(tempFix > 1)
					mainPackage = line.split("\"")[1];
				else
					mainPackage = line.split("\'")[1];
				break;
			}
		}
		br.close();
	}

	private void saveDependenciesLocally(String location) throws Exception {
		String repositoryFormat = "\n\tmaven {\n\t\turl '%s'\n\t}\n";

		List<String> m2repositories = Arrays.asList(new String[] { 
				ConfigurationProperties.getProperty("androidsdk") + "/extras/android/m2repository/"
			  , ConfigurationProperties.getProperty("androidsdk") + "/extras/google/m2repository/" });

		BufferedWriter out = new BufferedWriter(new FileWriter(location + "/build.gradle", true));

		out.write("\n\nrepositories {");
		for(String repository : m2repositories)
			out.write(String.format(repositoryFormat, repository));
		out.write("\n\tmavenLocal()\n}\n\n");
		

		BufferedReader in = new BufferedReader(new FileReader("save.gradle"));
		String line;
		while ((line = in.readLine()) != null) 
            out.write("\n" + line);

        in.close();
   		out.close();

   		CommandExecutorProcess.execute("./gradlew saveDependencies -no-daemon", location);

   		extractAAR(location + "/localrepo");
	}


	private void extractAAR(String libLocation) throws Exception {
		List<String> output = CommandExecutorProcess.execute("find " + libLocation + " -type f -name *.aar -printf %f\n");

		for(String aar : output){
			String aarFolder = aar.split(".aar")[0];
			CommandExecutorProcess.execute("mkdir " + aarFolder, libLocation);
			CommandExecutorProcess.execute("cp " + aar + " " + aarFolder, libLocation);
			CommandExecutorProcess.execute("jar xf " + aar, libLocation + "/" + aarFolder);
			CommandExecutorProcess.execute("mkdir jars", libLocation + "/" + aarFolder);
			CommandExecutorProcess.execute("mv classes.jar jars", libLocation + "/" + aarFolder);
			CommandExecutorProcess.execute("rm " + aar, libLocation + "/" + aarFolder);
		}
	}

	private void findDependencies() throws Exception {
		logger.info("Finding dependencies");
		saveDependenciesLocally(projectLocation);

		dependencies = "";
		List<String> output = CommandExecutorProcess.execute("find " + projectLocation + " -type f -name *.jar");

		for(String entry : output)
			dependencies += entry + ":";

		AndroidToolsExecutorProcess.compileProject(projectLocation);
		output = CommandExecutorProcess.execute("ls app/build/intermediates/classes/", projectLocation);

		for(String entry : output){
			if(entry.equals("debug"))
				dependencies += projectLocation + "/app/build/intermediates/classes/debug/" + ":";

			else if(!entry.equals("release")){
				dependencies += projectLocation + "/app/build/intermediates/classes/" + entry + "/debug/" + ":";
			}
		}

		dependencies += ConfigurationProperties.getProperty("androidjar");
	}

	public void setupProject() throws Exception {
		//Getting all the permissions
		CommandExecutorProcess.execute("chmod -R 777 " + getProjectName(), project.getParentFile().getAbsolutePath());

		//Copying local.properties
		CommandExecutorProcess.execute("cp local.properties " + project.getAbsolutePath());

		getProjectInformation();

		//Creating the default bin dir
		File defaultBin = new File("workDir/AstorWorker-" + getProjectName() + "/default/bin");
		ConfigurationProperties.setProperty("defaultbin", defaultBin.getAbsolutePath());
		defaultBin.mkdirs();

		if(flavor == null){
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/release/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/test/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/androidTest/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
		}
		else{
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/" + flavor + "/release/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/test/" + flavor + "/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/build/intermediates/classes/androidTest/" + flavor + "/debug/. workDir/AstorWorker-" + getProjectName() + "/default/bin");
		}

		//Creating the default source dir
		File defaultSrc = new File("workDir/AstorWorker-" + getProjectName() + "/default/src");
		ConfigurationProperties.setProperty("defaultsrc", defaultSrc.getAbsolutePath());
		defaultSrc.mkdirs();

		CommandExecutorProcess.execute("cp -a " + project.getAbsolutePath() + "/app/src/main/java/. workDir/AstorWorker-" + getProjectName() + "/default/src");
	}

	public String getUnitTestTask(){
		if(flavor != null)
			return "test" + flavor.substring(0, 1).toUpperCase() + flavor.substring(1) + "DebugUnitTest";
		else
			return "testDebugUnitTest";
	}

	public String getInstrumentationTestTask(){
		if(flavor != null)
			return "connected" + flavor.substring(0, 1).toUpperCase() + flavor.substring(1) + "DebugAndroidTest";
		else
			return "connectedDebugAndroidTest";
	}

	public void setFailingInstrumentationTests(String test){
		instrumentalFailing = test;
	}

	public void setFailingUnitTests(String test){
		failing = test;
	}

	public void saveBuildGradle() throws IOException, InterruptedException {
		CommandExecutorProcess.execute("cp " + projectLocation + "/app/build.gradle workDir/AstorWorker-" + projectName + "/");
	}

	public void restoreBuildGradle() throws IOException, InterruptedException {
		CommandExecutorProcess.execute("cp workDir/AstorWorker-" + projectName + "/build.gradle " + projectLocation + "/app/");
	}

	public String getProjectName(){
		return projectName;
	}

	public String getProjectLocation(){
		return projectLocation;
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

	public String getLocation(){
		return project.getAbsolutePath();
	}

	public String getParentPath(){
		return project.getParentFile().getAbsolutePath();
	}

	public boolean unitRegressionTestCasesExist(){
		return regression;
	}

	public boolean instrumentationRegressionTestCasesExist(){
		return instrumentationRegression;
	}
}
