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
	private static AndroidProject instance = null;
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
	private List<String> failingInstrumentationTestCases;
	private List<String> failingUnitTestCases;
	private boolean unitRegressionTestCasesExist;
	private boolean instrumentationRegressionTestCasesExist;
	private Logger logger = Logger.getLogger(AndroidProject.class);

	private Pattern unitTaskPattern = Pattern.compile("\\s*(test)([a-zA-Z0-9]+)(unittest)\\s-\\s(.*?)\\s*");
	private Pattern instrumentationTaskPattern = Pattern.compile("\\s*(connected)(androidtest[a-zA-Z0-9]+|[a-zA-Z0-9]+androidtest)\\s-\\s(.*?)\\s*");

	private AndroidProject() {}

	public static AndroidProject getInstance() {
		if(instance == null)
			instance = new AndroidProject();
		
		return instance;
	}
	
	public void setup(File projectDirectory) throws Exception {
		this.projectDirectory = projectDirectory;
		logger.info("Getting project information");

		if(!AndroidToolsExecutorProcess.getOperatingSystem().equals("Windows"))
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

		findDependencies();	
		flavor = findFlavor();
		findRegressionTestCases();
		setupWorkingDirectory();
	}

	private void findRegressionTestCases() throws Exception {
		if(new File(projectAbsolutePath + "/" + mainFolder + "/src/test").exists()) {
			List<String> output = FileSystemUtils.findFilesWithExtension(new File(projectAbsolutePath + "/" + mainFolder + "/src/test"), "java", false);
			unitRegressionTestCasesExist = !output.isEmpty();
		}
		else unitRegressionTestCasesExist = false;

		if(new File(projectAbsolutePath + "/" + mainFolder + "/src/androidTest").exists()) {
			List<String> output = FileSystemUtils.findFilesWithExtension(new File(projectAbsolutePath + "/" + mainFolder + "/src/androidTest"), "java", false);
			instrumentationRegressionTestCasesExist = !output.isEmpty();
		}
		else instrumentationRegressionTestCasesExist = false;
	} 

	private String findFlavor() throws Exception {
		List<String> output = FileSystemUtils.listContentsDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes"));
		ArrayList<String> flavors = new ArrayList<String>();

		for(String entry : output){
			if(!entry.equals("release") && !entry.equals("debug") && !entry.equals("test") && !entry.equals("androidTest")){
				flavors.add(entry);
				logger.info("Flavor found: " + entry);
			}
		}

		if(!flavors.isEmpty())
			return flavors.get(0);

		return null;
	}

	private void extractAAR(String libLocation) throws Exception {
		List<String> output = FileSystemUtils.findFilesWithExtension(new File(libLocation), "aar", false);

		for(String aar : output){
			String aarFolder = aar.split(".aar")[0];
			File aarDirectory = new File(FileSystemUtils.fixPath(libLocation + "/" + aarFolder));
			FileUtils.moveFileToDirectory(new File(FileSystemUtils.fixPath(libLocation + "/" + aar)), aarDirectory, true);
			CommandExecutorProcess.execute("jar xf " + aar, FileSystemUtils.fixPath(libLocation + "/" + aarFolder));
			File jarsDirectory = new File(FileSystemUtils.fixPath(libLocation + "/" + aarFolder + "/" + "jars"));
			FileUtils.moveFileToDirectory(new File(FileSystemUtils.fixPath(libLocation + "/" + aarFolder + "/classes.jar")), jarsDirectory, true);
			FileUtils.forceDelete(new File(FileSystemUtils.fixPath( libLocation + "/" + aarFolder + "/" + aar)));
		}
	}

	private void saveDependenciesLocally() throws Exception {
		String repositoryFormat = "\n\tmaven {\n\t\turl '%s'\n\t}\n";

		List<String> m2repositories = Arrays.asList(new String[] { 
				ConfigurationProperties.getProperty("androidsdk") + FileSystemUtils.fixPath("/extras/android/m2repository/")
			  , ConfigurationProperties.getProperty("androidsdk") + FileSystemUtils.fixPath("/extras/google/m2repository/") });

		BufferedWriter out = new BufferedWriter(new FileWriter(new File(projectAbsolutePath + "/" + mainFolder + "/build.gradle"), true));

		out.write("\n\nrepositories {");
		for(String repository : m2repositories)
			out.write(String.format(repositoryFormat, repository.replace("\\", "\\\\")));
		out.write("\n\tmavenLocal()\n}\n\n");
		

		BufferedReader in = new BufferedReader(new FileReader("save.gradle"));
		String line;
		while ((line = in.readLine()) != null) 
            out.write("\n" + line);

        in.close();
   		out.close();

   		AndroidToolsExecutorProcess.runGradleTask(projectAbsolutePath, "saveDependencies", true);

   		extractAAR(projectAbsolutePath + "/" + mainFolder + "/localrepo");
	}

	private void findDependencies() throws Exception {
		logger.info("Finding dependencies");
		saveDependenciesLocally();

		String dependencies = "";
		List<String> output = FileSystemUtils.findFilesWithExtension(new File(projectAbsolutePath), "jar", true);

		for(String entry : output)
			dependencies += entry + System.getProperty("path.separator");

		AndroidToolsExecutorProcess.compileProject(projectAbsolutePath);

		output = FileSystemUtils.listContentsDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes/"));

		for(String entry : output){
			if(entry.equals("debug"))
				dependencies += FileSystemUtils.fixPath(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes/debug/") + System.getProperty("path.separator");

			else if(!entry.equals("release")){
				dependencies += FileSystemUtils.fixPath(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes/" + entry + "/debug/") + System.getProperty("path.separator");
			}
		}

		dependencies += ConfigurationProperties.getProperty("androidjar");
	}



	private String findBuildVersion() throws Exception {
		Pattern buildVersionPattern = Pattern.compile("\\s*(buildtoolsversion)\\s*(\'|\")([ .0-9]+)(\'|\")\\s*");
		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + "/" + mainFolder + "/build.gradle")));

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
		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + "/" + mainFolder + "/build.gradle")));

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
		List<String> output = AndroidToolsExecutorProcess.runGradleTask(projectAbsolutePath, "tasks", false);

		for(String line : output) {
			Matcher m = p.matcher(line.toLowerCase());

			if (m.matches()) 
				return line.split("-")[0].trim();
		}
		return null;
	}

	private String findTestPackage() throws Exception {
		Pattern testPackagePattern = Pattern.compile("\\s*(testApplicationId)\\s*(\'|\")([ .a-zA-Z0-9]+)(\'|\")\\s*");

		BufferedReader br = new BufferedReader(new FileReader(new File(projectAbsolutePath + "/" + mainFolder + "/build.gradle")));
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
				new File(projectAbsolutePath + "/" + mainFolder + "/src/main/AndroidManifest.xml")));

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
		BufferedReader br = new BufferedReader(new FileReader(projectAbsolutePath + "/settings.gradle"));
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
		if(!(new File(projectAbsolutePath + "/" + mainFolder + "/src/androidTest").exists()))
			return false;

		List<String> instrumentationTests = FileSystemUtils.findFilesWithExtension(
				new File(projectAbsolutePath + "/" + mainFolder + "/src/androidTest"), "java", true);

		return !instrumentationTests.isEmpty();
	}	

	public boolean unitTestsExist() throws Exception {
		if(!(new File(projectAbsolutePath + "/" + mainFolder + "/src/test").exists()))
			return false;

		List<String> unitTests = FileSystemUtils.findFilesWithExtension(
				new File(projectAbsolutePath + "/" + mainFolder + "/src/test"), "java", true);

		return !unitTests.isEmpty();
	}

	private void activateTestLogging() throws Exception {
   		BufferedWriter out = new BufferedWriter(new FileWriter(projectAbsolutePath + "/" + mainFolder + "/build.gradle", true));
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

		if(flavor == null)
			FileUtils.copyDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes/release"), defaultBin);
		
		else
			FileUtils.copyDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/build/intermediates/classes/" + flavor + "/release"), defaultBin);
		
		//Creating the default source dir
		File defaultSrc = new File("workDir/AstorWorker-" + projectName + "/default/src");
		ConfigurationProperties.setProperty("defaultsrc", defaultSrc.getAbsolutePath());
		defaultSrc.mkdirs();

		FileUtils.copyDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/src/main/java"), defaultSrc);
	}

	private void restoreOriginalSource() throws Exception {
		FileUtils.copyDirectory(new File(ConfigurationProperties.getProperty("defaultsrc")), 
								new File((projectAbsolutePath + "/" + mainFolder + "/src/main/java/")));
	}

	public void applyVariant(File variant) throws Exception {
		restoreOriginalSource();
		FileUtils.copyDirectory(variant, new File(projectAbsolutePath + "/" + mainFolder + "/src/main/java/"));
	}

	public void activateCodeCoverage() throws IOException {
   		BufferedWriter out = new BufferedWriter(new FileWriter(projectAbsolutePath + "/" + mainFolder + "/build.gradle", true));
    	BufferedReader in = new BufferedReader(new FileReader("coverage.gradle"));
    	String line;

       	while ((line = in.readLine()) != null) 
            out.write("\n" + line);
        
    	in.close();
   		out.close();
	}


	public List<String> runTask(String task, boolean compileDependencies) throws Exception {
		return AndroidToolsExecutorProcess.runGradleTask(projectAbsolutePath, task, compileDependencies);
	}

	public List<String> runFailingInstrumentationTests() throws Exception {
		return AndroidToolsExecutorProcess.runInstrumentationTests(projectAbsolutePath, instrumentationTestTask, failingInstrumentationTestCases);
	}

	public List<String> runAllInstrumentationTests() throws Exception {
		return AndroidToolsExecutorProcess.runInstrumentationTests(projectAbsolutePath, instrumentationTestTask);
	}	

	public List<String> runFailingUnitTests() throws Exception {
		return AndroidToolsExecutorProcess.runUnitTests(projectAbsolutePath, unitTestTask, failingUnitTestCases);
	}

	public List<String> runAllUnitTests() throws Exception {
		return AndroidToolsExecutorProcess.runUnitTests(projectAbsolutePath, unitTestTask);
	}

	public void setFailingInstrumentationTestCases(String tests) {
		failingInstrumentationTestCases = Arrays.asList(tests.split(":"));
	}

	public void setFailingUnitTestCases(String tests) {
		failingUnitTestCases = Arrays.asList(tests.split(":"));
	}

	public void saveBuildGradle() throws IOException, InterruptedException {
		FileUtils.copyFileToDirectory(new File(projectAbsolutePath + "/" + mainFolder + "/build.gradle"), new File("workDir/AstorWorker-" + projectName));
	}

	public void restoreBuildGradle() throws IOException, InterruptedException {
		FileUtils.copyFileToDirectory(new File("workDir/AstorWorker-" + projectName + "/build.gradle"), new File(projectAbsolutePath + "/" + mainFolder + "/"));
	}

	public String getProjectName(){
		return projectName;
	}

	public String getDependencies(){
		return dependencies;
	}

	public List<String> getFailingUnitTestCases(){
		return failingUnitTestCases;
	}

	public List<String> getFailingInstrumentationTestCases(){
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