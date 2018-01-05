package br.ufg.inf.astorworker.executors;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.io.FileNotFoundException;

import org.apache.log4j.Logger;

import fr.inria.astor.core.setup.ConfigurationProperties;


/**
 * 
 * @author Kayque de Sousa Teixeira
 *
 */
public class AndroidToolsExecutorProcess {
	private static Logger logger = Logger.getLogger(AndroidToolsExecutorProcess.class);
	private static String GRADLE;
	private static String ADB;
	private static String ANDROID_HOME;

	public static void setup(String androidHome) throws Exception {
		ANDROID_HOME = androidHome;

		switch(getOperatingSystem()){
			case "Windows":
				GRADLE = "cmd /c gradlew.bat";
				ADB = "adb";
				break;

			case "Unix":
			case "MacOS":
				GRADLE = "./gradlew";
				ADB = "./adb";
				break;
		}
	}

	public static void compileProject(String projectLocation) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Compiling project");
		List<String> output = CommandExecutorProcess.execute(GRADLE + " build -x test", projectLocation);

		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to compile project at " + projectLocation + ", output:\n\t" + String.join("\n", output));
			throw new IllegalStateException("Could not compile the project");
		}
		
		logger.info("Successfully compiled the project");
	}	

	public static void compileTests(String projectLocation) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Compiling tests");
		List<String> output = CommandExecutorProcess.execute(GRADLE + " assembleAndroidTest", projectLocation);

		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to compile tests at " + projectLocation + ", output:\n\t" + String.join("\n", output));
			throw new IllegalStateException("Could not compile tests");
		}
		
		logger.info("Successfully compiled the tests");
	}	
	
	public static List<String> runGradleTask(String projectLocation, String gradleTask, boolean compileDependencies) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Running gradle task \"" + gradleTask + "\"");
		
		List<String> output;
		if(compileDependencies)
			output = CommandExecutorProcess.execute(GRADLE + " " + gradleTask, projectLocation);
		else
			output = CommandExecutorProcess.execute(GRADLE + " -a " + gradleTask, projectLocation);

		// TODO: find a way the check successfulness
		return output;
	}

	public static void uninstallPackage(String appPackage) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute(ADB + " uninstall "+appPackage, ConfigurationProperties.getProperty("platformtools"));

		// Emulator bug workaround
		if(searchForString(output, "Can't find service: package") || searchForString(output, "error: device offline")){
			logger.info("The android emulator had a problem. Restarting adb...");
			restartADB();
			uninstallPackage(appPackage);
			return;
		}

		// Checking if the execution was successful
		boolean success = searchForString(output, "Success");

		if(!success && !(searchForString(output, "DELETE_FAILED_INTERNAL_ERROR") || searchForString(output, "Unknown package"))){
			logger.error("Failed to uninstall "+appPackage+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not uninstall "+appPackage);
		}
		
		logger.info("Successfully uninstalled "+appPackage);
	}

	public static List<String> runUnitTests(String projectLocation, String task, List<String> classesToExecute) throws InterruptedException, IOException, IllegalStateException {
		String testsToRun = String.join(",",classesToExecute);
		logger.info("Running unit tests: " + testsToRun);

		String command = GRADLE + " --continue " + task + " ";

		for(String unitTest : classesToExecute)
			command += "--tests=" + unitTest.replaceAll("#", "\\.") + " ";
		
		List<String> output = CommandExecutorProcess.execute(command, projectLocation);
		return output;
	}

	public static List<String> runUnitTests(String projectLocation, String task) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute(GRADLE + " --continue " + task, projectLocation);
		return output;
	}


	public static List<String> runInstrumentationTests(String projectLocation, String task, List<String> classesToExecute) throws InterruptedException, IOException, IllegalStateException {
		String testsToRun = String.join(",",classesToExecute);
		logger.info("Running instrumentation tests: " + testsToRun);

		List<String> output = CommandExecutorProcess.execute(GRADLE + " --continue -Pandroid.testInstrumentationRunnerArguments.class=" + testsToRun + " -i " + task, projectLocation);

		// Checking if the execution was successful
		boolean errorOccurred = searchForString(output, "INSTRUMENTATION_FAILED");

		if(errorOccurred){
			logger.error("Failed to run instrumentation tests, output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not run instrumentation tests");
		}
		
		// Emulator bug workaround
		if(searchForString(output, "Can't find service: package") || searchForString(output, "error: device offline")){
			logger.info("The android emulator had a problem. Restarting adb...");
			restartADB();
			return runInstrumentationTests(projectLocation, task, classesToExecute);
		}

		logger.info("Status: SUCCESSFUL");
		return output;
	}

	
	public static List<String> runInstrumentationTests(String projectLocation, String task) throws InterruptedException, IOException, IllegalStateException {
		logger.info("Running all instrumentation tests");

		List<String> output = CommandExecutorProcess.execute(GRADLE + " -i --continue " + task, projectLocation);

		// Checking if the execution was successful
		boolean errorOccurred = searchForString(output, "INSTRUMENTATION_FAILED");

		if(errorOccurred){
			logger.error("Failed to run instrumentation tests, output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not run instrumentation tests");
		}
		
		// Emulator bug workaround
		if(searchForString(output, "Can't find service: package") || searchForString(output, "error: device offline")){
			logger.info("The android emulator had a problem. Restarting adb...");
			restartADB();
			return runInstrumentationTests(projectLocation, task);
		}

		logger.info("Status: SUCCESSFUL");
		return output;
	}

	private static boolean searchForString(List<String> outputFromCommand, String str){
		for(String entry : outputFromCommand){
			if(entry.contains(str))
				return true;
		}

		return false;
	}

	private static void restartADB() throws IOException, InterruptedException {
		CommandExecutorProcess.execute(ADB + " kill-server", ConfigurationProperties.getProperty("platformtools"));
		CommandExecutorProcess.execute(ADB + " start-server", ConfigurationProperties.getProperty("platformtools"));
		logger.info("Adb restarted!");
	}


	public static String getOperatingSystem() {
		String operatingSystem = System.getProperty("os.name").toLowerCase();

		if(operatingSystem.contains("win")) return "Windows";
		if(operatingSystem.contains("nux") ||
			operatingSystem.contains("aix") ||
			operatingSystem.contains("nix")) return "Unix";

		if(operatingSystem.contains("mac")) return "MacOS";

		return null;
	}
}
