package br.ufg.inf.astorworker.entities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import br.ufg.inf.astorworker.executors.CommandExecutorProcess;
import br.ufg.inf.astorworker.executors.AndroidToolsExecutorProcess;
import fr.inria.astor.core.setup.ConfigurationProperties;
import br.inf.ufg.astorworker.utils.FileSystemUtils;

public class AndroidProject {
	private String mainFolder;
	private String mainPackage;
	private String testPackage;
	private String flavor;
	private File projectDirectory;
	private String projectName;
	private String dependencies;
	private String projectAbsolutePath;
	private String unitTestTask;
	private String instrumentationTestTask;
	private String buildVersion;
	private String compileVersion;
	private String failingInstrumentationTestCases;
	private String failingUnitTestCases;
	private boolean unitRegressionTestCasesExist;
	private boolean instrumentationRegressionTestCasesExist;
	private Logger logger = Logger.getLogger(AndroidProject.class);

	private Pattern unitTaskPattern = Pattern.compile("\\s*(test)([a-zA-Z0-9]+)(unittest)\\s-\\s(.*?)\\s*");
	private Pattern instrumentationTaskPattern = Pattern.compile("\\s*(connected)(androidtest[a-zA-Z0-9]+|[a-zA-Z0-9]+androidtest)\\s-\\s(.*?)\\s*");

	public AndroidProject(File projectDirectory) {
		this.projectDirectory = projectDirectory;
	}
	
	public void setup() throws Exception {
		logger.info("Getting project information");

		FileSystemUtils.getPermissionsForDirectory(projectDirectory);
		projectAbsolutePath = projectDirectory.getAbsolutePath();
		projectName = projectDirectory.getName();
		mainFolder = findMainFolder();
		mainPackage = findMainPackage();
		testPackage = findTestPackage();

		//Uninstalling old app version
		AndroidToolsExecutorProcess.uninstallPackage(mainPackage);
		AndroidToolsExecutorProcess.uninstallPackage(testPackage);


		buildVersion = findBuildVersion();
		compileVersion = findCompileVersion();
		unitTestTask = findTask(unitTaskPattern);
		instrumentationTestTask = findTask(instrumentationTaskPattern);
		activateTestLogging();
		setupWorkingDirectory();

		findDependencies();	
		flavor = findFlavor();
		findRegressionTestCases();
	}

	private void findRegressionTestCases() throws Exception {
		if(new File(projectAbsolutePath + "/app/src/test").exists()) {
			List<String> output = CommandExecutorProcess.execute("find app/src/test -name *.java", projectAbsolutePath);
			unitRegressionTestCasesExist = !output.isEmpty();
		}
		else unitRegressionTestCasesExist = false;

		if(new File(projectAbsolutePath + "/app/src/androidTest").exists()) {
			List<String> output = CommandExecutorProcess.execute("find app/src/androidTest -name *.java", projectAbsolutePath);
			instrumentationRegressionTestCasesExist = !output.isEmpty();
		}
		else instrumentationRegressionTestCasesExist = false;
	} 

	private String findFlavor() throws IOException, InterruptedException {
		List<String> output = CommandExecutorProcess.execute("ls app/build/intermediates/classes/", projectAbsolutePath);
		ArrayList<String> flavors = new ArrayList<String>();

		for(String entry : output){
			if(!entry.equals("release") && !entry.equals("debug") && !entry.equals("test") && !entry.equals("androidTest")){
				flavors.add(entry);
				logger.info("Flavor found: "+entry);
			}
		}

		if(!flavors.isEmpty())
			return flavors.get(0);

		return null;
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

	private void saveDependenciesLocally() throws Exception {
		String repositoryFormat = "\n\tmaven {\n\t\turl '%s'\n\t}\n";

		List<String> m2repositories = Arrays.asList(new String[] { 
				ConfigurationProperties.getProperty("androidsdk") + "/extras/android/m2repository/"
			  , ConfigurationProperties.getProperty("androidsdk") + "/extras/google/m2repository/" });

		BufferedWriter out = new BufferedWriter(new FileWriter(projectAbsolutePath + "/app/build.gradle", true));

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

   		CommandExecutorProcess.execute("./gradlew saveDependencies -no-daemon", projectAbsolutePath);

   		extractAAR(projectAbsolutePath + "/app/localrepo");
	}

	private void findDependencies() throws Exception {
		logger.info("Finding dependencies");
		saveDependenciesLocally();

		dependencies = "";
		List<String> output = CommandExecutorProcess.execute("find " + projectAbsolutePath + " -type f -name *.jar");

		for(String entry : output)
			dependencies += entry + ":";

		AndroidToolsExecutorProcess.compileProject(projectAbsolutePath);
		output = CommandExecutorProcess.execute("ls app/build/intermediates/classes/", projectAbsolutePath);

		for(String entry : output){
			if(entry.equals("debug"))
				dependencies += projectAbsolutePath + "/app/build/intermediates/classes/debug/" + ":";

			else if(!entry.equals("release")){
				dependencies += projectAbsolutePath + "/app/build/intermediates/classes/" + entry + "/debug/" + ":";
			}
		}

		dependencies += ConfigurationProperties.getProperty("androidjar");
	}



	private String findBuildVersion() throws Exception {
		Pattern buildVersionPattern = Pattern.compile("\\s*(buildtoolsversion)\\s*(\'|\")([ .0-9]+)(\'|\")\\s*");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + File.separator + mainFolder + File.separator + "build.gradle")));

		String line = null;
		String buildToolsVersion = null ;

		while((line = br.readLine()) != null){
			Matcher buildVersionMatcher = buildVersionPattern.matcher(line.toLowerCase());

			if (buildVersionMatcher.matches()) {
				buildToolsVersion = buildVersionMatcher.group(3);
				break;
			}
		}
		
		br.close();
		return buildToolsVersion;
	}

	private String findCompileVersion() throws Exception {
		Pattern compileVersionPattern = Pattern.compile("\\s*(compilesdkversion)\\s*([0-9]+)\\s*");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + File.separator + mainFolder + File.separator + "build.gradle")));

		String line = null;
		String compileVersion = null ;

		while((line = br.readLine()) != null){
			Matcher compileVersionMatcher = compileVersionPattern.matcher(line.toLowerCase());

			if (compileVersionMatcher.matches()) {
				compileVersion = compileVersionMatcher.group(2);
				break;
			}
		}
		
		br.close();
		return compileVersion;
	}

	

	private String findTask(Pattern p) throws Exception {
		List<String> output = AndroidToolsExecutorProcess.runGradleTask(projectAbsolutePath, "tasks");

		for(String line : output) {
			Matcher m = p.matcher(line.toLowerCase());

			if (m.matches()) 
				return line.split("-")[0].trim();
		}
		return null;
	}

	private String findTestPackage() throws Exception {
		Pattern testPackagePattern = Pattern.compile("\\s*(testApplicationId)\\s*(\'|\")([ .a-zA-Z0-9]+)(\'|\")\\s*");

		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + File.separator + mainFolder + File.separator + "build.gradle")));
		String line = null;
		String testPackage = null;


		while((line = br.readLine()) != null){
			Matcher testPackageMatcher = testPackagePattern.matcher(line);

			if (testPackageMatcher.matches()) {
				testPackage = testPackageMatcher.group(3);
				break;
			}
		}

		if(testPackage == null)
			testPackage = this.mainPackage + ".test";

		br.close();
		return testPackage;
	}

	private String findMainPackage() throws Exception {
		Pattern packagePattern = Pattern.compile("\\s*(package)\\s*(=)\\s*(\'|\")([ .a-zA-Z0-9]+)(\'|\")\\s*(.*?)\\s*");

		BufferedReader br = new BufferedReader(new FileReader(
				new File(projectAbsolutePath + File.separator + mainFolder + File.separator
					+ "src" + File.separator + "main" + File.separator + "AndroidManifest.xml")));

		String line = null;
		String mainPackage = null ;

		while((line = br.readLine()) != null){
			Matcher packageMatcher = packagePattern.matcher(line);

			if (packageMatcher.matches()) {
				mainPackage = packageMatcher.group(4);
				break;
			}
		}
		
		br.close();
		return mainPackage;
	}

	private String findMainFolder() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(projectAbsolutePath + File.separator + "settings.gradle"));
		Pattern p = Pattern.compile("include\\s*\\'\\:([a-zA-Z0-9]+)\\'\\s*\\,?+(.*?)\\s*");

		String line;
		while((line = br.readLine()) != null){
			Matcher m = p.matcher(line);
			if(m.matches()){
				br.close();
				return m.group(1);
			}
		}

		return "app";
	}

	public boolean instrumentationTestsExist() throws Exception {
		if(!(new File(projectAbsolutePath + File.separator + mainFolder + File.separator + "src" + File.separator + "androidTest").exists()))
			return false;

		List<String> instrumentationTests = FileSystemUtils.findFilesWithExtension(
				new File(projectAbsolutePath + File.separator + mainFolder 
					+ File.separator + "src" + File.separator + "androidTest"), "java");

		return !instrumentationTests.isEmpty();
	}	

	public boolean unitTestsExist() throws Exception {
		if(!(new File(projectAbsolutePath + File.separator + mainFolder + File.separator + "src" + File.separator + "test").exists()))
			return false;

		List<String> unitTests = FileSystemUtils.findFilesWithExtension(
				new File(projectAbsolutePath + File.separator + mainFolder 
					+ File.separator + "src" + File.separator + "test"), "java");

		return !unitTests.isEmpty();
	}

	private void activateTestLogging() throws Exception {
   		BufferedWriter out = new BufferedWriter(new FileWriter(projectAbsolutePath + File.separator + mainFolder + File.separator + "build.gradle", true));
    	BufferedReader in = new BufferedReader(new FileReader("test.gradle"));
    	String line;

       	while ((line = in.readLine()) != null) 
            out.write("\n" + line);
        
    	in.close();
   		out.close();
	}

	private void setupWorkingDirectory() throws Exception {
		//Creating the default bin dir
		File defaultBin = new File("workDir/AstorWorker-" + projectName + "/default/bin");
		ConfigurationProperties.setProperty("defaultbin", defaultBin.getAbsolutePath());
		defaultBin.mkdirs();

		if(flavor == null){
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/release/. workDir/AstorWorker-" + projectName + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/test/debug/. workDir/AstorWorker-" + projectName + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/androidTest/debug/. workDir/AstorWorker-" + projectName + "/default/bin");
		}
		else{
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/" + flavor + "/release/. workDir/AstorWorker-" + projectName + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/test/" + flavor + "/debug/. workDir/AstorWorker-" + projectName + "/default/bin");
			CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/build/intermediates/classes/androidTest/" + flavor + "/debug/. workDir/AstorWorker-" + projectName + "/default/bin");
		}

		//Creating the default source dir
		File defaultSrc = new File("workDir/AstorWorker-" + projectName + "/default/src");
		ConfigurationProperties.setProperty("defaultsrc", defaultSrc.getAbsolutePath());
		defaultSrc.mkdirs();

		CommandExecutorProcess.execute("cp -a " + projectAbsolutePath + "/app/src/main/java/. workDir/AstorWorker-" + projectName + "/default/src");
	}

	public void setFailingInstrumentationTestCases(String tests){
		failingInstrumentationTestCases = tests;
	}

	public void setFailingUnitTestCases(String tests){
		failingUnitTestCases = tests;
	}

	public void saveBuildGradle() throws IOException, InterruptedException {
		CommandExecutorProcess.execute("cp " + projectAbsolutePath + "/app/build.gradle workDir/AstorWorker-" + projectName + "/");
	}

	public void restoreBuildGradle() throws IOException, InterruptedException {
		CommandExecutorProcess.execute("cp workDir/AstorWorker-" + projectName + "/build.gradle " + projectAbsolutePath + "/app/");
	}

	public String getProjectName(){
		return projectName;
	}

	public String getDependencies(){
		return dependencies;
	}

	public String getFailingUnitTestCases(){
		return failingUnitTestCases;
	}

	public String getFailingInstrumentationTestCases(){
		return failingInstrumentationTestCases;
	}

	public String getLocation() {
		return projectAbsolutePath;
	}

	public String getMainPackage() {
		return mainPackage;
	}

	public String getTestPackage() {
		return testPackage;
	}

	public String getMainFolder() {
		return mainFolder;
	}

	public String getUnitTestTask() {
		return unitTestTask;
	}

	public String getInstrumentationTestTask() {
		return instrumentationTestTask;
	}

	public String getCompileVersion() {
		return compileVersion;
	}

	public String getBuildVersion() {
		return buildVersion;
	}

	public boolean unitRegressionTestCasesExist(){
		return unitRegressionTestCasesExist;
	}

	public boolean instrumentationRegressionTestCasesExist(){
		return instrumentationRegressionTestCasesExist;
	}
}