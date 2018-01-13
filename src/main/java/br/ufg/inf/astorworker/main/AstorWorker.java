package br.ufg.inf.astorworker.main;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.commons.io.FileUtils;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.UnrecognizedOptionException;

import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.core.validation.validators.TestCasesProgramValidationResult;
import fr.inria.astor.core.setup.ConfigurationProperties;
import br.ufg.inf.astorworker.executors.AndroidToolsExecutorProcess;
import br.ufg.inf.astorworker.executors.JavaProjectCompiler;
import br.ufg.inf.astorworker.validators.ProgramValidator;
import br.ufg.inf.astorworker.entities.AndroidProject;
import br.ufg.inf.astorworker.faultlocalization.entities.Line;
import br.ufg.inf.astorworker.faultlocalization.AndroidFaultLocalization;
import br.ufg.inf.astorworker.handlers.DataConnectionHandler;
import br.ufg.inf.astorworker.utils.ZipUtils;
import br.ufg.inf.astorworker.enums.TestType;


/**
 * 
 * @author Kayque de Sousa Teixeira
 *
 */
public class AstorWorker  {
	protected static Logger logger = Logger.getRootLogger();
	private static Options options = new Options();

	static {
		options.addOption("hostip", true, "IP address of the machine running Astor");
		options.addOption("hostport", true, "Port used to locate Astor at the host");
		options.addOption("workerip", true, "IP address of the machine running AstorWorker");
		options.addOption("workerport", true, "AstorWorker's port");
		options.addOption("platformtools", true, "Location of the Android platform tools");
		options.addOption("buildtools", true, "Location of the Android build tools");
		options.addOption("androidjar", true, "Location of the Android.jar");
		options.addOption("androidsdk", true, "Location of the Android SDK");
		options.addOption("help", false, "Print help and usage");
	}

	public static void main(String[] args) {

		try{
			AndroidProject project = null;
			ObjectOutputStream hostObjectOutput = null;
			Socket hostByteSocket = null;
			Socket hostStringSocket = null;
			ServerSocket astorWorkerServerSocket;
			PrintWriter hostStringOutput;
			BufferedReader hostStringInput;
			boolean faultLocalizationInitialized = false;


			boolean correct = processArguments(args);
			if (!correct) {
				logger.error("Problems with command arguments");
				return;
			}

			AndroidToolsExecutorProcess.setup(ConfigurationProperties.getProperty("androidsdk"));

			new DataConnectionHandler(Integer.parseInt(ConfigurationProperties.getProperty("workerport"))).start();

			logger.info("ServerSocket created on port " + ConfigurationProperties.getProperty("workerport"));
			
			ConfigurationProperties.properties.setProperty("hostaddress", 
					ConfigurationProperties.getProperty("hostip") + ":" +
					ConfigurationProperties.getProperty("hostport"));

			logger.info("Trying to connect to " + ConfigurationProperties.getProperty("hostaddress"));
			
			
			while(true){
				try{
					hostStringSocket = new Socket(ConfigurationProperties.getProperty("hostip"), 
							Integer.parseInt(ConfigurationProperties.getProperty("hostport")));
					if (hostStringSocket != null) break; 
				}
				catch (IOException e) { 
					Thread.sleep(1000); 
				}
			}

			logger.info("Connected to " + ConfigurationProperties.getProperty("hostaddress"));
			
			logger.info("Sending connection information to " 
					+ ConfigurationProperties.getProperty("hostaddress"));

			hostStringOutput = new PrintWriter(hostStringSocket.getOutputStream(), true);
			hostStringOutput.println(ConfigurationProperties.getProperty("workerip") + ":" + 
						ConfigurationProperties.getProperty("workerport"));

			logger.info("\"" + ConfigurationProperties.getProperty("workerip") + ":" + 
					ConfigurationProperties.getProperty("workerport") + "\" sent to " +
					ConfigurationProperties.getProperty("hostaddress"));

			hostStringInput = new BufferedReader(new InputStreamReader(hostStringSocket.getInputStream()));



			while(true){

				logger.info("Waiting for instructions from " 
					+ ConfigurationProperties.getProperty("hostaddress"));

				String action = hostStringInput.readLine();
				logger.info("ACTION: "+action);

				if(action == null || action.equals("END")){
					logger.info("Repair has been finished!");
					System.exit(0);
				}

				switch(action){
					case "PROCESS_VARIANT":
						File variant;
						File rJavaLocationProject;
						File rJavaLocationVariant;
						TestCasesProgramValidationResult validationResult = null;

						logger.info("Waiting for variant to process...");
						hostByteSocket = DataConnectionHandler.getSocket();
						logger.info("Connected to " + ConfigurationProperties.getProperty("hostaddress"));

						logger.info("Receiving variant from " 
								+ ConfigurationProperties.getProperty("hostaddress") + " ...");

						variant = ZipUtils.getInstance().receiveFolder(ConfigurationProperties.getProperty("projectname"), 
								hostByteSocket.getInputStream());

						logger.info("File "+variant.getName()+" received!");

						logger.info("Processing " + variant.getName() + " ...");

						if(JavaProjectCompiler.compile(variant) == true){
							logger.info(variant.getName() + " compiles!");
							
							logger.info("Validating " + variant.getName() + "...");
							validationResult = ProgramValidator.validate(variant);
							validationResult.setCompilationSuccess(true);

							if(validationResult.isSuccessful())
								logger.info(variant.getName() + " is a fix!!!");
							else
								logger.info(variant.getName() + " is not a fix");
						}
						else {
							logger.info(variant.getName() + " does not compile!");
							validationResult = new TestCasesProgramValidationResult(null);
							validationResult.setCompilationSuccess(false);
						}

						
						hostByteSocket = DataConnectionHandler.getSocket();
						logger.info("Connected to " + ConfigurationProperties.getProperty("hostaddress"));
						logger.info("Sending results to " 
								+ ConfigurationProperties.getProperty("hostaddress"));

						hostObjectOutput = new ObjectOutputStream(
								new BufferedOutputStream(hostByteSocket.getOutputStream()));

						hostObjectOutput.writeObject(validationResult);
						hostObjectOutput.flush();
						logger.info("Results sent to " + ConfigurationProperties.getProperty("hostaddress"));
						break;

					case "SEND_PROJECT_NAME":
						String projectName = hostStringInput.readLine();

						ConfigurationProperties.properties.setProperty("projectname", projectName);

						logger.info("Project name: " + projectName);
						logger.info("Creating folder on workDir for " + projectName);


						File workingDir = new File("workDir/AstorWorker-" + projectName);
						//Deleting old dir if it exists
						FileUtils.deleteQuietly(workingDir);
						workingDir.mkdir();
						break;

					case "SEND_PROJECT":
						logger.info("Waiting for connection...");
						hostByteSocket = DataConnectionHandler.getSocket();
						logger.info("Connected to " + ConfigurationProperties.getProperty("hostaddress"));

						logger.info("Receiving project from " 
							+ ConfigurationProperties.getProperty("hostaddress") + " ...");

						File projectFile = ZipUtils.getInstance().receiveInitialProject(ConfigurationProperties.getProperty("projectname"), 
								hostByteSocket.getInputStream());

						logger.info("File " + projectFile.getName() + " received!");

						AndroidProject.getInstance().setup(projectFile);
						break;

					case "SEND_FAILING_TEST":
						action = hostStringInput.readLine();

						String[] tokens = action.split("@");

						if(TestType.valueOf(tokens[0]).equals(TestType.INSTRUMENTATION)){
							AndroidProject.getInstance().setFailingInstrumentationTestCases(tokens[1]);
							logger.info("Failing instrumentation tests received: " + tokens[1]);
						}

						if(TestType.valueOf(tokens[0]).equals(TestType.UNIT)){
							AndroidProject.getInstance().setFailingUnitTestCases(tokens[1]);
							logger.info("Failing unit tests received: " + tokens[1]);
						}

						break;


					case "FAULT_LOCALIZATION":
						logger.info("Waiting for the name of a test case to process");
						action = hostStringInput.readLine();

						if(!faultLocalizationInitialized){
							logger.info("Setting up fault localization");
							AndroidProject.getInstance().saveBuildGradle();
							AndroidFaultLocalization.setupFaultLocalization();
							logger.info("Fault localization initialized");
							faultLocalizationInitialized = true;
						}

						String[] params = action.split(":");
						logger.info("Test received: " + params[1]);

						List<Line> candidates = AndroidFaultLocalization.searchSuspicious(params[1], 
								TestType.valueOf(params[0]), new Boolean(params[2]));

						hostByteSocket = DataConnectionHandler.getSocket();
						logger.info("Connected to " + ConfigurationProperties.getProperty("hostaddress"));
						logger.info("Sending candidates to " + hostByteSocket.getRemoteSocketAddress());

						hostObjectOutput = new ObjectOutputStream(
								new BufferedOutputStream(hostByteSocket.getOutputStream()));

						hostObjectOutput.writeObject(candidates);
						hostObjectOutput.flush();
						logger.info("Candidates sent to " + hostByteSocket.getRemoteSocketAddress());
						
						break;

					case "END_FAULT_LOCALIZATION":
						logger.info("Fault localization ended");
						AndroidProject.getInstance().restoreBuildGradle();
						break;
				}

			}

		} catch(Exception e){
			logger.error("There was an error while processing the project, error:\n" + e.toString());
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static boolean processArguments(String[] args) throws Exception {
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;

		logger.info("Command line arguments: " + Arrays.toString(args).replace(",", " "));
		ConfigurationProperties.clear();

		try {
			cmd = parser.parse(options, args);
		} catch (UnrecognizedOptionException e) {
			logger.error(e.getMessage());
			help();
			return false;
		}

		if (cmd.hasOption("help")) {
			help();
			return false;
		}

		if (cmd.hasOption("hostip"))
			ConfigurationProperties.properties.setProperty("hostip", cmd.getOptionValue("hostip"));

		if (cmd.hasOption("hostport"))
			ConfigurationProperties.properties.setProperty("hostport", cmd.getOptionValue("hostport"));

		if (cmd.hasOption("workerip"))
			ConfigurationProperties.properties.setProperty("workerip", cmd.getOptionValue("workerip"));

		if (cmd.hasOption("workerport"))
			ConfigurationProperties.properties.setProperty("workerport", cmd.getOptionValue("workerport"));

		if (cmd.hasOption("platformtools"))
			ConfigurationProperties.properties.setProperty("platformtools", cmd.getOptionValue("platformtools"));

		if (cmd.hasOption("buildtools"))
			ConfigurationProperties.properties.setProperty("buildtools", cmd.getOptionValue("buildtools"));

		if(cmd.hasOption("androidjar"))
			ConfigurationProperties.properties.setProperty("androidjar", cmd.getOptionValue("androidjar"));

		if(cmd.hasOption("androidsdk"))
			ConfigurationProperties.properties.setProperty("androidsdk", cmd.getOptionValue("androidsdk"));

		return true;
	}

	private static void help() {
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp("Main", options);

		System.exit(0);
	}
}