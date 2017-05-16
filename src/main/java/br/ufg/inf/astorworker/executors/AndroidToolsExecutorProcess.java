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

	public static void compileProject(String projectLocation, int waitTime) throws InterruptedException, IOException, IllegalStateException  {
		logger.info("Compiling project");
		List<String> output = CommandExecutorProcess.execute("./gradlew build -x test", projectLocation);

		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to compile project at "+projectLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not compile the project");
		}
		
		logger.info("Status: BUILD SUCCESSFUL");
	}


	public static void generateAPK(String projectLocation, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		logger.info("Generating the application's apk");
		logger.info("Executing \"./gradlew assembleRelease on\" "+projectLocation);

		List<String> output = CommandExecutorProcess.execute("./gradlew -a assembleRelease", projectLocation);

		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to generate apk on "+projectLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not generate apk");
		}
		
		logger.info("Status: BUILD SUCCESSFUL");
	}

	public static void generateTestAPK(String projectLocation, int waitTime) throws IOException, InterruptedException, IllegalStateException {
		logger.info("Generating the test apk");
		logger.info("Executing \"./gradlew -a assembleAndroidTest\" on "+projectLocation);
		List<String> output = CommandExecutorProcess.execute("./gradlew assembleAndroidTest", projectLocation);
		
		// Checking if the execution was successful
		boolean success = searchForString(output, "BUILD SUCCESSFUL");

		if(!success){
			logger.error("Failed to generate test apk on "+projectLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not generate test apk");
		}
		
		logger.info("Status: BUILD SUCCESSFUL");
	}

	public static void signAPK(String apkLocation, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		logger.info("Signing apk "+apkLocation);
		List<String> output = CommandExecutorProcess.execute("./apksigner sign -v --ks "+ConfigurationProperties.getProperty("key")+" --ks-pass pass:"+ConfigurationProperties.getProperty("keypassword")+" "+apkLocation, ConfigurationProperties.getProperty("buildtools"));
		
		// Checking if the execution was successful
		boolean success = searchForString(output, "Signed");

		if(!success){
			logger.error("Failed to sign apk "+apkLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not sign apk");
		}
		
		logger.info("Status: SUCCESSFUL");
	}

	public static void installAPK(String apkLocation, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute("./adb install "+apkLocation, ConfigurationProperties.getProperty("platformtools"));

		// Checking if the execution was successful
		boolean success = searchForString(output, "Success");

		if(!success && !searchForString(output, "INSTALL_FAILED_ALREADY_EXISTS")){
			if(searchForString(output, "Can't find service: package")){
				restartADB();
				installAPK(apkLocation, waitTime);
			}

			logger.error("Failed to install apk "+apkLocation+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not install apk");
		}
		
		logger.info("Status: SUCCESSFUL");
	}	


	public static void uninstallAPK(String appPackage, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		List<String> output = CommandExecutorProcess.execute("./adb uninstall "+appPackage, ConfigurationProperties.getProperty("platformtools"));

		// Checking if the execution was successful
		boolean success = searchForString(output, "Success");

		if(!success && !searchForString(output, "Exception")){
			if(searchForString(output, "Can't find service: package")){
				restartADB();
				uninstallAPK(appPackage, waitTime);
			}
			logger.error("Failed to uninstall "+appPackage+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not uninstall apk");
		}
		
		logger.info("Status: SUCCESSFUL");
	}

	public static List<String> runInstrumentationTests(String appPackage, List<String> classesToExecute, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		String testsToRun = String.join(",",classesToExecute);
		logger.info("Running instrumentation tests: "+testsToRun);

		List<String> output = CommandExecutorProcess.execute("./adb shell am instrument -w -e class "+testsToRun+" "+appPackage+".test/android.support.test.runner.AndroidJUnitRunner", ConfigurationProperties.getProperty("platformtools"));

		// Checking if the execution was successful
		boolean errorOccurred = searchForString(output, "INSTRUMENTATION_FAILED");

		if(errorOccurred){
			logger.error("Failed to run instrumentation tests "+testsToRun+" on package "+appPackage+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not run instrumentation tests");
		}
		
		if(searchForString(output, "Can't find service: package")){
			restartADB();
			runInstrumentationTests(appPackage, classesToExecute, waitTime);
		}

		logger.info("Status: SUCCESSFUL");
		return output;
	}

	public static List<String> runInstrumentationTests(String appPackage, int waitTime) throws InterruptedException, IOException, IllegalStateException {
		logger.info("Running instrumentation tests from package "+appPackage);
		List<String> output = CommandExecutorProcess.execute("./adb shell am instrument -w "+appPackage+".test/android.support.test.runner.AndroidJUnitRunner", ConfigurationProperties.getProperty("platformtools"));

		// Checking if the execution was successful
		boolean errorOccurred = searchForString(output, "INSTRUMENTATION_FAILED");

		if(errorOccurred){
			logger.error("Failed to run instrumentation tests from package "+appPackage+", output:\n\t"+String.join("\n", output));
			throw new IllegalStateException("Could not run instrumentation tests");
		}

		if(searchForString(output, "Can't find service: package")){
			restartADB();
			runInstrumentationTests(appPackage, waitTime);
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
	}

}