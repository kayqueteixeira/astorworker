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

	public static void compileProject(String projectLocation) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Compiling project");
		List<String> output = CommandExecutorProcess.execute("./gradlew build -x test", projectLocation);

		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to compile project at "+projectLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not compile the project");
		}
		
		logger.info("Successfully compiled the project");
	}	
	
	public static List<String> runGradleTask(String projectLocation, String gradleTask) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Running gradle task \"" + gradleTask + "\"");
		List<String> output = CommandExecutorProcess.execute("./gradlew -a " + gradleTask, projectLocation);

		// TODO: find a way the check successfulness
		return output;
	}

	public static void uninstallAPK(String appPackage) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute("./adb uninstall "+appPackage, ConfigurationProperties.getProperty("platformtools"));

		// Emulator bug workaround
		if(searchForString(output, "Can't find service: package") || searchForString(output, "error: device offline")){
			logger.info("The android emulator had a problem. Restarting adb...");
			restartADB();
			uninstallAPK(appPackage);
			return;
		}

		// Checking if the execution was successful
		boolean success = searchForString(output, "Success");

		if(!success && !searchForString(output, "DELETE_FAILED_INTERNAL_ERROR")){
			logger.error("Failed to uninstall "+appPackage+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not uninstall "+appPackage);
		}
		
		logger.info("Successfully uninstalled "+appPackage);
	}

	public static List<String> runUnitTests(String projectLocation, String task, List<String> classesToExecute) throws InterruptedException, IOException, IllegalStateException {
		String testsToRun = String.join(",",classesToExecute);
		logger.info("Running unit tests: " + testsToRun);

		String command = "./gradlew --continue " + task + " ";

		for(String unitTest : classesToExecute)
			command += "--tests=" + unitTest.replaceAll("#", "\\.") + " ";
		
		List<String> output = CommandExecutorProcess.execute(command, projectLocation);
		return output;
	}

	public static List<String> runUnitTests(String projectLocation, String task) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute("./gradlew --continue " + task, projectLocation);
		return output;
	}


	public static List<String> runInstrumentationTests(String projectLocation, String task, List<String> classesToExecute) throws InterruptedException, IOException, IllegalStateException {
		String testsToRun = String.join(",",classesToExecute);
		logger.info("Running instrumentation tests: " + testsToRun);

		List<String> output = CommandExecutorProcess.execute("./gradlew --continue -Pandroid.testInstrumentationRunnerArguments.class=" + testsToRun + " -i " + task, projectLocation);

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

		List<String> output = CommandExecutorProcess.execute("./gradlew -i --continue " + task, projectLocation);

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
		CommandExecutorProcess.execute("./adb kill-server", ConfigurationProperties.getProperty("platformtools"));
		CommandExecutorProcess.execute("./adb start-server", ConfigurationProperties.getProperty("platformtools"));
		logger.info("Adb restarted!");
	}



}